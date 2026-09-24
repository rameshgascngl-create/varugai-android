package com.gasczoology.varugai.ui.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gasczoology.varugai.data.backup.RegisterBundle
import com.gasczoology.varugai.data.export.AttendancePdfWriter
import com.gasczoology.varugai.data.export.NativeExportWriters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ExportScreen(
    state: ExportUiState,
    onPreviewRestore: (String) -> Unit,
    onCancelRestore: () -> Unit,
    onCommitRestore: () -> Unit,
    onCreateBackup: suspend () -> String,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bundle = state.bundle

    fun writeUri(uri: Uri?, label: String, block: suspend (java.io.OutputStream) -> Unit) {
        if (uri == null) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { out -> block(out) }
                        ?: error("Could not open the selected destination.")
                }
            }.onSuccess { onNotify("$label exported") }
                .onFailure { onNotify(it.message ?: "$label export failed") }
        }
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> writeUri(uri, "Attendance workbook") { out ->
        NativeExportWriters.attendanceXlsx(requireNotNull(bundle), out)
    } }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> writeUri(uri, "Summary CSV") { out ->
        out.write(NativeExportWriters.summaryCsv(requireNotNull(bundle)).toByteArray(Charsets.UTF_8))
    } }

    val auditLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> writeUri(uri, "Audit log") { out ->
        out.write(NativeExportWriters.auditCsv(requireNotNull(bundle).audits).toByteArray(Charsets.UTF_8))
    } }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = onCreateBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open the selected destination.")
                }
            }.onSuccess { onNotify("JSON backup exported") }
                .onFailure { onNotify(it.message ?: "Backup export failed") }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> writeUri(uri, "PDF statement") { out ->
        AttendancePdfWriter.write(requireNotNull(bundle), out)
    } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input -> readUtf8Limited(input, 20 * 1024 * 1024) }
                        ?: error("Could not open the selected backup.")
                }
            }.onSuccess(onPreviewRestore)
                .onFailure { onNotify(it.message ?: "Backup could not be read") }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Export & backup", style = MaterialTheme.typography.headlineSmall)
            Text("Native Android file handling through the system document picker. No broad storage permission is used.")
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Storage status", style = MaterialTheme.typography.titleMedium)
                    Text("Authoritative data: app-private Room database.")
                    Text("Automatic Android/cloud backup: disabled.")
                    Text("Network sync: none. Files leave the app only when you explicitly export them.")
                    if (bundle != null) {
                        Text("${bundle.students.size} students · ${bundle.days.size} calendar dates · ${bundle.marks.size} attendance marks")
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exports", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = bundle != null,
                            onClick = { xlsxLauncher.launch(exportName(bundle, "attendance", "xlsx")) },
                        ) { Text("XLSX workbook") }
                        OutlinedButton(
                            enabled = bundle != null,
                            onClick = { csvLauncher.launch(exportName(bundle, "summary", "csv")) },
                        ) { Text("Summary CSV") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = bundle != null,
                            onClick = { pdfLauncher.launch(exportName(bundle, "statement", "pdf")) },
                        ) { Text("PDF statement") }
                        OutlinedButton(
                            enabled = bundle != null,
                            onClick = { auditLauncher.launch(exportName(bundle, "audit", "csv")) },
                        ) { Text("Audit CSV") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup / restore", style = MaterialTheme.typography.titleMedium)
                    Text("VARUGAI 16 exports schema-3 JSON with SHA-256 integrity checking. VARUGAI 15.x schema-2 backups remain importable after legacy FNV-1a verification.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = bundle != null,
                            onClick = { jsonLauncher.launch(exportName(bundle, "backup", "json")) },
                        ) { Text("Export JSON backup") }
                        OutlinedButton(
                            enabled = bundle != null,
                            onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        ) { Text("Restore JSON") }
                    }
                    Text("Restore replaces the current register only after validation and confirmation. A failed restore is rolled back transactionally.")
                }
            }
        }

        if (bundle != null) {
            item { Text("Recent audit events", style = MaterialTheme.typography.titleMedium) }
            items(bundle.audits.sortedByDescending { it.timestamp }.take(50), key = { event -> "${event.id}-${event.timestamp}" }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(event.kind)
                        Text(listOfNotNull(event.date, event.sid).joinToString(" · "))
                        Text(event.message)
                    }
                }
            }
        }
    }

    state.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Confirm restore") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pending.sourceLabel)
                    Text("${pending.bundle.students.size} students · ${pending.bundle.days.size} calendar dates · ${pending.bundle.marks.size} attendance marks")
                    pending.warning?.let { Text(it) }
                    Text("This will replace the currently selected register's roster, calendar, attendance and audit log.")
                }
            },
            confirmButton = { TextButton(onClick = onCommitRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Cancel") } },
        )
    }
}

private fun exportName(bundle: RegisterBundle?, kind: String, extension: String): String {
    val r = bundle?.register
    val stem = listOf(r?.courseCode.orEmpty(), r?.className.orEmpty(), kind)
        .filter { it.isNotBlank() }
        .joinToString("_")
        .ifBlank { "VARUGAI_$kind" }
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .take(80)
    return "$stem.$extension"
}

private fun readUtf8Limited(input: java.io.InputStream, limit: Int): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        require(total <= limit) { "Backup exceeds the 20 MB safety limit." }
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
}

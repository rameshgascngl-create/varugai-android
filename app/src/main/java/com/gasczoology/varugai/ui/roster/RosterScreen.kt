package com.gasczoology.varugai.ui.roster

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.export.RosterXlsxExporter
import com.gasczoology.varugai.data.importer.RosterFileParser
import com.gasczoology.varugai.domain.roster.RosterImportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RosterScreen(
    state: RosterUiState,
    onAdd: (String, String, String, String, String) -> Unit,
    onUpdate: (StudentEntity) -> Unit,
    onDelete: (StudentEntity) -> Unit,
    onMove: (StudentEntity, Int) -> Unit,
    onPreviewImport: (List<RosterImportRow>) -> Unit,
    onCancelImport: () -> Unit,
    onCommitImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf<StudentEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment ?: "roster"
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val rows = RosterFileParser.parseByFileName(name, input)
                        val guess = RosterFileParser.guessColumns(rows, hasHeader = true)
                        RosterFileParser.mapColumns(rows, true, guess.roll, guess.registerNumber, guess.name)
                    } ?: error("Could not open selected roster file.")
                }
            }.onSuccess(onPreviewImport).onFailure { localError = it.message ?: "Roster import failed." }
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                        RosterXlsxExporter.write(state.students, out)
                    } ?: error("Could not create roster workbook.")
                }
            }.onFailure { localError = it.message ?: "Roster export failed." }
        }
    }

    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Student roster")
            Text("${state.students.size} student(s) · attendance follows stable student ID, not roll number.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Button(onClick = { adding = true }, enabled = state.currentRegister != null) { Text("Add") }
                OutlinedButton(onClick = { pasteOpen = true }, enabled = state.currentRegister != null) { Text("Paste") }
                OutlinedButton(onClick = {
                    openDocument.launch(arrayOf("text/*", "application/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream"))
                }, enabled = state.currentRegister != null) { Text("Import") }
                OutlinedButton(onClick = { createDocument.launch("VARUGAI_roster.xlsx") }, enabled = state.students.isNotEmpty()) { Text("Export XLSX") }
            }
        }

        itemsIndexed(state.students, key = { _, s -> s.sid }) { index, student ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${student.roll}  ${student.name}")
                    if (student.registerNumber.isNotBlank()) Text("Reg. No: ${student.registerNumber}")
                    if (student.admissionDate.isNotBlank() || student.attendanceEndDate.isNotBlank()) Text("Counts ${student.admissionDate.ifBlank { "from course start" }} → ${student.attendanceEndDate.ifBlank { "course end" }}")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onMove(student, -1) }, enabled = index > 0) { Text("↑") }
                        TextButton(onClick = { onMove(student, 1) }, enabled = index < state.students.lastIndex) { Text("↓") }
                        TextButton(onClick = { showEditor = student }) { Text("Edit") }
                        TextButton(onClick = { onDelete(student) }) { Text("Remove") }
                    }
                }
            }
        }
    }

    if (adding) StudentDialog("Add student", null, { adding = false }) { roll, reg, name, from, until ->
        adding = false; onAdd(roll, reg, name, from, until)
    }
    showEditor?.let { original ->
        StudentDialog("Edit student", original, { showEditor = null }) { roll, reg, name, from, until ->
            showEditor = null
            onUpdate(original.copy(roll = roll, registerNumber = reg, name = name, admissionDate = from, attendanceEndDate = until))
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text("Paste roster") },
            text = { OutlinedTextField(pasteText, { pasteText = it }, label = { Text("One student per line: roll,name or just name") }, minLines = 8) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { RosterFileParser.parsePastedRoster(pasteText, false) }
                        .onSuccess { pasteOpen = false; onPreviewImport(it) }
                        .onFailure { localError = it.message ?: "Could not parse pasted roster." }
                }) { Text("Preview") }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("Cancel") } },
        )
    }

    state.pendingImport?.let { plan ->
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(if (plan.isBlocked) "Roster import blocked" else "Confirm roster replacement") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("New roster: ${plan.students.size} student(s).")
                if (plan.rollChanges.isNotEmpty()) Text("${plan.rollChanges.size} roll-number correction(s) preserve attendance by stable ID.")
                if (plan.omittedWithoutAttendance.isNotEmpty()) Text("${plan.omittedWithoutAttendance.size} unmarked omitted student(s) will be removed.")
                if (plan.omittedWithAttendance.isNotEmpty()) Text("${plan.omittedWithAttendance.size} omitted student(s) already have attendance. Import is blocked to prevent data loss.")
            } },
            confirmButton = { if (!plan.isBlocked) TextButton(onClick = onCommitImport) { Text("Commit roster") } },
            dismissButton = { TextButton(onClick = onCancelImport) { Text(if (plan.isBlocked) "Close" else "Cancel") } },
        )
    }
    localError?.let { message -> AlertDialog(onDismissRequest = { localError = null }, title = { Text("Roster") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { localError = null }) { Text("OK") } }) }
}

@Composable
private fun StudentDialog(title: String, initial: StudentEntity?, onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var roll by remember(initial?.sid) { mutableStateOf(initial?.roll.orEmpty()) }
    var reg by remember(initial?.sid) { mutableStateOf(initial?.registerNumber.orEmpty()) }
    var name by remember(initial?.sid) { mutableStateOf(initial?.name.orEmpty()) }
    var from by remember(initial?.sid) { mutableStateOf(initial?.admissionDate.orEmpty()) }
    var until by remember(initial?.sid) { mutableStateOf(initial?.attendanceEndDate.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(roll, { roll = it }, label = { Text("Roll number") }, singleLine = true)
            OutlinedTextField(reg, { reg = it }, label = { Text("Register number (optional)") }, singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("Student name") }, singleLine = true)
            OutlinedTextField(from, { from = it }, label = { Text("Counts from YYYY-MM-DD (optional)") }, singleLine = true)
            OutlinedTextField(until, { until = it }, label = { Text("Counts until YYYY-MM-DD (optional)") }, singleLine = true)
        } },
        confirmButton = { TextButton(enabled = roll.isNotBlank() && name.isNotBlank(), onClick = { onSave(roll, reg, name, from, until) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

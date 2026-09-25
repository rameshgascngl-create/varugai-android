package com.gasczoology.varugai.ui.recovery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gasczoology.varugai.data.backup.BackupCodec
import com.gasczoology.varugai.data.backup.BackupImport
import com.gasczoology.varugai.data.backup.PortableBackupCrypto
import com.gasczoology.varugai.data.backup.RecoveryKeyFormat
import com.gasczoology.varugai.security.RecoveryKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun DatabaseRecoveryScreen(
    reason: String,
    recoveryKeyManager: RecoveryKeyManager,
    onRestoreConfirmed: suspend (BackupImport) -> Result<Unit>,
    onResetConfirmed: suspend () -> Result<Unit>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRaw by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<BackupImport?>(null) }
    var showRecoveryKeyPrompt by rememberSaveable { mutableStateOf(false) }
    var recoveryKeyInput by rememberSaveable { mutableStateOf("") }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    fun decodePlain(raw: String) {
        runCatching { BackupCodec.decode(raw) }
            .onSuccess {
                pendingImport = it
                message = null
            }
            .onFailure { message = it.message ?: "Backup could not be validated." }
    }

    fun handleRawBackup(raw: String) {
        if (!PortableBackupCrypto.isEncrypted(raw)) {
            decodePlain(raw)
            return
        }

        val storedKey = runCatching { recoveryKeyManager.getStoredRecoveryKey() }.getOrNull()
        if (storedKey != null) {
            val automatic = runCatching {
                BackupCodec.decode(PortableBackupCrypto.decrypt(raw, storedKey))
            }
            if (automatic.isSuccess) {
                pendingImport = automatic.getOrThrow()
                message = null
                return
            }
        }

        pendingRaw = raw
        recoveryKeyInput = ""
        showRecoveryKeyPrompt = true
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        readUtf8Limited(it, 30 * 1024 * 1024)
                    } ?: error("Could not open the selected backup.")
                }
            }.onSuccess(::handleRawBackup)
                .onFailure { message = it.message ?: "Backup could not be read." }
            busy = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("VARUGAI data recovery", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Encrypted attendance data cannot be opened on this device.", style = MaterialTheme.typography.titleMedium)
                Text(reason)
                Text("VARUGAI has not deleted or recreated the local database.")
                Text("Restore from a validated JSON backup is the primary recovery route.")
            }
        }

        Button(
            enabled = !busy,
            onClick = { restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Reading backup…" else "Restore from a JSON backup")
        }

        TextButton(
            enabled = !busy,
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset local database")
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showRecoveryKeyPrompt) {
        AlertDialog(
            onDismissRequest = {
                showRecoveryKeyPrompt = false
                pendingRaw = null
            },
            title = { Text("Enter recovery key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This backup is encrypted. Enter the recovery key that was recorded when the backup was created.")
                    OutlinedTextField(
                        value = recoveryKeyInput,
                        onValueChange = { recoveryKeyInput = it },
                        singleLine = true,
                        label = { Text("Recovery key") },
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = recoveryKeyInput.isNotBlank(),
                    onClick = {
                        val raw = pendingRaw ?: return@TextButton
                        runCatching {
                            val normalized = RecoveryKeyFormat.requireValid(recoveryKeyInput)
                            val plain = PortableBackupCrypto.decrypt(raw, normalized)
                            val imported = BackupCodec.decode(plain)
                            Triple(imported, normalized, recoveryKeyManager.hasRecoveryKey())
                        }.onSuccess { (imported, normalized, hadKey) ->
                            if (!hadKey) recoveryKeyManager.adoptIfAbsent(normalized)
                            pendingImport = imported
                            pendingRaw = null
                            showRecoveryKeyPrompt = false
                            message = null
                        }.onFailure {
                            message = it.message ?: "Recovery key is incorrect or the backup is damaged."
                        }
                    },
                ) { Text("Unlock backup") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRecoveryKeyPrompt = false
                    pendingRaw = null
                }) { Text("Cancel") }
            },
        )
    }

    pendingImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore validated backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(imported.sourceLabel)
                    Text("${imported.bundle.students.size} students · ${imported.bundle.days.size} calendar dates · ${imported.bundle.marks.size} attendance marks")
                    Text("The current encrypted database is unreadable. Confirming will delete that unreadable local database and replace it with this already-validated backup.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        val result = onRestoreConfirmed(imported)
                        if (result.isFailure) {
                            message = result.exceptionOrNull()?.message ?: "Recovery restore failed."
                            busy = false
                        }
                    }
                }) { Text("Restore backup") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Permanently reset local attendance data?") },
            text = {
                Text(
                    "This permanently deletes the unreadable on-device register, student roster, semester calendar, attendance marks and audit history. Exported backup files outside VARUGAI are not deleted. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    scope.launch {
                        busy = true
                        val result = onResetConfirmed()
                        if (result.isFailure) {
                            message = result.exceptionOrNull()?.message ?: "Reset failed."
                            busy = false
                        }
                    }
                }) { Text("Delete local data and reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

private fun readUtf8Limited(input: java.io.InputStream, limit: Int): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        require(total <= limit) { "Backup exceeds the safe import size limit." }
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
}

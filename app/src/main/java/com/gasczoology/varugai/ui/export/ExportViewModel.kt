package com.gasczoology.varugai.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gasczoology.varugai.data.backup.BackupCodec
import com.gasczoology.varugai.data.backup.BackupImport
import com.gasczoology.varugai.data.backup.PortableBackupCrypto
import com.gasczoology.varugai.data.backup.RecoveryKeyFormat
import com.gasczoology.varugai.data.backup.RegisterBundle
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.data.repository.VarugaiRepository
import com.gasczoology.varugai.security.RecoveryKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportUiState(
    val bundle: RegisterBundle? = null,
    val pendingRestore: BackupImport? = null,
    val message: String? = null,
    val recoveryKeyConfigured: Boolean = false,
    val shownRecoveryKey: String? = null,
    val recoveryKeyRequestedForRestore: Boolean = false,
)

private data class BackupUiState(
    val pendingRestore: BackupImport?,
    val message: String?,
    val recoveryKeyConfigured: Boolean,
    val shownRecoveryKey: String?,
    val recoveryKeyRequestedForRestore: Boolean,
)

class ExportViewModel(
    private val repository: VarugaiRepository,
    preferences: VarugaiPreferences,
    private val recoveryKeyManager: RecoveryKeyManager,
) : ViewModel() {
    private val pendingRestore = MutableStateFlow<BackupImport?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val recoveryKeyConfigured = MutableStateFlow(recoveryKeyManager.hasRecoveryKey())
    private val shownRecoveryKey = MutableStateFlow<String?>(null)
    private val recoveryKeyRequestedForRestore = MutableStateFlow(false)
    private val pendingEncryptedBackup = MutableStateFlow<String?>(null)

    private val currentRegister: Flow<RegisterEntity?> =
        combine(repository.registers, preferences.currentRegisterId) { registers, id ->
            registers.firstOrNull { it.id == id } ?: registers.firstOrNull()
        }

    private val bundle: Flow<RegisterBundle?> = currentRegister.flatMapLatest { register ->
        if (register == null) {
            flowOf(null)
        } else {
            combine(
                repository.observeStudents(register.id),
                repository.observeTeachingDays(register.id),
                repository.observeAttendance(register.id),
                repository.observeAudit(register.id),
            ) { students, days, marks, audits ->
                RegisterBundle(register, students, days, marks, audits)
            }
        }
    }

    private val backupUi: Flow<BackupUiState> =
        combine(
            pendingRestore,
            message,
            recoveryKeyConfigured,
            shownRecoveryKey,
            recoveryKeyRequestedForRestore,
        ) { pending, msg, configured, shown, requested ->
            BackupUiState(pending, msg, configured, shown, requested)
        }

    val uiState: StateFlow<ExportUiState> = combine(bundle, backupUi) { data, backup ->
        ExportUiState(
            bundle = data,
            pendingRestore = backup.pendingRestore,
            message = backup.message,
            recoveryKeyConfigured = backup.recoveryKeyConfigured,
            shownRecoveryKey = backup.shownRecoveryKey,
            recoveryKeyRequestedForRestore = backup.recoveryKeyRequestedForRestore,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    fun showRecoveryKey() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { recoveryKeyManager.getOrCreateRecoveryKey() }
            .onSuccess {
                recoveryKeyConfigured.value = true
                shownRecoveryKey.value = it
            }
            .onFailure { message.value = it.message ?: "Recovery key could not be prepared." }
    }

    fun hideRecoveryKey() {
        shownRecoveryKey.value = null
    }

    suspend fun encryptedBackupText(): String = withContext(Dispatchers.Default) {
        val current = uiState.value.bundle ?: error("No active register.")
        val key = recoveryKeyManager.getStoredRecoveryKey()
            ?: error("Set up and record the recovery key before exporting a backup.")
        PortableBackupCrypto.encrypt(BackupCodec.createNative(current), key)
    }

    fun previewRestore(raw: String) = viewModelScope.launch(Dispatchers.Default) {
        if (!PortableBackupCrypto.isEncrypted(raw)) {
            runCatching { BackupCodec.decode(raw) }
                .onSuccess { pendingRestore.value = it }
                .onFailure { message.value = it.message ?: "Backup could not be validated." }
            return@launch
        }

        pendingEncryptedBackup.value = raw
        val storedKey = runCatching { recoveryKeyManager.getStoredRecoveryKey() }.getOrNull()
        if (storedKey != null) {
            val automatic = runCatching {
                BackupCodec.decode(PortableBackupCrypto.decrypt(raw, storedKey))
            }
            if (automatic.isSuccess) {
                pendingEncryptedBackup.value = null
                pendingRestore.value = automatic.getOrThrow()
                return@launch
            }
        }
        recoveryKeyRequestedForRestore.value = true
    }

    fun submitRecoveryKeyForRestore(input: String) = viewModelScope.launch(Dispatchers.Default) {
        val raw = pendingEncryptedBackup.value
        if (raw == null) {
            recoveryKeyRequestedForRestore.value = false
            return@launch
        }
        runCatching {
            val normalized = RecoveryKeyFormat.requireValid(input)
            val plain = PortableBackupCrypto.decrypt(raw, normalized)
            val imported = BackupCodec.decode(plain)
            Triple(imported, normalized, recoveryKeyManager.hasRecoveryKey())
        }.onSuccess { (imported, normalized, hadKey) ->
            if (!hadKey) {
                recoveryKeyManager.adoptIfAbsent(normalized)
                recoveryKeyConfigured.value = true
            }
            pendingEncryptedBackup.value = null
            recoveryKeyRequestedForRestore.value = false
            pendingRestore.value = imported
        }.onFailure {
            message.value = it.message ?: "Encrypted backup could not be opened."
        }
    }

    fun cancelRecoveryKeyPrompt() {
        pendingEncryptedBackup.value = null
        recoveryKeyRequestedForRestore.value = false
    }

    fun cancelRestore() {
        pendingRestore.value = null
    }

    fun commitRestore() = viewModelScope.launch {
        val currentId = uiState.value.bundle?.register?.id ?: return@launch
        val pending = pendingRestore.value ?: return@launch
        runCatching {
            repository.restoreRegisterBundle(currentId, pending.bundle, pending.sourceLabel)
        }.onSuccess {
            pendingRestore.value = null
            message.value = "Backup restored. Recomputed native summaries now use the restored Room data."
        }.onFailure {
            message.value = it.message ?: "Restore failed. The previous register was kept unchanged."
        }
    }

    fun notify(text: String) {
        message.value = text
    }

    fun clearMessage() {
        message.value = null
    }

    class Factory(
        private val repository: VarugaiRepository,
        private val preferences: VarugaiPreferences,
        private val recoveryKeyManager: RecoveryKeyManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExportViewModel(repository, preferences, recoveryKeyManager) as T
    }
}

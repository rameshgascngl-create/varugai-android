package com.gasczoology.varugai.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gasczoology.varugai.data.backup.BackupCodec
import com.gasczoology.varugai.data.backup.BackupImport
import com.gasczoology.varugai.data.backup.RegisterBundle
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.data.repository.VarugaiRepository
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
)

class ExportViewModel(
    private val repository: VarugaiRepository,
    preferences: VarugaiPreferences,
) : ViewModel() {
    private val pendingRestore = MutableStateFlow<BackupImport?>(null)
    private val message = MutableStateFlow<String?>(null)

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

    val uiState: StateFlow<ExportUiState> = combine(bundle, pendingRestore, message) { data, pending, msg ->
        ExportUiState(data, pending, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    suspend fun nativeBackupText(): String = withContext(Dispatchers.Default) {
        val current = uiState.value.bundle ?: error("No active register.")
        BackupCodec.createNative(current)
    }

    fun previewRestore(raw: String) = viewModelScope.launch(Dispatchers.Default) {
        runCatching { BackupCodec.decode(raw) }
            .onSuccess { pendingRestore.value = it }
            .onFailure { message.value = it.message ?: "Backup could not be validated." }
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExportViewModel(repository, preferences) as T
    }
}

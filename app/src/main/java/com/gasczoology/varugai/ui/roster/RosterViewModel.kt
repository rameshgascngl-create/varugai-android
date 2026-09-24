package com.gasczoology.varugai.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.data.repository.VarugaiRepository
import com.gasczoology.varugai.domain.roster.RosterImportPlan
import com.gasczoology.varugai.domain.roster.RosterImportRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RosterUiState(
    val currentRegister: RegisterEntity? = null,
    val students: List<StudentEntity> = emptyList(),
    val pendingImport: RosterImportPlan? = null,
    val message: String? = null,
)

class RosterViewModel(
    private val repository: VarugaiRepository,
    private val preferences: VarugaiPreferences,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val pendingImport = MutableStateFlow<RosterImportPlan?>(null)

    private val currentRegister = combine(repository.registers, preferences.currentRegisterId) { registers, id ->
        registers.firstOrNull { it.id == id } ?: registers.firstOrNull()
    }

    private val registerAndStudents = currentRegister.flatMapLatest { register ->
        if (register == null) flowOf<Pair<RegisterEntity?, List<StudentEntity>>>(null to emptyList())
        else repository.observeStudents(register.id).map { register to it }
    }

    val uiState: StateFlow<RosterUiState> = combine(
        registerAndStudents,
        pendingImport,
        message,
    ) { pair, pending, msg ->
        RosterUiState(pair.first, pair.second, pending, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RosterUiState())

    init {
        viewModelScope.launch {
            val initial = repository.ensureInitialRegister()
            if (preferences.currentRegisterId.first() == null) {
                preferences.setCurrentRegisterId(initial.id)
            }
        }
    }

    fun clearMessage() { message.value = null }

    fun addStudent(roll: String, regNo: String, name: String, from: String, until: String) = viewModelScope.launch {
        val registerId = uiState.value.currentRegister?.id ?: return@launch
        runCatching { repository.addStudent(registerId, roll, regNo, name, from, until) }
            .onSuccess { message.value = "Student added" }
            .onFailure { message.value = it.message ?: "Could not add student" }
    }

    fun updateStudent(student: StudentEntity) = viewModelScope.launch {
        runCatching { repository.updateStudent(student) }
            .onSuccess { message.value = "Student updated" }
            .onFailure { message.value = it.message ?: "Could not update student" }
    }

    fun deleteStudent(student: StudentEntity) = viewModelScope.launch {
        runCatching { repository.deleteStudent(student.registerId, student.sid) }
            .onSuccess { result ->
                message.value = if (result.deleted) "Student removed" else if (result.attendanceRows > 0) {
                    "Cannot remove ${student.roll} ${student.name}: ${result.attendanceRows} attendance mark(s) already exist. Set Counts until instead, or clear those marks first."
                } else "Student not found"
            }
            .onFailure { message.value = it.message ?: "Could not remove student" }
    }

    fun moveStudent(student: StudentEntity, delta: Int) = viewModelScope.launch {
        runCatching { repository.moveStudent(student.registerId, student.sid, delta) }
            .onFailure { message.value = it.message ?: "Could not reorder roster" }
    }

    fun previewImport(rows: List<RosterImportRow>) = viewModelScope.launch {
        val registerId = uiState.value.currentRegister?.id ?: return@launch
        runCatching { repository.previewRosterReplacement(registerId, rows) }
            .onSuccess { pendingImport.value = it }
            .onFailure { message.value = it.message ?: "Could not prepare roster import" }
    }

    fun cancelImport() { pendingImport.value = null }

    fun commitImport() = viewModelScope.launch {
        val plan = pendingImport.value ?: return@launch
        runCatching { repository.applyRosterReplacement(plan) }
            .onSuccess {
                pendingImport.value = null
                message.value = "Roster updated: ${plan.students.size} student(s)"
            }
            .onFailure { message.value = it.message ?: "Could not import roster" }
    }

    class Factory(
        private val repository: VarugaiRepository,
        private val preferences: VarugaiPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RosterViewModel(repository, preferences) as T
    }
}

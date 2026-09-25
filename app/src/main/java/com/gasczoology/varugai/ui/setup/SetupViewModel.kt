package com.gasczoology.varugai.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.data.repository.VarugaiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetupUiState(
    val registers: List<RegisterEntity> = emptyList(),
    val current: RegisterEntity? = null,
    val selectedWeekdays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val teachingDays: List<TeachingDayEntity> = emptyList(),
    val lastFullBackupAt: Long? = null,
    val message: String? = null,
)

class SetupViewModel(
    private val repository: VarugaiRepository,
    private val preferences: VarugaiPreferences,
) : ViewModel() {
    private val selectedId = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val teachingDays = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeTeachingDays(id)
    }
    private val lastFullBackupAt = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(null) else preferences.lastFullBackupAt(id)
    }

    val uiState: StateFlow<SetupUiState> = combine(
        repository.registers,
        selectedId,
        teachingDays,
        lastFullBackupAt,
        message,
    ) { registers, chosenId, days, backupAt, msg ->
        val current = registers.firstOrNull { it.id == chosenId } ?: registers.firstOrNull()
        SetupUiState(
            registers = registers,
            current = current,
            selectedWeekdays = current?.calendarWeekdaysCsv
                ?.split(',')
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: setOf(1, 2, 3, 4, 5),
            teachingDays = days,
            lastFullBackupAt = backupAt,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    init {
        viewModelScope.launch {
            val initial = repository.ensureInitialRegister()
            val stored = preferences.currentRegisterId.first()
            val all = repository.registers.first()
            val resolved = all.firstOrNull { it.id == stored } ?: initial
            selectedId.value = resolved.id
            preferences.setCurrentRegisterId(resolved.id)
        }
    }

    fun selectRegister(id: String) = viewModelScope.launch {
        selectedId.value = id
        preferences.setCurrentRegisterId(id)
    }

    fun save(register: RegisterEntity, weekdays: Set<Int>) = viewModelScope.launch {
        try {
            repository.saveRegister(register.copy(calendarWeekdaysCsv = weekdays.sorted().joinToString(",")))
            message.value = "Settings saved on device"
        } catch (t: Throwable) {
            message.value = t.message ?: "Could not save settings"
        }
    }

    fun newRegister(seed: RegisterEntity?) = viewModelScope.launch {
        val created = repository.createRegister(seed)
        selectedId.value = created.id
        preferences.setCurrentRegisterId(created.id)
        message.value = "New register created"
    }

    fun duplicateCurrent() = viewModelScope.launch {
        val id = selectedId.value ?: return@launch
        try {
            val copy = repository.duplicateRegister(id)
            selectedId.value = copy.id
            preferences.setCurrentRegisterId(copy.id)
            message.value = "Register duplicated without attendance marks"
        } catch (t: Throwable) {
            message.value = t.message ?: "Could not duplicate register"
        }
    }

    fun deleteCurrent() = viewModelScope.launch {
        val id = selectedId.value ?: return@launch
        if (!repository.deleteRegister(id)) {
            message.value = "At least one register must remain"
            return@launch
        }
        val replacement = repository.registers.first().first()
        selectedId.value = replacement.id
        preferences.setCurrentRegisterId(replacement.id)
        message.value = "Register deleted"
    }

    fun generateCalendar(register: RegisterEntity, weekdays: Set<Int>) = viewModelScope.launch {
        try {
            repository.generateCalendar(register, weekdays)
            message.value = "Calendar generated"
        } catch (t: Throwable) {
            message.value = t.message ?: "Could not generate calendar"
        }
    }


    fun updateTeachingDay(day: TeachingDayEntity) = viewModelScope.launch {
        try {
            repository.updateTeachingDay(day)
            message.value = "Working day updated"
        } catch (t: Throwable) {
            message.value = t.message ?: "Could not update working day"
        }
    }

    fun markHoliday(date: String, name: String) = viewModelScope.launch {
        val id = selectedId.value ?: return@launch
        try {
            repository.markHoliday(id, date, name)
            message.value = "Holiday saved"
        } catch (t: Throwable) {
            message.value = t.message ?: "Could not mark holiday"
        }
    }

    fun addWorkingDay(date: String, hours: Int) = viewModelScope.launch {
        val id = selectedId.value ?: return@launch
        try {
            repository.addWorkingDay(id, date, hours)
            message.value = "Special working day added"
        } catch (t: Throwable) {
            message.value = t.message ?: "Could not add working day"
        }
    }

    fun setDayOfWeekWorking(dayOfWeek: Int, working: Boolean) = viewModelScope.launch {
        val id = selectedId.value ?: return@launch
        try {
            repository.setDayOfWeekWorking(id, dayOfWeek, working)
            message.value = "Calendar updated"
        } catch (t: Throwable) {
            message.value = t.message ?: "Could not update calendar"
        }
    }

    fun copyCalendar(sourceId: String, targetId: String) = viewModelScope.launch {
        try {
            repository.copyCalendar(sourceId, targetId)
            message.value = "Calendar copied"
        } catch (t: Throwable) {
            message.value = t.message ?: "Could not copy calendar"
        }
    }

    fun clearMessage() {
        message.value = null
    }

    class Factory(
        private val repository: VarugaiRepository,
        private val preferences: VarugaiPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SetupViewModel(repository, preferences) as T
        }
    }
}

package com.gasczoology.varugai.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gasczoology.varugai.data.db.AttendanceMarkEntity
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import com.gasczoology.varugai.data.preferences.VarugaiPreferences
import com.gasczoology.varugai.data.repository.VarugaiRepository
import com.gasczoology.varugai.domain.attendance.AttendanceCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class GridStudentFilter { ALL, ACTIVE }

data class GridUiState(
    val register: RegisterEntity? = null,
    val students: List<StudentEntity> = emptyList(),
    val visibleStudents: List<StudentEntity> = emptyList(),
    val days: List<TeachingDayEntity> = emptyList(),
    val selectedDate: String? = null,
    val selectedDay: TeachingDayEntity? = null,
    val windowDates: List<TeachingDayEntity> = emptyList(),
    val windowSize: Int = 5,
    val filter: GridStudentFilter = GridStudentFilter.ALL,
    val query: String = "",
    val marksByCell: Map<Pair<String, Int>, String> = emptyMap(),
    val canUndo: Boolean = false,
    val message: String? = null,
)

private data class GridRaw(
    val register: RegisterEntity? = null,
    val students: List<StudentEntity> = emptyList(),
    val days: List<TeachingDayEntity> = emptyList(),
    val marks: List<AttendanceMarkEntity> = emptyList(),
)

private data class GridViewPrefs(
    val selectedDate: String?,
    val filter: GridStudentFilter,
    val query: String,
    val windowSize: Int,
)

private data class UndoSnapshot(
    val day: TeachingDayEntity,
    val marks: List<AttendanceMarkEntity>,
)

class GridViewModel(
    private val repository: VarugaiRepository,
    private val preferences: VarugaiPreferences,
) : ViewModel() {
    private val selectedDate = MutableStateFlow<String?>(null)
    private val filter = MutableStateFlow(GridStudentFilter.ALL)
    private val query = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private val undoSnapshot = MutableStateFlow<UndoSnapshot?>(null)

    private val currentRegister: Flow<RegisterEntity?> =
        combine(repository.registers, preferences.currentRegisterId) { registers, id ->
            registers.firstOrNull { it.id == id } ?: registers.firstOrNull()
        }

    private val raw: Flow<GridRaw> = currentRegister.flatMapLatest { register ->
        if (register == null) {
            flowOf(GridRaw())
        } else {
            combine(
                repository.observeStudents(register.id),
                repository.observeTeachingDays(register.id),
                repository.observeAttendance(register.id),
            ) { students, days, marks ->
                GridRaw(register, students, days.sortedBy { it.date }, marks)
            }
        }
    }

    private val viewPrefs = combine(
        selectedDate,
        filter,
        query,
        preferences.gridWindowSize,
    ) { date, selectedFilter, text, window ->
        GridViewPrefs(date, selectedFilter, text, window.coerceIn(1, 15))
    }

    val uiState: StateFlow<GridUiState> = combine(raw, viewPrefs, message, undoSnapshot) { data, prefs, msg, undo ->
        val resolvedDate = resolveDate(data.days, prefs.selectedDate)
        val selected = data.days.firstOrNull { it.date == resolvedDate }
        val normalizedQuery = prefs.query.trim().lowercase()
        val visible = data.students.filter { student ->
            val textMatch = normalizedQuery.isBlank() ||
                student.name.lowercase().contains(normalizedQuery) ||
                student.roll.lowercase().contains(normalizedQuery) ||
                student.registerNumber.lowercase().contains(normalizedQuery)
            val activeMatch = prefs.filter == GridStudentFilter.ALL ||
                (resolvedDate != null && AttendanceCalculator.isActiveOn(student, resolvedDate))
            textMatch && activeMatch
        }
        val selectedMarks = if (resolvedDate == null) emptyMap() else {
            data.marks.asSequence()
                .filter { it.date == resolvedDate }
                .associate { (it.sid to it.hourIndex) to it.status }
        }
        GridUiState(
            register = data.register,
            students = data.students,
            visibleStudents = visible,
            days = data.days,
            selectedDate = resolvedDate,
            selectedDay = selected,
            windowDates = dateWindow(data.days, resolvedDate, prefs.windowSize),
            windowSize = prefs.windowSize,
            filter = prefs.filter,
            query = prefs.query,
            marksByCell = selectedMarks,
            canUndo = undo != null,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridUiState())

    fun clearMessage() { message.value = null }
    fun selectDate(date: String) { selectedDate.value = date }
    fun setFilter(value: GridStudentFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }

    fun setWindowSize(value: Int) = viewModelScope.launch {
        preferences.setGridWindowSize(value.coerceIn(1, 15))
    }

    fun previousDate() { moveDate(-1) }
    fun nextDate() { moveDate(1) }

    fun jumpToday() {
        val days = uiState.value.days
        if (days.isEmpty()) return
        val today = LocalDate.now().toString()
        selectedDate.value = days.firstOrNull { it.date == today }?.date
            ?: days.lastOrNull { it.date <= today }?.date
            ?: days.first().date
    }

    fun cycleMark(student: StudentEntity, hourIndex: Int) {
        val state = uiState.value
        val date = state.selectedDate ?: return
        val current = state.marksByCell[student.sid to hourIndex]
        val next = when (current) {
            null -> "P"
            "P" -> "A"
            "A" -> "O"
            else -> null
        }
        undoable(date) {
            repository.setAttendanceMark(student.registerId, date, student.sid, hourIndex, next)
        }
    }

    fun allPresent() {
        val state = uiState.value
        val date = state.selectedDate ?: return
        val registerId = state.register?.id ?: return
        undoable(date) { repository.allPresent(registerId, date) }
    }

    fun clearDay() {
        val state = uiState.value
        val date = state.selectedDate ?: return
        val registerId = state.register?.id ?: return
        undoable(date) { repository.clearAttendanceDay(registerId, date) }
    }

    fun clearHour(hourIndex: Int) {
        val state = uiState.value
        val date = state.selectedDate ?: return
        val registerId = state.register?.id ?: return
        undoable(date) { repository.clearAttendanceHour(registerId, date, hourIndex) }
    }

    fun toggleComplete() {
        val state = uiState.value
        val day = state.selectedDay ?: return
        undoable(day.date) { repository.setDayComplete(day.registerId, day.date, !day.isComplete) }
    }

    fun undo() = viewModelScope.launch {
        val snapshot = undoSnapshot.value ?: return@launch
        runCatching { repository.restoreDaySnapshot(snapshot.day, snapshot.marks) }
            .onSuccess {
                undoSnapshot.value = null
                message.value = "Previous attendance state restored"
            }
            .onFailure { message.value = it.message ?: "Undo failed" }
    }

    private fun undoable(date: String, block: suspend () -> Unit) = viewModelScope.launch {
        val registerId = uiState.value.register?.id ?: return@launch
        runCatching {
            val snapshot = repository.snapshotDay(registerId, date)
            block()
            UndoSnapshot(snapshot.first, snapshot.second)
        }.onSuccess {
            undoSnapshot.value = it
        }.onFailure {
            message.value = it.message ?: "Attendance update failed"
        }
    }

    private fun moveDate(delta: Int) {
        val state = uiState.value
        val date = state.selectedDate ?: return
        val index = state.days.indexOfFirst { it.date == date }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, state.days.lastIndex)
        selectedDate.value = state.days[target].date
    }

    private fun resolveDate(days: List<TeachingDayEntity>, requested: String?): String? {
        if (days.isEmpty()) return null
        if (requested != null && days.any { it.date == requested }) return requested
        val today = LocalDate.now().toString()
        return days.firstOrNull { it.date == today }?.date
            ?: days.lastOrNull { it.date <= today }?.date
            ?: days.first().date
    }

    private fun dateWindow(days: List<TeachingDayEntity>, selected: String?, size: Int): List<TeachingDayEntity> {
        if (days.isEmpty() || selected == null) return emptyList()
        val index = days.indexOfFirst { it.date == selected }.coerceAtLeast(0)
        val maxStart = (days.size - size).coerceAtLeast(0)
        val start = (index - size / 2).coerceIn(0, maxStart)
        return days.drop(start).take(size)
    }

    class Factory(
        private val repository: VarugaiRepository,
        private val preferences: VarugaiPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GridViewModel(repository, preferences) as T
    }
}

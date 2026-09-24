package com.gasczoology.varugai.ui.summary

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
import com.gasczoology.varugai.domain.attendance.AttendanceCategory
import com.gasczoology.varugai.domain.attendance.AttendanceForecast
import com.gasczoology.varugai.domain.attendance.AttendanceTotals
import com.gasczoology.varugai.domain.attendance.EligibilityAssessment
import com.gasczoology.varugai.domain.attendance.EligibilityClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class SummaryFilter { ALL, ELIGIBLE, CONDONATION, REPEAT, NEAR_THRESHOLD }

data class StudentSummaryRow(
    val student: StudentEntity,
    val totals: AttendanceTotals,
    val assessment: EligibilityAssessment,
    val forecast: AttendanceForecast,
)

data class SummaryUiState(
    val register: RegisterEntity? = null,
    val rows: List<StudentSummaryRow> = emptyList(),
    val visibleRows: List<StudentSummaryRow> = emptyList(),
    val filter: SummaryFilter = SummaryFilter.ALL,
    val query: String = "",
)

private data class SummaryRaw(
    val register: RegisterEntity? = null,
    val students: List<StudentEntity> = emptyList(),
    val days: List<TeachingDayEntity> = emptyList(),
    val marks: List<AttendanceMarkEntity> = emptyList(),
)

class SummaryViewModel(
    private val repository: VarugaiRepository,
    preferences: VarugaiPreferences,
) : ViewModel() {
    private val filter = MutableStateFlow(SummaryFilter.ALL)
    private val query = MutableStateFlow("")

    private val currentRegister: Flow<RegisterEntity?> =
        combine(repository.registers, preferences.currentRegisterId) { registers, id ->
            registers.firstOrNull { it.id == id } ?: registers.firstOrNull()
        }

    private val raw = currentRegister.flatMapLatest { register ->
        if (register == null) flowOf(SummaryRaw())
        else combine(
            repository.observeStudents(register.id),
            repository.observeTeachingDays(register.id),
            repository.observeAttendance(register.id),
        ) { students, days, marks ->
            SummaryRaw(register, students, days, marks)
        }
    }

    val uiState: StateFlow<SummaryUiState> = combine(raw, filter, query) { data, selectedFilter, text ->
        val register = data.register
        val rows = if (register == null) emptyList() else {
            data.students.sortedBy { it.rosterOrder }.map { student ->
                val totals = AttendanceCalculator.totals(student, data.days, data.marks)
                val assessment = EligibilityClassifier.assess(totals, register)
                StudentSummaryRow(
                    student = student,
                    totals = totals,
                    assessment = assessment,
                    forecast = EligibilityClassifier.forecast(totals, register.passMark),
                )
            }
        }
        val q = text.trim().lowercase()
        val visible = rows.filter { row ->
            val textMatch = q.isBlank() ||
                row.student.name.lowercase().contains(q) ||
                row.student.roll.lowercase().contains(q) ||
                row.student.registerNumber.lowercase().contains(q)
            val filterMatch = when (selectedFilter) {
                SummaryFilter.ALL -> true
                SummaryFilter.ELIGIBLE -> row.assessment.category == AttendanceCategory.ELIGIBLE
                SummaryFilter.CONDONATION -> row.assessment.category == AttendanceCategory.CONDONATION_FEE ||
                    row.assessment.category == AttendanceCategory.CONDONATION_MEDICAL
                SummaryFilter.REPEAT -> row.assessment.category == AttendanceCategory.REPEAT
                SummaryFilter.NEAR_THRESHOLD -> row.assessment.nearThreshold
            }
            textMatch && filterMatch
        }
        SummaryUiState(register, rows, visible, selectedFilter, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    fun setFilter(value: SummaryFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }

    class Factory(
        private val repository: VarugaiRepository,
        private val preferences: VarugaiPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SummaryViewModel(repository, preferences) as T
    }
}

package com.gasczoology.varugai.ui.summary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gasczoology.varugai.domain.attendance.AttendanceCategory
import java.util.Locale

@Composable
fun SummaryScreen(
    state: SummaryUiState,
    onFilter: (SummaryFilter) -> Unit,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val register = state.register
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Attendance summary", style = MaterialTheme.typography.headlineSmall)
            if (register != null) {
                Text("Eligibility ≥ ${fmt(register.passMark)}% · condonation bands ${fmt(register.condonationMedicalFloor)}–<${fmt(register.condonationFeeFloor)} and ${fmt(register.condonationFeeFloor)}–<${fmt(register.passMark)}")
                Text("Percentages are classified before display rounding.")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(state.filter == SummaryFilter.ALL, { onFilter(SummaryFilter.ALL) }, { Text("All") })
                FilterChip(state.filter == SummaryFilter.ELIGIBLE, { onFilter(SummaryFilter.ELIGIBLE) }, { Text("Eligible") })
                FilterChip(state.filter == SummaryFilter.CONDONATION, { onFilter(SummaryFilter.CONDONATION) }, { Text("Condonation") })
                FilterChip(state.filter == SummaryFilter.REPEAT, { onFilter(SummaryFilter.REPEAT) }, { Text("Repeat") })
                FilterChip(state.filter == SummaryFilter.NEAR_THRESHOLD, { onFilter(SummaryFilter.NEAR_THRESHOLD) }, { Text("Near threshold") })
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                label = { Text("Find name / roll / register no.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("${state.visibleRows.size} of ${state.rows.size} student(s)")
        }

        items(state.visibleRows, key = { it.student.sid }) { row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${row.student.roll} · ${row.student.name}", style = MaterialTheme.typography.titleMedium)
                    if (row.student.registerNumber.isNotBlank()) Text("Reg. No: ${row.student.registerNumber}")
                    Text("P ${row.totals.presentHours} · OD ${row.totals.odHours} · A ${row.totals.absentHours} · Counted ${row.totals.countedHours} h")
                    Text("Attendance: ${row.totals.percentage?.let { fmt(it) + "%" } ?: "—"}")
                    Text(row.assessment.label)
                    if (row.assessment.nearThreshold) Text("Near threshold — verify source attendance records")
                    if (register != null && row.totals.countedHours < register.minimumCountedHours) {
                        Text("Provisional: only ${row.totals.countedHours} counted hour(s); minimum for standing display is ${register.minimumCountedHours}.")
                    }
                    when {
                        row.forecast.hoursNeededToReachEligible != null && register != null ->
                            Text("Forecast: ${row.forecast.hoursNeededToReachEligible} additional attended hour(s), with no further absence, would reach ${fmt(register.passMark)}%.")
                        row.forecast.additionalAbsenceHoursAffordable != null && register != null ->
                            Text("Forecast: up to ${row.forecast.additionalAbsenceHoursAffordable} additional absence hour(s) could occur before falling below ${fmt(register.passMark)}%, assuming no other changes.")
                        row.totals.countedHours == 0 ->
                            Text("Forecast unavailable until at least one teaching day is completed.")
                    }
                    if (row.assessment.category == AttendanceCategory.PROVISIONAL) {
                        Text("This is not a final eligibility classification.")
                    }
                }
            }
        }
    }
}

private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

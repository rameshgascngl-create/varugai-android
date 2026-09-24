package com.gasczoology.varugai.ui.grid

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.domain.attendance.AttendanceCalculator

@Composable
fun GridScreen(
    state: GridUiState,
    onSelectDate: (String) -> Unit,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onToday: () -> Unit,
    onSetWindowSize: (Int) -> Unit,
    onFilter: (GridStudentFilter) -> Unit,
    onQuery: (String) -> Unit,
    onCycleMark: (StudentEntity, Int) -> Unit,
    onAllPresent: () -> Unit,
    onClearDay: () -> Unit,
    onClearHour: (Int) -> Unit,
    onToggleComplete: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = state.selectedDay
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Attendance grid", style = MaterialTheme.typography.headlineSmall)
            Text(
                state.register?.let {
                    listOf(it.courseCode, it.className, it.semester)
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                }?.ifBlank { "Current register" } ?: "No register"
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onPreviousDate, enabled = state.days.isNotEmpty()) { Text("←") }
                OutlinedButton(onClick = onToday, enabled = state.days.isNotEmpty()) { Text("Today") }
                OutlinedButton(onClick = onNextDate, enabled = state.days.isNotEmpty()) { Text("→") }
                listOf(3, 5, 7).forEach { size ->
                    TextButton(onClick = { onSetWindowSize(size) }) {
                        Text(if (state.windowSize == size) "[$size]" else size.toString())
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.windowDates.forEach { date ->
                    val label = date.date.takeLast(5) +
                        if (!date.isWorking) " H" else if (date.isComplete) " ✓" else ""
                    if (date.date == state.selectedDate) {
                        Button(onClick = { onSelectDate(date.date) }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { onSelectDate(date.date) }) { Text(label) }
                    }
                }
            }
        }

        item {
            if (day == null) {
                Text("Generate a teaching calendar in Setup before entering attendance.")
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${day.date} · ${if (day.isWorking) "${day.hours} teaching hour(s)" else "Holiday / excluded"}")
                        Text(
                            if (day.isComplete) {
                                "Day status: COMPLETED — included in denominator"
                            } else {
                                "Day status: OPEN — excluded from denominator"
                            }
                        )
                        if (day.isWorking) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(onClick = onAllPresent) { Text("All Present") }
                                OutlinedButton(onClick = onClearDay) { Text("Clear day") }
                                OutlinedButton(onClick = onUndo, enabled = state.canUndo) { Text("Undo") }
                                Button(onClick = onToggleComplete) { Text(if (day.isComplete) "Reopen" else "Complete day") }
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (hour in 1..day.hours) {
                                    TextButton(onClick = { onClearHour(hour) }) { Text("Clear H$hour") }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == GridStudentFilter.ALL,
                    onClick = { onFilter(GridStudentFilter.ALL) },
                    label = { Text("All students") },
                )
                FilterChip(
                    selected = state.filter == GridStudentFilter.ACTIVE,
                    onClick = { onFilter(GridStudentFilter.ACTIVE) },
                    label = { Text("Active on date") },
                )
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                label = { Text("Find name / roll / register no.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("${state.visibleStudents.size} of ${state.students.size} student(s)")
        }

        if (day != null) {
            items(state.visibleStudents, key = { it.sid }) { student ->
                StudentAttendanceRow(
                    student = student,
                    date = day.date,
                    hours = day.hours,
                    dayWorking = day.isWorking,
                    marks = state.marksByCell,
                    onCycleMark = onCycleMark,
                )
            }
        }
    }
}

@Composable
private fun StudentAttendanceRow(
    student: StudentEntity,
    date: String,
    hours: Int,
    dayWorking: Boolean,
    marks: Map<Pair<String, Int>, String>,
    onCycleMark: (StudentEntity, Int) -> Unit,
) {
    val active = dayWorking && AttendanceCalculator.isActiveOn(student, date)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${student.roll} · ${student.name}", style = MaterialTheme.typography.titleSmall)
            if (!active) Text("Outside counted attendance period for this date")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (hour in 1..hours) {
                    val status = marks[student.sid to hour]
                    OutlinedButton(
                        enabled = active,
                        onClick = { onCycleMark(student, hour) },
                    ) {
                        Text("H$hour ${status ?: "–"}")
                    }
                }
            }
        }
    }
}

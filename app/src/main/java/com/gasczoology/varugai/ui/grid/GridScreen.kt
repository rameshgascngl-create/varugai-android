package com.gasczoology.varugai.ui.grid

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
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
    onCycleMark: (StudentEntity, String, Int) -> Unit,
    onAllPresent: () -> Unit,
    onClearDay: () -> Unit,
    onClearHour: (Int) -> Unit,
    onToggleComplete: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = state.selectedDay
    val matrixScroll = rememberScrollState()
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

        if (state.days.isNotEmpty()) {
            item {
                Text("Semester grid — student names stay on the left; drag the dates horizontally →", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth()) {
                    Card(Modifier.width(180.dp)) {
                        Text("Student", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.titleSmall)
                    }
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(matrixScroll),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.days.forEach { gridDay ->
                            DayHeaderCell(
                                day = gridDay,
                                selected = gridDay.date == state.selectedDate,
                                onClick = { onSelectDate(gridDay.date) },
                            )
                        }
                    }
                }
            }
            items(state.visibleStudents, key = { it.sid }) { student ->
                StudentMatrixRow(
                    student = student,
                    days = state.days,
                    marks = state.allMarksByCell,
                    scroll = matrixScroll,
                    onCycleMark = onCycleMark,
                )
            }
        }
    }
}

@Composable
private fun DayHeaderCell(
    day: com.gasczoology.varugai.data.db.TeachingDayEntity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
    val ddmm = if (date != null) {
        date.dayOfMonth.toString().padStart(2, '0') + "/" + date.monthValue.toString().padStart(2, '0')
    } else day.date
    val weekday = date?.dayOfWeek?.name?.take(3).orEmpty()
    val label = ddmm + "\n" + weekday + if (!day.isWorking) "\nHOL" else if (day.isComplete) "\n✓" else ""
    val width = dayColumnWidth(day)
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.width(width)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.width(width)) { Text(label) }
    }
}

@Composable
private fun StudentMatrixRow(
    student: StudentEntity,
    days: List<com.gasczoology.varugai.data.db.TeachingDayEntity>,
    marks: Map<Triple<String, String, Int>, String>,
    scroll: androidx.compose.foundation.ScrollState,
    onCycleMark: (StudentEntity, String, Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Card(Modifier.width(180.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text(student.name, style = MaterialTheme.typography.titleSmall)
                Text(student.roll, style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            days.forEach { day ->
                DayAttendanceCell(student, day, marks, onCycleMark)
            }
        }
    }
}

@Composable
private fun DayAttendanceCell(
    student: StudentEntity,
    day: com.gasczoology.varugai.data.db.TeachingDayEntity,
    marks: Map<Triple<String, String, Int>, String>,
    onCycleMark: (StudentEntity, String, Int) -> Unit,
) {
    val active = day.isWorking && AttendanceCalculator.isActiveOn(student, day.date)
    Card(Modifier.width(dayColumnWidth(day))) {
        if (!day.isWorking) {
            Column(Modifier.padding(8.dp)) {
                Text("HOL", style = MaterialTheme.typography.labelLarge)
                if (day.note.isNotBlank()) Text(day.note, style = MaterialTheme.typography.labelSmall)
            }
        } else if (!active) {
            Text("—", modifier = Modifier.padding(12.dp))
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (hour in 1..day.hours) {
                    val status = marks[Triple(day.date, student.sid, hour)]
                    TextButton(onClick = { onCycleMark(student, day.date, hour) }) {
                        Text(hour.toString() + ":" + (status ?: "–"))
                    }
                }
            }
        }
    }
}

private fun dayColumnWidth(day: com.gasczoology.varugai.data.db.TeachingDayEntity): Dp =
    maxOf(112, 50 * day.hours).dp

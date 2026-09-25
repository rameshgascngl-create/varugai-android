package com.gasczoology.varugai.ui.grid

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.domain.attendance.AttendanceCalculator
import com.gasczoology.varugai.ui.common.displayDate
import com.gasczoology.varugai.ui.common.displayShortDate
import com.gasczoology.varugai.ui.common.displayWeekday

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
    var accessibleMode by rememberSaveable { mutableStateOf(false) }
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
                    val label = displayShortDate(date.date) +
                        if (!date.isWorking) " HOL" else if (date.isComplete) " ✓" else ""
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
                        Text("${displayDate(day.date)} · ${displayWeekday(day.date)} · ${if (day.isWorking) "${day.hours} teaching hour(s)" else "Holiday / excluded"}")
                        if (!day.isWorking && day.note.isNotBlank()) Text(day.note, style = MaterialTheme.typography.titleSmall)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusLegend("P", "Present")
                StatusLegend("A", "Absent")
                StatusLegend("OD", "On Duty")
                StatusLegend("–", "Blank")
            }
            Text("Recommended entry: All Present → tap only exceptions to A or OD → Complete day.")
        }

        item {
            OutlinedButton(onClick = { accessibleMode = !accessibleMode }) {
                Text(if (accessibleMode) "Show semester matrix" else "Accessible selected-day view")
            }
            if (accessibleMode) {
                Text("Student-by-student controls announce the student, date, hour and current attendance state for screen readers.")
            }
        }

        if (accessibleMode && day != null) {
            if (!day.isWorking) {
                item {
                    Text("Selected date is a holiday / excluded day${if (day.note.isNotBlank()) ": ${day.note}" else ""}.")
                }
            } else {
                items(state.visibleStudents, key = { "accessible-${it.sid}" }) { student ->
                    AccessibleStudentDayRow(
                        student = student,
                        day = day,
                        marks = state.allMarksByCell,
                        onCycleMark = onCycleMark,
                    )
                }
            }
        } else if (state.days.isNotEmpty()) {
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
private fun AccessibleStudentDayRow(
    student: StudentEntity,
    day: com.gasczoology.varugai.data.db.TeachingDayEntity,
    marks: Map<Triple<String, String, Int>, String>,
    onCycleMark: (StudentEntity, String, Int) -> Unit,
) {
    val active = AttendanceCalculator.isActiveOn(student, day.date)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${student.name} · Roll ${student.roll}", style = MaterialTheme.typography.titleSmall)
            if (!active) {
                Text("Not counted on ${displayDate(day.date)}")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (hour in 1..day.hours) {
                        val status = marks[Triple(day.date, student.sid, hour)]
                        AttendanceCell(
                            hour = hour,
                            status = status,
                            description = "${student.name}, ${displayDate(day.date)}, hour ${hour}, ${statusDescription(status)}. Double tap to change attendance.",
                            onClick = { onCycleMark(student, day.date, hour) },
                        )
                    }
                }
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
    val label = displayShortDate(day.date) + "\n" +
        displayWeekday(day.date).take(3) +
        if (!day.isWorking) "\nHOL" else if (day.isComplete) "\n✓" else ""
    val width = dayColumnWidth(day)
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.width(width)) { Text(label, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.width(width)) { Text(label, textAlign = TextAlign.Center) }
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
        Card(Modifier.width(170.dp)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(student.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text("Roll ${student.roll}", style = MaterialTheme.typography.labelMedium)
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
        when {
            !day.isWorking -> {
                Column(Modifier.padding(8.dp)) {
                    Text("HOL", style = MaterialTheme.typography.labelLarge)
                    if (day.note.isNotBlank()) Text(day.note, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
            !active -> Text("Not counted", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium)
            else -> Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (hour in 1..day.hours) {
                    val status = marks[Triple(day.date, student.sid, hour)]
                    AttendanceCell(
                        hour = hour,
                        status = status,
                        description = "${student.name}, ${displayDate(day.date)}, hour ${hour}, ${statusDescription(status)}. Double tap to change attendance.",
                        onClick = { onCycleMark(student, day.date, hour) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceCell(hour: Int, status: String?, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background = when (status) {
        "P" -> colors.primaryContainer
        "A" -> colors.errorContainer
        "O" -> colors.tertiaryContainer
        else -> colors.surfaceVariant
    }
    val foreground = when (status) {
        "P" -> colors.onPrimaryContainer
        "A" -> colors.onErrorContainer
        "O" -> colors.onTertiaryContainer
        else -> colors.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.width(48.dp).sizeIn(minHeight = 48.dp).semantics { contentDescription = description },
        color = background,
        contentColor = foreground,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "H$hour\n${displayStatus(status)}",
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatusLegend(code: String, label: String) {
    Text("$code $label", style = MaterialTheme.typography.labelMedium)
}

private fun displayStatus(status: String?): String = when (status) {
    "O" -> "OD"
    null -> "–"
    else -> status
}

private fun statusDescription(status: String?): String = when (status) {
    "P" -> "Present"
    "A" -> "Absent"
    "O" -> "On Duty"
    else -> "Blank"
}

private fun dayColumnWidth(day: com.gasczoology.varugai.data.db.TeachingDayEntity): Dp =
    maxOf(112, 52 * day.hours + 8).dp

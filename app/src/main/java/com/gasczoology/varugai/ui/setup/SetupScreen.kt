package com.gasczoology.varugai.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import com.gasczoology.varugai.ui.common.displayDate
import com.gasczoology.varugai.ui.common.displayWeekday

@Composable
fun SetupScreen(
    state: SetupUiState,
    onSelectRegister: (String) -> Unit,
    onSave: (RegisterEntity, Set<Int>) -> Unit,
    onNew: (RegisterEntity?) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onGenerateCalendar: (RegisterEntity, Set<Int>) -> Unit,
    onCopyCalendar: (String, String) -> Unit,
    onUpdateTeachingDay: (TeachingDayEntity) -> Unit,
    onAddWorkingDay: (String, Int) -> Unit,
    onSetDayOfWeekWorking: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.current
    if (current == null) {
        Column(modifier.padding(24.dp)) { Text("Preparing local register…") }
        return
    }

    var edit by remember(current.id, current.updatedAt) { mutableStateOf(current) }
    var weekdays by remember(current.id, current.calendarWeekdaysCsv) { mutableStateOf(state.selectedWeekdays) }
    var specialDate by remember(current.id) { mutableStateOf(current.startDate.ifBlank { LocalDate.now().toString() }) }
    var specialHours by remember(current.id) { mutableStateOf(current.defaultHours.toString()) }
    var holidayDate by remember(current.id) { mutableStateOf(current.startDate.ifBlank { LocalDate.now().toString() }) }
    var holidayName by remember(current.id) { mutableStateOf("") }
    var confirmDeleteRegister by remember(current.id) { mutableStateOf(false) }
    val setupDateRangeValid = remember(edit.startDate, edit.endDate) {
        runCatching {
            val start = LocalDate.parse(edit.startDate)
            val end = LocalDate.parse(edit.endDate)
            !end.isBefore(start)
        }.getOrDefault(false)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Register setup", style = MaterialTheme.typography.headlineSmall)
            Text("Native VARUGAI 16 · local Room database · no WebView or camera subsystem")
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Registers", style = MaterialTheme.typography.titleMedium)
                    state.registers.forEach { register ->
                        TextButton(onClick = { onSelectRegister(register.id) }) {
                            Text(if (register.id == current.id) "✓ ${registerLabel(register)}" else registerLabel(register))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onNew(current) }) { Text("New class") }
                        OutlinedButton(onClick = onDuplicate) { Text("Duplicate") }
                        OutlinedButton(onClick = { confirmDeleteRegister = true }, enabled = state.registers.size > 1) { Text("Delete") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Class details", style = MaterialTheme.typography.titleMedium)
                    Field("Institution", edit.institution) { edit = edit.copy(institution = it) }
                    Field("Department", edit.department) { edit = edit.copy(department = it) }
                    Field("Faculty in charge", edit.faculty) { edit = edit.copy(faculty = it) }
                    Field("Course code", edit.courseCode) { edit = edit.copy(courseCode = it) }
                    Field("Course title", edit.courseTitle) { edit = edit.copy(courseTitle = it) }
                    Field("Class / programme", edit.className) { edit = edit.copy(className = it) }
                    Field("Semester", edit.semester) { edit = edit.copy(semester = it) }
                    Field("Academic year", edit.academicYear) { edit = edit.copy(academicYear = it) }
                    DateDropdownPicker("Start date", edit.startDate) { edit = edit.copy(startDate = it) }
                    DateDropdownPicker("End date", edit.endDate) { edit = edit.copy(endDate = it) }
                    ChoiceDropdown("Total hours per day", edit.defaultHours.toString(), (1..8).map(Int::toString)) {
                        edit = edit.copy(defaultHours = it.toInt())
                        specialHours = it
                    }
                    if (!setupDateRangeValid && (edit.startDate.isNotBlank() || edit.endDate.isNotBlank())) {
                        Text("End date must be the same as or later than the start date.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Attendance rules", style = MaterialTheme.typography.titleMedium)
                    DoubleField("Eligible threshold (%)", edit.passMark, 0.0, 100.0) { edit = edit.copy(passMark = it) }
                    DoubleField("Condonation + fee floor (%)", edit.condonationFeeFloor, 0.0, 100.0) { edit = edit.copy(condonationFeeFloor = it) }
                    DoubleField("Condonation + fee + medical floor (%)", edit.condonationMedicalFloor, 0.0, 100.0) { edit = edit.copy(condonationMedicalFloor = it) }
                    DoubleField("Verify band (percentage points)", edit.verifyBandPoints, 0.0, 20.0) { edit = edit.copy(verifyBandPoints = it) }
                    IntField("Minimum counted hours before percentage", edit.minimumCountedHours, 0, 1000) { edit = edit.copy(minimumCountedHours = it) }
                    Field("Rule-set label", edit.ruleSetLabel) { edit = edit.copy(ruleSetLabel = it) }
                    Text("Classification is performed on the unrounded percentage. Display rounding must not change eligibility.")
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Teaching weekdays", style = MaterialTheme.typography.titleMedium)
                    dayNames.forEachIndexed { index, label ->
                        val day = index + 1
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = day in weekdays,
                                onCheckedChange = { checked -> weekdays = if (checked) weekdays + day else weekdays - day },
                            )
                            Text(label, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSave(edit, weekdays) }) { Text("Save setup") }
                        OutlinedButton(
                            onClick = { onGenerateCalendar(edit, weekdays) },
                            enabled = setupDateRangeValid && weekdays.isNotEmpty(),
                        ) { Text("Generate calendar") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Calendar controls", style = MaterialTheme.typography.titleMedium)
                    Text("Use these controls for recurring Saturdays/Sundays. A non-working date contributes zero attendance hours.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSetDayOfWeekWorking(6, true) }) { Text("Saturdays working") }
                        OutlinedButton(onClick = { onSetDayOfWeekWorking(6, false) }) { Text("Saturdays holiday") }
                    }
                    OutlinedButton(onClick = { onSetDayOfWeekWorking(7, false) }) { Text("Sundays holiday") }
                    DateDropdownPicker("Special working date", specialDate) { specialDate = it }
                    ChoiceDropdown("Hours for special day", specialHours, (1..8).map(Int::toString)) { specialHours = it }
                    Button(
                        enabled = specialDate.isNotBlank(),
                        onClick = { onAddWorkingDay(specialDate, specialHours.toIntOrNull() ?: current.defaultHours) },
                    ) { Text("Add special working day") }
                    Text("Festival / weekday holiday within the selected semester", style = MaterialTheme.typography.titleSmall)
                    DateDropdownPicker("Holiday date", holidayDate) { holidayDate = it }
                    Field("Holiday / festival name", holidayName) { holidayName = it }
                    val holidayDay = state.teachingDays.firstOrNull { it.date == holidayDate }
                    Button(
                        enabled = holidayDay != null && holidayName.isNotBlank(),
                        onClick = {
                            holidayDay?.let { onUpdateTeachingDay(it.copy(isWorking = false, isComplete = false, note = holidayName.trim())) }
                            holidayName = ""
                        },
                    ) { Text("Mark selected date as holiday") }
                    if (state.teachingDays.isEmpty()) Text("Generate the calendar first, then add festival/weekday holidays inside the chosen date range.")
                }
            }
        }

        if (state.teachingDays.isNotEmpty()) {
            item {
                val working = state.teachingDays.filter { it.isWorking }
                val holidays = state.teachingDays.size - working.size
                val plannedHours = working.sumOf { it.hours }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Semester plan", style = MaterialTheme.typography.titleMedium)
                        Text("${working.size} working day(s) · $holidays holiday/excluded day(s)")
                        Text("$plannedHours planned teaching hour(s) at the current calendar settings")
                    }
                }
            }
        }

        if (state.registers.any { it.id != current.id }) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Copy calendar", style = MaterialTheme.typography.titleMedium)
                        Text("Copies dates/hours from another register without copying attendance marks.")
                        state.registers.filter { it.id != current.id }.forEach { source ->
                            OutlinedButton(onClick = { onCopyCalendar(source.id, current.id) }) { Text("Copy from ${registerLabel(source)}") }
                        }
                    }
                }
            }
        }

        if (state.teachingDays.isNotEmpty()) {
            item { Text("Calendar dates", style = MaterialTheme.typography.titleMedium) }
            items(state.teachingDays, key = { it.date }) { day ->
                TeachingDayCard(day, onUpdateTeachingDay)
            }
        }
    }
    if (confirmDeleteRegister) {
        AlertDialog(
            onDismissRequest = { confirmDeleteRegister = false },
            title = { Text("Delete this register?") },
            text = { Text("This removes the selected register and its locally stored roster, calendar, attendance and audit history. Export a backup first if the data may be needed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteRegister = false
                    onDelete()
                }) { Text("Delete register") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteRegister = false }) { Text("Cancel") } },
        )
    }

}

@Composable
private fun TeachingDayCard(day: TeachingDayEntity, onUpdate: (TeachingDayEntity) -> Unit) {
    var hoursText by remember(day.date, day.hours) { mutableStateOf(day.hours.toString()) }
    var note by remember(day.date, day.note) { mutableStateOf(day.note) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${displayDate(day.date)} · ${displayWeekday(day.date)}", style = MaterialTheme.typography.titleSmall)
                    Text(if (day.isWorking) "Working day · ${day.hours} hour(s)" else "Holiday / excluded${if (day.note.isNotBlank()) " · ${day.note}" else ""}")
                }
                Switch(
                    checked = day.isWorking,
                    onCheckedChange = { working -> onUpdate(day.copy(isWorking = working, isComplete = if (working) day.isComplete else false)) },
                )
            }
            IntTextField("Hours", hoursText) { hoursText = it.filter(Char::isDigit) }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = {
                onUpdate(day.copy(hours = hoursText.toIntOrNull()?.coerceIn(1, 8) ?: day.hours, note = note))
            }) { Text("Save day") }
        }
    }
}

@Composable
private fun DateDropdownPicker(label: String, isoDate: String, onIsoDate: (String) -> Unit) {
    val today = remember { LocalDate.now() }
    val parsed = remember(isoDate) { runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: today }
    var day by remember(isoDate) { mutableStateOf(parsed.dayOfMonth) }
    var month by remember(isoDate) { mutableStateOf(parsed.monthValue) }
    var year by remember(isoDate) { mutableStateOf(parsed.year) }

    fun commit(newDay: Int = day, newMonth: Int = month, newYear: Int = year) {
        val safeDay = newDay.coerceAtMost(YearMonth.of(newYear, newMonth).lengthOfMonth())
        day = safeDay
        month = newMonth
        year = newYear
        onIsoDate(LocalDate.of(newYear, newMonth, safeDay).toString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceDropdown("Day", day.toString().padStart(2, '0'), (1..YearMonth.of(year, month).lengthOfMonth()).map { it.toString().padStart(2, '0') }) {
                commit(newDay = it.toInt())
            }
            ChoiceDropdown("Month", month.toString().padStart(2, '0'), (1..12).map { it.toString().padStart(2, '0') }) {
                commit(newMonth = it.toInt())
            }
            ChoiceDropdown("Year", year.toString(), (2000..2100).map(Int::toString)) {
                commit(newYear = it.toInt())
            }
        }
        Text(if (isoDate.isBlank()) "Selected: Not set — choose day/month/year" else "Selected: " + day.toString().padStart(2, '0') + "/" + month.toString().padStart(2, '0') + "/" + year)
    }
}

@Composable
private fun ChoiceDropdown(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun IntTextField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun IntField(label: String, value: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter(Char::isDigit)
            text.toIntOrNull()?.coerceIn(min, max)?.let(onValue)
        },
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
}

@Composable
private fun DoubleField(label: String, value: Double, min: Double, max: Double, onValue: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() || it == '.' }
            text.toDoubleOrNull()?.coerceIn(min, max)?.let(onValue)
        },
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
}

private val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun registerLabel(register: RegisterEntity): String = listOf(
    register.courseCode, register.className, register.semester
).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { register.courseTitle.ifBlank { "Untitled register" } }

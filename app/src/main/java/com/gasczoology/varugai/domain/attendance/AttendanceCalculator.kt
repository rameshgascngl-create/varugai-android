package com.gasczoology.varugai.domain.attendance

import com.gasczoology.varugai.data.db.AttendanceMarkEntity
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import java.time.LocalDate

data class DayCompletionCheck(
    val activeStudents: Int,
    val expectedCells: Int,
    val enteredCells: Int,
    val missingCells: Int,
)

data class AttendanceTotals(
    val presentHours: Int,
    val odHours: Int,
    val absentHours: Int,
    val countedHours: Int,
) {
    val attendedHours: Int get() = presentHours + odHours
    val percentage: Double? get() = countedHours.takeIf { it > 0 }?.let { attendedHours * 100.0 / it }
}

object AttendanceCalculator {
    private val validStatuses = setOf("P", "A", "O")

    fun isActiveOn(student: StudentEntity, date: String): Boolean {
        val d = LocalDate.parse(date)
        val from = student.admissionDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        val until = student.attendanceEndDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        return (from == null || !d.isBefore(from)) && (until == null || !d.isAfter(until))
    }

    fun dayCompletionCheck(
        day: TeachingDayEntity,
        students: List<StudentEntity>,
        marks: List<AttendanceMarkEntity>,
    ): DayCompletionCheck {
        if (!day.isWorking || day.hours <= 0) return DayCompletionCheck(0, 0, 0, 0)
        val active = students.filter { it.registerId == day.registerId && isActiveOn(it, day.date) }
        val activeIds = active.mapTo(hashSetOf()) { it.sid }
        val entered = marks.asSequence()
            .filter { it.registerId == day.registerId && it.date == day.date }
            .filter { it.sid in activeIds && it.hourIndex in 1..day.hours && it.status in validStatuses }
            .map { it.sid to it.hourIndex }
            .distinct()
            .count()
        val expected = active.size * day.hours
        return DayCompletionCheck(active.size, expected, entered, (expected - entered).coerceAtLeast(0))
    }

    fun totals(
        student: StudentEntity,
        days: List<TeachingDayEntity>,
        marks: List<AttendanceMarkEntity>,
    ): AttendanceTotals {
        val countedDays = days.filter {
            it.registerId == student.registerId &&
                it.isWorking &&
                it.isComplete &&
                it.hours > 0 &&
                isActiveOn(student, it.date)
        }
        val countedHours = countedDays.sumOf { it.hours }
        val allowed = countedDays.associate { it.date to it.hours }
        var present = 0
        var od = 0
        marks.asSequence()
            .filter { it.registerId == student.registerId && it.sid == student.sid }
            .filter { mark -> allowed[mark.date]?.let { mark.hourIndex in 1..it } == true }
            .forEach {
                when (it.status) {
                    "P" -> present++
                    "O" -> od++
                }
            }
        val absent = (countedHours - present - od).coerceAtLeast(0)
        return AttendanceTotals(present, od, absent, countedHours)
    }
}

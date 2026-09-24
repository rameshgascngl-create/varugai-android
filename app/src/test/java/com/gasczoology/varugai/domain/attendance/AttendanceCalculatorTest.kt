package com.gasczoology.varugai.domain.attendance

import com.gasczoology.varugai.data.db.AttendanceMarkEntity
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceCalculatorTest {
    private val student = StudentEntity("r1", "s1", "1", "", "Arun", "", "", 0)

    @Test fun incompleteDaysDoNotEnterDenominator() {
        val days = listOf(
            TeachingDayEntity("r1", "2026-07-01", 5, true, true),
            TeachingDayEntity("r1", "2026-07-02", 5, true, false),
        )
        val marks = (1..5).map { AttendanceMarkEntity("r1", "2026-07-01", "s1", it, "P") }
        val t = AttendanceCalculator.totals(student, days, marks)
        assertEquals(5, t.countedHours)
        assertEquals(5, t.presentHours)
        assertEquals(100.0, t.percentage!!, 0.0001)
    }

    @Test fun holidayContributesZeroAndShortDayUsesActualHours() {
        val days = listOf(
            TeachingDayEntity("r1", "2026-07-03", 5, false, false),
            TeachingDayEntity("r1", "2026-07-04", 3, true, true),
        )
        val marks = listOf(
            AttendanceMarkEntity("r1", "2026-07-04", "s1", 1, "P"),
            AttendanceMarkEntity("r1", "2026-07-04", "s1", 2, "O"),
            AttendanceMarkEntity("r1", "2026-07-04", "s1", 3, "A"),
        )
        val t = AttendanceCalculator.totals(student, days, marks)
        assertEquals(3, t.countedHours)
        assertEquals(1, t.presentHours)
        assertEquals(1, t.odHours)
        assertEquals(1, t.absentHours)
    }

    @Test fun odCountsAsAttendedButRemainsSeparate() {
        val day = TeachingDayEntity("r1", "2026-07-05", 2, true, true)
        val marks = listOf(
            AttendanceMarkEntity("r1", day.date, "s1", 1, "P"),
            AttendanceMarkEntity("r1", day.date, "s1", 2, "O"),
        )
        val t = AttendanceCalculator.totals(student, listOf(day), marks)
        assertEquals(2, t.attendedHours)
        assertEquals(1, t.odHours)
        assertEquals(100.0, t.percentage!!, 0.0001)
    }

    @Test fun admissionDateExcludesEarlierCompletedDays() {
        val later = student.copy(admissionDate = "2026-07-10")
        assertFalse(AttendanceCalculator.isActiveOn(later, "2026-07-09"))
        assertTrue(AttendanceCalculator.isActiveOn(later, "2026-07-10"))
        val days = listOf(
            TeachingDayEntity("r1", "2026-07-09", 5, true, true),
            TeachingDayEntity("r1", "2026-07-10", 5, true, true),
        )
        val marks = (1..5).map { AttendanceMarkEntity("r1", "2026-07-10", "s1", it, "P") }
        assertEquals(5, AttendanceCalculator.totals(later, days, marks).countedHours)
    }

    @Test fun completionCheckFindsBlankCellsOnlyForActiveStudents() {
        val later = StudentEntity("r1", "s2", "2", "", "Banu", "2026-07-20", "", 1)
        val day = TeachingDayEntity("r1", "2026-07-10", 2, true, false)
        val marks = listOf(AttendanceMarkEntity("r1", day.date, "s1", 1, "P"))
        val check = AttendanceCalculator.dayCompletionCheck(day, listOf(student, later), marks)
        assertEquals(1, check.activeStudents)
        assertEquals(2, check.expectedCells)
        assertEquals(1, check.enteredCells)
        assertEquals(1, check.missingCells)
    }
}

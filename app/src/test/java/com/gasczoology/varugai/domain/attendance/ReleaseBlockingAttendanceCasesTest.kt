package com.gasczoology.varugai.domain.attendance

import com.gasczoology.varugai.data.db.AttendanceMarkEntity
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseBlockingAttendanceCasesTest {
    private val register = RegisterEntity(
        id = "r1",
        passMark = 75.0,
        condonationFeeFloor = 65.0,
        condonationMedicalFloor = 50.0,
        verifyBandPoints = 1.0,
        minimumCountedHours = 1,
    )

    private fun student(
        sid: String = "s1",
        admissionDate: String = "",
        attendanceEndDate: String = "",
    ) = StudentEntity(
        registerId = "r1",
        sid = sid,
        roll = sid,
        registerNumber = "",
        name = sid,
        admissionDate = admissionDate,
        attendanceEndDate = attendanceEndDate,
        rosterOrder = 0,
    )

    private fun weekdays(start: String, count: Int): List<String> =
        generateSequence(LocalDate.parse(start)) { it.plusDays(1) }
            .filter { it.dayOfWeek.value in 1..5 }
            .take(count)
            .map(LocalDate::toString)
            .toList()

    private fun marksFor(
        sid: String,
        dates: List<String>,
        hours: Int,
        status: String,
    ): List<AttendanceMarkEntity> = dates.flatMap { date ->
        (1..hours).map { hour -> AttendanceMarkEntity("r1", date, sid, hour, status) }
    }

    @Test
    fun caseA_90Planned20Complete_is100PercentOn100HourDenominator() {
        val dates = weekdays("2026-07-01", 90)
        val days = dates.mapIndexed { index, date ->
            TeachingDayEntity("r1", date, 5, true, isComplete = index < 20)
        }
        val marks = marksFor("s1", dates.take(20), 5, "P")
        val totals = AttendanceCalculator.totals(student(), days, marks)

        assertEquals(100, totals.countedHours)
        assertEquals(100, totals.attendedHours)
        assertEquals(100.0, totals.percentage!!, 0.0000001)
    }

    @Test
    fun caseB_45DaysPresent5DaysAbsent_is90Percent() {
        val dates = weekdays("2026-07-01", 50)
        val days = dates.map { TeachingDayEntity("r1", it, 5, true, true) }
        val marks = marksFor("s1", dates.take(45), 5, "P")
        val totals = AttendanceCalculator.totals(student(), days, marks)

        assertEquals(250, totals.countedHours)
        assertEquals(225, totals.presentHours)
        assertEquals(25, totals.absentHours)
        assertEquals(90.0, totals.percentage!!, 0.0000001)
    }

    @Test
    fun caseC_allOd_countsAsAttendedAndSeparatelyTracked() {
        val day = TeachingDayEntity("r1", "2026-07-01", 5, true, true)
        val totals = AttendanceCalculator.totals(
            student(),
            listOf(day),
            marksFor("s1", listOf(day.date), 5, "O"),
        )

        assertEquals(0, totals.presentHours)
        assertEquals(5, totals.odHours)
        assertEquals(5, totals.attendedHours)
        assertEquals(100.0, totals.percentage!!, 0.0000001)
    }

    @Test
    fun caseD_workingDaySwitchedToHoliday_contributesZeroHours() {
        val holiday = TeachingDayEntity("r1", "2026-07-01", 5, isWorking = false, isComplete = false, note = "Festival")
        val staleMark = AttendanceMarkEntity("r1", holiday.date, "s1", 1, "P")
        val totals = AttendanceCalculator.totals(student(), listOf(holiday), listOf(staleMark))

        assertEquals(0, totals.countedHours)
        assertEquals(0, totals.attendedHours)
        assertNull(totals.percentage)
    }

    @Test
    fun caseE_midTermAdmission_excludesEarlierHoursWithoutChangingContinuousStudent() {
        val dates = weekdays("2026-07-01", 10)
        val days = dates.map { TeachingDayEntity("r1", it, 5, true, true) }
        val continuous = student("continuous")
        val late = student("late", admissionDate = dates[5])
        val marks = marksFor("continuous", dates, 5, "P") +
            marksFor("late", dates.drop(5), 5, "P")

        val fullTotals = AttendanceCalculator.totals(continuous, days, marks)
        val lateTotals = AttendanceCalculator.totals(late, days, marks)

        assertEquals(50, fullTotals.countedHours)
        assertEquals(25, lateTotals.countedHours)
        assertEquals(100.0, fullTotals.percentage!!, 0.0000001)
        assertEquals(100.0, lateTotals.percentage!!, 0.0000001)
    }

    @Test
    fun caseF_attendanceEndDate_excludesLaterHours() {
        val dates = weekdays("2026-07-01", 10)
        val days = dates.map { TeachingDayEntity("r1", it, 5, true, true) }
        val leaving = student(attendanceEndDate = dates[4])
        val marks = marksFor("s1", dates.take(5), 5, "P")
        val totals = AttendanceCalculator.totals(leaving, days, marks)

        assertEquals(25, totals.countedHours)
        assertEquals(100.0, totals.percentage!!, 0.0000001)
    }

    private fun totalsAtPercent(percent: Double): AttendanceTotals {
        val counted = 10_000
        val attended = (percent * 100).toInt()
        return AttendanceTotals(attended, 0, counted - attended, counted)
    }

    @Test
    fun caseG_preciseBoundaryPercentages_classifyWithoutDisplayRounding() {
        assertEquals(AttendanceCategory.REPEAT, EligibilityClassifier.assess(totalsAtPercent(49.99), register).category)
        assertEquals(AttendanceCategory.CONDONATION_MEDICAL, EligibilityClassifier.assess(totalsAtPercent(50.00), register).category)
        assertEquals(AttendanceCategory.CONDONATION_MEDICAL, EligibilityClassifier.assess(totalsAtPercent(64.99), register).category)
        assertEquals(AttendanceCategory.CONDONATION_FEE, EligibilityClassifier.assess(totalsAtPercent(65.00), register).category)
        assertEquals(AttendanceCategory.CONDONATION_FEE, EligibilityClassifier.assess(totalsAtPercent(74.99), register).category)
        assertEquals(AttendanceCategory.ELIGIBLE, EligibilityClassifier.assess(totalsAtPercent(75.00), register).category)
    }

    @Test
    fun caseH_verificationBandAbovePassMark_remainsEligibleWithSeparateFlag() {
        val assessment = EligibilityClassifier.assess(totalsAtPercent(75.50), register)
        assertEquals(AttendanceCategory.ELIGIBLE, assessment.category)
        assertTrue(assessment.nearThreshold)
    }

    @Test
    fun caseI_belowMinimumSample_returnsProvisionalStanding() {
        val strict = register.copy(minimumCountedHours = 50)
        val assessment = EligibilityClassifier.assess(
            AttendanceTotals(presentHours = 8, odHours = 0, absentHours = 2, countedHours = 10),
            strict,
        )
        assertEquals(AttendanceCategory.PROVISIONAL, assessment.category)
        assertTrue(assessment.label.contains("insufficient counted hours"))
    }
}

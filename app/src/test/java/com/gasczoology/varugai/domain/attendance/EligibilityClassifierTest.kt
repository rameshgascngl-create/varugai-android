package com.gasczoology.varugai.domain.attendance

import com.gasczoology.varugai.data.db.RegisterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EligibilityClassifierTest {
    private val register = RegisterEntity(
        id = "r1",
        passMark = 75.0,
        condonationFeeFloor = 65.0,
        condonationMedicalFloor = 50.0,
        verifyBandPoints = 1.0,
        minimumCountedHours = 1,
    )

    private fun totals(pct: Double): AttendanceTotals {
        val counted = 10_000
        val attended = (pct * counted / 100.0).toInt()
        return AttendanceTotals(attended, 0, counted - attended, counted)
    }

    @Test fun exactAttendanceBoundariesAreNotRoundedBeforeClassification() {
        assertEquals(AttendanceCategory.REPEAT, EligibilityClassifier.assess(totals(49.99), register).category)
        assertEquals(AttendanceCategory.CONDONATION_MEDICAL, EligibilityClassifier.assess(totals(50.00), register).category)
        assertEquals(AttendanceCategory.CONDONATION_MEDICAL, EligibilityClassifier.assess(totals(64.99), register).category)
        assertEquals(AttendanceCategory.CONDONATION_FEE, EligibilityClassifier.assess(totals(65.00), register).category)
        assertEquals(AttendanceCategory.CONDONATION_FEE, EligibilityClassifier.assess(totals(74.99), register).category)
        assertEquals(AttendanceCategory.ELIGIBLE, EligibilityClassifier.assess(totals(75.00), register).category)
    }

    @Test fun minimumHoursMakesStandingProvisionalWithoutChangingRawPercentage() {
        val t = AttendanceTotals(8, 0, 2, 10)
        val strict = register.copy(minimumCountedHours = 50)
        val a = EligibilityClassifier.assess(t, strict)
        assertEquals(AttendanceCategory.PROVISIONAL, a.category)
        assertEquals(80.0, a.percentage!!, 0.0001)
    }

    @Test fun verifyBandAnnotatesButDoesNotChangeCategory() {
        val near = EligibilityClassifier.assess(totals(74.50), register)
        assertEquals(AttendanceCategory.CONDONATION_FEE, near.category)
        assertTrue(near.nearThreshold)
        val far = EligibilityClassifier.assess(totals(70.00), register)
        assertFalse(far.nearThreshold)
    }

    @Test fun forecastUsesUnroundedHours() {
        val below = AttendanceTotals(70, 0, 30, 100)
        assertEquals(20, EligibilityClassifier.forecast(below, 75.0).hoursNeededToReachEligible)
        val above = AttendanceTotals(80, 0, 20, 100)
        assertEquals(6, EligibilityClassifier.forecast(above, 75.0).additionalAbsenceHoursAffordable)
    }
}

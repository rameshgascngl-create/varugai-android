package com.gasczoology.varugai.domain.attendance

import com.gasczoology.varugai.data.db.RegisterEntity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

enum class AttendanceCategory {
    ELIGIBLE,
    CONDONATION_FEE,
    CONDONATION_MEDICAL,
    REPEAT,
    PROVISIONAL,
}

data class EligibilityAssessment(
    val category: AttendanceCategory,
    val label: String,
    val percentage: Double?,
    val nearThreshold: Boolean,
)

data class AttendanceForecast(
    val hoursNeededToReachEligible: Int? = null,
    val additionalAbsenceHoursAffordable: Int? = null,
)

object EligibilityClassifier {
    fun assess(totals: AttendanceTotals, register: RegisterEntity): EligibilityAssessment {
        val pct = totals.percentage
        if (totals.countedHours < register.minimumCountedHours || pct == null) {
            return EligibilityAssessment(
                category = AttendanceCategory.PROVISIONAL,
                label = "Provisional — insufficient counted hours",
                percentage = pct,
                nearThreshold = pct?.let { isNearThreshold(it, register) } ?: false,
            )
        }

        val category = when {
            pct >= register.passMark -> AttendanceCategory.ELIGIBLE
            pct >= register.condonationFeeFloor -> AttendanceCategory.CONDONATION_FEE
            pct >= register.condonationMedicalFloor -> AttendanceCategory.CONDONATION_MEDICAL
            else -> AttendanceCategory.REPEAT
        }
        val label = when (category) {
            AttendanceCategory.ELIGIBLE -> "Eligible"
            AttendanceCategory.CONDONATION_FEE -> "Condonation — form + fee"
            AttendanceCategory.CONDONATION_MEDICAL -> "Condonation — form + fee + medical"
            AttendanceCategory.REPEAT -> "Repeat semester"
            AttendanceCategory.PROVISIONAL -> "Provisional"
        }
        return EligibilityAssessment(category, label, pct, isNearThreshold(pct, register))
    }

    fun forecast(totals: AttendanceTotals, eligibleThreshold: Double): AttendanceForecast {
        val counted = totals.countedHours
        val attended = totals.attendedHours
        if (counted <= 0 || eligibleThreshold <= 0.0 || eligibleThreshold >= 100.0) return AttendanceForecast()

        val ratio = eligibleThreshold / 100.0
        val pct = totals.percentage ?: return AttendanceForecast()
        return if (pct < eligibleThreshold) {
            val needed = ceil((ratio * counted - attended) / (1.0 - ratio)).toInt().coerceAtLeast(0)
            AttendanceForecast(hoursNeededToReachEligible = needed)
        } else {
            val affordable = floor(attended / ratio - counted).toInt().coerceAtLeast(0)
            AttendanceForecast(additionalAbsenceHoursAffordable = affordable)
        }
    }

    fun isNearThreshold(percentage: Double, register: RegisterEntity): Boolean {
        val band = register.verifyBandPoints.coerceAtLeast(0.0)
        return listOf(
            register.condonationMedicalFloor,
            register.condonationFeeFloor,
            register.passMark,
        ).any { abs(percentage - it) <= band }
    }
}

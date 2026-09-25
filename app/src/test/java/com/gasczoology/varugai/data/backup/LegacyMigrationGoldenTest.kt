package com.gasczoology.varugai.data.backup

import com.gasczoology.varugai.domain.attendance.AttendanceCalculator
import com.gasczoology.varugai.domain.attendance.EligibilityClassifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden compatibility gate for the architectural replacement.
 *
 * The fixture was generated from the real VARUGAI 15 schema-2 export contract:
 * JSON.stringify(data) + JavaScript-compatible FNV-1a checksum. It is synthetic
 * classroom data, not a recovered real register.
 */
class LegacyMigrationGoldenTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun resourceText(path: String): String {
        val url = requireNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing test resource: $path"
        }
        return url.openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun canonicalV15Schema2FixturePreservesRosterDenominatorsMarksPercentagesAndCategories() {
        val raw = resourceText("fixtures/VARUGAI_v15_CANONICAL_MIGRATION_FIXTURE.json")
        val expectedRoot = json.parseToJsonElement(
            resourceText("fixtures/VARUGAI_v15_EXPECTED_MIGRATION_RESULTS.json")
        ).jsonObject

        val imported = BackupCodec.decode(raw)
        val bundle = imported.bundle
        val source = expectedRoot.getValue("sourceContract").jsonObject

        assertEquals(2, imported.schema)
        assertEquals("VARUGAI 15.1.0 schema-2 backup", imported.sourceLabel)
        assertEquals(source.getValue("plannedWorkingDays").jsonPrimitive.content.toInt(), bundle.days.count { it.isWorking })
        assertEquals(
            source.getValue("completedWorkingDays").jsonPrimitive.content.toInt(),
            bundle.days.count { it.isWorking && it.isComplete }
        )
        assertEquals(10, bundle.students.size)

        val expectedBySid = expectedRoot.getValue("expectedStudents").jsonArray.associateBy {
            it.jsonObject.getValue("sid").jsonPrimitive.content
        }
        assertEquals(expectedBySid.keys, bundle.students.map { it.sid }.toSet())

        bundle.students.forEach { student ->
            val e = expectedBySid.getValue(student.sid).jsonObject
            val totals = AttendanceCalculator.totals(student, bundle.days, bundle.marks)
            val assessment = EligibilityClassifier.assess(totals, bundle.register)

            assertEquals(e.getValue("roll").jsonPrimitive.content, student.roll)
            assertEquals(e.getValue("name").jsonPrimitive.content, student.name)
            assertEquals(e.getValue("presentHoursExcludingOD").jsonPrimitive.content.toInt(), totals.presentHours)
            assertEquals(e.getValue("odHours").jsonPrimitive.content.toInt(), totals.odHours)
            assertEquals(e.getValue("attendedHoursIncludingOD").jsonPrimitive.content.toInt(), totals.attendedHours)
            assertEquals(e.getValue("absentHours").jsonPrimitive.content.toInt(), totals.absentHours)
            assertEquals(e.getValue("countedHours").jsonPrimitive.content.toInt(), totals.countedHours)
            assertEquals(
                e.getValue("percentage").jsonPrimitive.content.toDouble(),
                requireNotNull(totals.percentage),
                0.000000001
            )
            assertEquals(e.getValue("v16ExpectedCategory").jsonPrimitive.content, assessment.category.name)
        }

        // Explicit high-risk invariants from the v15 audit.
        val fullTerm = bundle.students.single { it.sid == "SQA001" }
        val fullTermTotals = AttendanceCalculator.totals(fullTerm, bundle.days, bundle.marks)
        assertEquals(100, fullTermTotals.countedHours)
        assertEquals(100.0, fullTermTotals.percentage!!, 0.000000001) // 90 planned, only 20 completed.

        val late = bundle.students.single { it.sid == "SQA008" }
        assertEquals(65, AttendanceCalculator.totals(late, bundle.days, bundle.marks).countedHours)

        val leftEarly = bundle.students.single { it.sid == "SQA009" }
        val leftEarlyAssessment = EligibilityClassifier.assess(
            AttendanceCalculator.totals(leftEarly, bundle.days, bundle.marks),
            bundle.register
        )
        assertEquals(35, AttendanceCalculator.totals(leftEarly, bundle.days, bundle.marks).countedHours)
        assertEquals("PROVISIONAL", leftEarlyAssessment.category.name)

        val allOd = bundle.students.single { it.sid == "SQA010" }
        val allOdTotals = AttendanceCalculator.totals(allOd, bundle.days, bundle.marks)
        assertEquals(100, allOdTotals.odHours)
        assertEquals(100, allOdTotals.attendedHours)
        assertTrue(allOdTotals.absentHours == 0)
    }
}

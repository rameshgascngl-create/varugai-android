package com.gasczoology.varugai.data.backup

import com.gasczoology.varugai.data.db.AttendanceMarkEntity
import com.gasczoology.varugai.data.db.AuditEventEntity
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import com.gasczoology.varugai.legacy.LegacyChecksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun nativeSchema3RoundTripPreservesAttendanceAndUsesSha256() {
        val bundle = RegisterBundle(
            register = RegisterEntity(id = "r1", courseCode = "ZU1", courseTitle = "Zoology", className = "I B.Sc.", startDate = "2026-07-01", endDate = "2026-10-31"),
            students = listOf(StudentEntity("r1", "s1", "1", "24Z001", "Arun", "", "", 0)),
            days = listOf(TeachingDayEntity("r1", "2026-07-01", 2, true, true)),
            marks = listOf(
                AttendanceMarkEntity("r1", "2026-07-01", "s1", 1, "P"),
                AttendanceMarkEntity("r1", "2026-07-01", "s1", 2, "O"),
            ),
            audits = listOf(AuditEventEntity(registerId = "r1", timestamp = 1L, kind = "attendance", date = "2026-07-01", sid = "s1", message = "test")),
        )

        val encoded = BackupCodec.createNative(bundle)
        assertTrue(encoded.contains("\"schema\": 3"))
        assertTrue(encoded.contains("\"checksumType\": \"sha256\""))

        val decoded = BackupCodec.decode(encoded)
        assertEquals(3, decoded.schema)
        assertEquals(1, decoded.bundle.students.size)
        assertEquals("Arun", decoded.bundle.students.single().name)
        assertEquals(listOf("P", "O"), decoded.bundle.marks.sortedBy { it.hourIndex }.map { it.status })
        assertEquals(true, decoded.bundle.days.single().isComplete)
    }

    @Test
    fun nativeTamperIsRejectedBeforeRestore() {
        val bundle = RegisterBundle(
            RegisterEntity(id = "r1"),
            listOf(StudentEntity("r1", "s1", "1", "", "Arun", "", "", 0)),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        val encoded = BackupCodec.createNative(bundle)
        val tampered = encoded.replace("Arun", "Banu")
        assertTrue(runCatching { BackupCodec.decode(tampered) }.isFailure)
    }

    @Test
    fun nativeBackupRejectsDuplicateRegisterNumbers() {
        val bundle = RegisterBundle(
            RegisterEntity(id = "r1"),
            listOf(
                StudentEntity("r1", "s1", "1", "24Z001", "Arun", "", "", 0),
                StudentEntity("r1", "s2", "2", "24Z001", "Banu", "", "", 1),
            ),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        val encoded = BackupCodec.createNative(bundle)
        val result = runCatching { BackupCodec.decode(encoded) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("duplicate register"))
    }

    @Test
    fun legacySchema2WithStale1510AppVersionImportsAfterFnvVerification() {
        val data = """{"meta":{"defHours":2,"passMark":75,"condFee":65,"condMed":50,"bandPts":3,"minHours":1,"courseCode":"ZU1","className":"I B.Sc.","startDate":"2026-07-01","endDate":"2026-07-31"},"days":{"2026-07-01":{"hours":2,"on":true,"done":true,"note":""}},"roster":[{"sid":"s1","roll":"1","regno":"24Z001","name":"ரமேஷ்","from":"","to":""}],"marks":{"2026-07-01":{"s1":"PO"}},"audit":[],"schema":2}"""
        val checksum = LegacyChecksum.fnv1a32(data)
        val envelope = """{"format":"varugai-backup","schema":2,"appVersion":"15.1.0","created":"2026-09-01T00:00:00Z","register":"ZU1","students":1,"checksum":"$checksum","data":$data}"""

        val decoded = BackupCodec.decode(envelope)
        assertEquals(2, decoded.schema)
        assertEquals("ரமேஷ்", decoded.bundle.students.single().name)
        assertEquals(2, decoded.bundle.days.single().hours)
        assertEquals(listOf("P", "O"), decoded.bundle.marks.sortedBy { it.hourIndex }.map { it.status })
    }

    @Test
    fun legacyChecksumTamperIsRejected() {
        val data = """{"meta":{},"days":{},"roster":[],"marks":{},"audit":[],"schema":2}"""
        val checksum = LegacyChecksum.fnv1a32(data)
        val envelope = """{"format":"varugai-backup","schema":2,"appVersion":"15.1.0","checksum":"$checksum","data":$data}"""
        val tampered = envelope.replace("\"schema\":2}", "\"schema\":1}")
        assertTrue(runCatching { BackupCodec.decode(tampered) }.isFailure)
    }
}

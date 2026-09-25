package com.gasczoology.varugai.data.backup

import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.StudentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseBlockingBackupCaseJTest {
    @Test
    fun caseJ_validBackupReachesWriteGate_butOneByteTamperWritesNothing() {
        val bundle = RegisterBundle(
            register = RegisterEntity(id = "r1"),
            students = listOf(StudentEntity("r1", "s1", "1", "", "Arun", "", "", 0)),
            days = emptyList(),
            marks = emptyList(),
            audits = emptyList(),
        )
        val valid = BackupCodec.createNative(bundle)

        var validWrites = 0
        val decoded = BackupCodec.decode(valid)
        validWrites++
        assertEquals("Arun", decoded.bundle.students.single().name)
        assertEquals(1, validWrites)

        val tampered = valid.replace("Arun", "Brun")
        var tamperedWrites = 0
        val attempt = runCatching {
            BackupCodec.decode(tampered)
            tamperedWrites++
        }

        assertTrue(attempt.isFailure)
        assertEquals(0, tamperedWrites)
    }
}

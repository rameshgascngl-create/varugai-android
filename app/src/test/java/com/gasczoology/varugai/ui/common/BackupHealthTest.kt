package com.gasczoology.varugai.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupHealthTest {
    private val day = 86_400_000L
    private val now = 20L * day

    @Test fun noBackupIsErrorAndStatesSingleCopyRisk() {
        val h = backupHealth(null, now)
        assertEquals(BackupRisk.ERROR, h.risk)
        assertEquals("Last backup: Never", h.label)
        assertTrue(h.message.contains("only one place"))
    }

    @Test fun sevenDaysIsCurrent_butEightDaysIsWarning() {
        assertEquals(BackupRisk.CURRENT, backupHealth(now - 7L * day, now).risk)
        assertEquals(BackupRisk.WARNING, backupHealth(now - 8L * day, now).risk)
        assertEquals("Last backup: 8 days ago", backupHealth(now - 8L * day, now).label)
    }

    @Test fun fourteenDaysIsWarning_butFifteenDaysIsError() {
        assertEquals(BackupRisk.WARNING, backupHealth(now - 14L * day, now).risk)
        assertEquals(BackupRisk.ERROR, backupHealth(now - 15L * day, now).risk)
    }
}

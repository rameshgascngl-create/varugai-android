package com.gasczoology.varugai.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterFileParserTest {
    @Test
    fun quotedCsvAndTamilNamesArePreserved() {
        val rows = RosterFileParser.parseDelimited("Roll,Reg,Name\n1,24Z001,\"ராஜ், குமார்\"\n2,24Z002,Anitha")
        assertEquals(3, rows.size)
        assertEquals("ராஜ், குமார்", rows[1][2])
    }

    @Test
    fun pastedRosterRejectsDuplicateRolls() {
        val result = runCatching {
            RosterFileParser.parsePastedRoster("1, Arun\n1, Banu", false)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Duplicate roll"))
    }

    @Test
    fun pastedRosterRejectsDuplicateRegisterNumbers() {
        val result = runCatching {
            RosterFileParser.parsePastedRoster("1,24Z001,Arun\n2,24z001,Banu", true)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Duplicate register"))
    }

    @Test
    fun columnGuessRecognisesCommonHeaders() {
        val rows = listOf(
            listOf("S.No", "Register No", "Student Name"),
            listOf("1", "24Z001", "Arun"),
            listOf("2", "24Z002", "Banu"),
        )
        val guess = RosterFileParser.guessColumns(rows, hasHeader = true)
        assertEquals(0, guess.roll)
        assertEquals(1, guess.registerNumber)
        assertEquals(2, guess.name)
    }

    @Test
    fun nameOnlyPasteGeneratesSequentialRolls() {
        val rows = RosterFileParser.parsePastedRoster("Arun\nBanu\nChitra", false)
        assertEquals(listOf("1", "2", "3"), rows.map { it.roll })
    }
}

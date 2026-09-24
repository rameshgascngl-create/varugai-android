package com.gasczoology.varugai.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormattingTest {
    @Test
    fun isoDateDisplaysAsDayMonthYear() {
        assertEquals("01/07/2026", displayDate("2026-07-01"))
    }

    @Test
    fun isoDateDisplaysShortDayMonth() {
        assertEquals("01/07", displayShortDate("2026-07-01"))
    }

    @Test
    fun invalidInputFallsBackWithoutCrashing() {
        assertEquals("not-a-date", displayDate("not-a-date"))
    }

    @Test
    fun weekdayIsHumanReadable() {
        assertEquals("Wednesday", displayWeekday("2026-07-01"))
    }
}

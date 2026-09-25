package com.gasczoology.varugai.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class PinRateLimitTest {
    @Test fun firstFourFailuresHaveNoDelay() {
        for (attempts in 1..4) assertEquals(0L, PinRateLimit.lockoutMillis(attempts))
    }

    @Test fun fifthFailureStartsThirtySecondDelay() {
        assertEquals(30_000L, PinRateLimit.lockoutMillis(5))
    }

    @Test fun delayIncreasesAndCapsAtFiveMinutes() {
        assertEquals(60_000L, PinRateLimit.lockoutMillis(6))
        assertEquals(120_000L, PinRateLimit.lockoutMillis(7))
        assertEquals(240_000L, PinRateLimit.lockoutMillis(8))
        assertEquals(300_000L, PinRateLimit.lockoutMillis(9))
        assertEquals(300_000L, PinRateLimit.lockoutMillis(20))
    }
}

package com.gasczoology.varugai.legacy

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyChecksumTest {
    @Test fun emptyStringMatchesJavaScriptFNV1a() {
        assertEquals("811c9dc5", LegacyChecksum.fnv1a32(""))
    }

    @Test fun asciiMatchesJavaScriptFNV1a() {
        assertEquals("4f9f2cab", LegacyChecksum.fnv1a32("hello"))
    }

    @Test fun tamilJsonMatchesJavaScriptUtf16CodeUnits() {
        assertEquals("8b8e6736", LegacyChecksum.fnv1a32("{\"schema\":2,\"name\":\"ரமேஷ்\"}"))
    }

    @Test fun surrogatePairMatchesJavaScriptCharCodeAtIteration() {
        assertEquals("cb31c4b8", LegacyChecksum.fnv1a32("😀"))
    }
}

package pl.usagi.edziennik.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringExtensionsTest {
    @Test fun emptyToNullHandlesNullAndEmpty() {
        assertNull((null as String?).emptyToNull())
        assertNull("".emptyToNull())
    }

    @Test fun emptyToNullPreservesWhitespace() {
        assertEquals(" \t\n", " \t\n".emptyToNull())
    }

    @Test fun emptyToNullPreservesTextWithoutTrimming() {
        assertEquals(" text ", " text ".emptyToNull())
    }

    @Test fun blankToNullHandlesNullAndEmpty() {
        assertNull((null as String?).blankToNull())
        assertNull("".blankToNull())
    }

    @Test fun blankToNullHandlesWhitespace() {
        assertNull(" \t\n".blankToNull())
        assertNull("\u00a0".blankToNull())
    }

    @Test fun blankToNullPreservesTextWithoutTrimming() {
        assertEquals(" text ", " text ".blankToNull())
    }
}

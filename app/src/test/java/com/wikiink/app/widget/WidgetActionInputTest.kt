package com.wikiink.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The trampoline is exported, so its extras are untrusted. */
class WidgetActionInputTest {

    @Test
    fun realTitlesPass() {
        assertEquals("Voyager 1", WidgetActionActivity.sanitizeTitle("Voyager 1"))
        assertEquals("Café Müller", WidgetActionActivity.sanitizeTitle("  Café Müller "))
    }

    @Test
    fun junkIsRejected() {
        assertNull(WidgetActionActivity.sanitizeTitle(""))
        assertNull(WidgetActionActivity.sanitizeTitle("   "))
        assertNull(WidgetActionActivity.sanitizeTitle("x".repeat(256)))
        assertNull(WidgetActionActivity.sanitizeTitle("Bad\u0000title"))
        assertNull(WidgetActionActivity.sanitizeTitle("Line\nbreak"))
    }
}

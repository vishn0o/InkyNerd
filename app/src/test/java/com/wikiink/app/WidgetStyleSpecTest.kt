package com.wikiink.app

import com.wikiink.app.data.prefs.WidgetPreferences
import com.wikiink.app.data.prefs.WidgetStyle
import com.wikiink.app.data.prefs.WidgetThemeMode
import com.wikiink.app.widget.WidgetStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStyleSpecTest {

    @Test
    fun everyStyleHasDistinctLayoutAndLightDarkVariants() {
        val layouts = WidgetStyle.entries.map { WidgetStyles.specFor(it, false).itemLayout }
        assertEquals(WidgetStyle.entries.size, layouts.toSet().size)
        WidgetStyle.entries.forEach { style ->
            val light = WidgetStyles.specFor(style, false)
            val dark = WidgetStyles.specFor(style, true)
            assertEquals(style, light.style)
            assertEquals(light.itemLayout, dark.itemLayout)
            assertNotEquals("$style card", light.cardBackground, dark.cardBackground)
            assertNotEquals("$style pill", light.pillBackground, dark.pillBackground)
            assertNotEquals("$style hook ink", light.hookInk, dark.hookInk)
            assertNotEquals("$style title ink", light.titleInk, dark.titleInk)
            assertFalse(light.dark)
            assertTrue(dark.dark)
        }
    }

    @Test
    fun cornerRadiiMatchSpec() {
        assertEquals(4f, WidgetStyles.specFor(WidgetStyle.CLASSIC_EINK, false).cornerDp)
        assertEquals(28f, WidgetStyles.specFor(WidgetStyle.SWISS_ALABASTER, false).cornerDp)
        assertEquals(26f, WidgetStyles.specFor(WidgetStyle.FROSTED_GLASS, false).cornerDp)
        assertEquals(24f, WidgetStyles.specFor(WidgetStyle.DOT_MATRIX, false).cornerDp)
        assertEquals(28f, WidgetStyles.specFor(WidgetStyle.ALMANAC, false).cornerDp)
    }

    @Test
    fun themeModeResolution() {
        assertTrue(WidgetPreferences(themeMode = WidgetThemeMode.DARK).isDark(systemIsNight = false))
        assertFalse(WidgetPreferences(themeMode = WidgetThemeMode.LIGHT).isDark(systemIsNight = true))
        assertTrue(WidgetPreferences(themeMode = WidgetThemeMode.SYSTEM).isDark(systemIsNight = true))
        assertFalse(WidgetPreferences(themeMode = WidgetThemeMode.SYSTEM).isDark(systemIsNight = false))
    }

    @Test
    fun defaultsAndUnknownValuesFallBack() {
        assertEquals(WidgetStyle.SWISS_ALABASTER, WidgetPreferences().style)
        assertEquals(WidgetThemeMode.SYSTEM, WidgetPreferences().themeMode)
        assertEquals(WidgetStyle.SWISS_ALABASTER, WidgetStyle.parse("NEON_VAPORWAVE"))
        assertEquals(WidgetStyle.SWISS_ALABASTER, WidgetStyle.parse(null))
        assertEquals(WidgetStyle.FROSTED_GLASS, WidgetStyle.parse("FROSTED_GLASS"))
        assertEquals(WidgetThemeMode.SYSTEM, WidgetThemeMode.parse("bogus"))
    }
}

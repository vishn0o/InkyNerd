package com.wikiink.app

import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.data.prefs.WidgetStyle
import com.wikiink.app.widget.WidgetTypeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTypeScaleTest {

    private fun fit(len: Int, w: Int, h: Int, layout: WidgetLayout = WidgetLayout.SINGLE, style: WidgetStyle = WidgetStyle.SWISS_ALABASTER, scale: Float = 1f) =
        WidgetTypeScale.fit(len, w, h, layout, style, scale)

    @Test
    fun biggerWidgetGetsBiggerText() {
        assertTrue(fit(150, 330, 290).hookSp > fit(150, 330, 190).hookSp)
    }

    @Test
    fun shorterHooksReadLargerOnTheSameWidget() {
        assertTrue(fit(60, 330, 290).hookSp > fit(180, 330, 290).hookSp)
    }

    @Test
    fun fittedTextFitsTheTextArea() {
        // 330×290 single: text area ≈ 298 × 198 dp. Lines × line height must not exceed it.
        val t = fit(180, 330, 290)
        val perLine = (298f / (t.hookSp * 0.53f)).toInt()
        val lines = kotlin.math.ceil(180 * 1.12 / perLine).toInt()
        assertTrue(lines * t.hookSp * 1.36f <= 198f)
        assertTrue(t.hookMaxLines >= lines)
    }

    @Test
    fun boundsAndUnknownSizes() {
        assertEquals(WidgetTypeScale.MIN_HOOK_SP, fit(400, 120, 80).hookSp)
        assertEquals(WidgetTypeScale.MAX_HOOK_SP, fit(10, 2000, 2000).hookSp)
        assertEquals(fit(140, 0, 0), fit(140, WidgetTypeScale.DEFAULT_WIDTH_DP, WidgetTypeScale.DEFAULT_HEIGHT_DP))
        assertTrue(fit(400, 120, 80).hookMaxLines >= 2)
    }

    @Test
    fun largeSystemFontShrinksTheFittedSize() {
        assertTrue(fit(150, 330, 290, scale = 1.3f).hookSp < fit(150, 330, 290).hookSp)
    }

    @Test
    fun stackLeavesRoomForPeekingCards() {
        assertTrue(fit(150, 330, 290, WidgetLayout.STACK).hookSp <= fit(150, 330, 290).hookSp)
    }

    @Test
    fun everyStyleGetsComparableSizes() {
        val sizes = WidgetStyle.entries.map { fit(150, 330, 290, style = it).hookSp }
        assertTrue("styles differ too much: $sizes", sizes.max() - sizes.min() <= 2.5f)
    }
}

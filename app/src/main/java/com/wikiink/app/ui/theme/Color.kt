package com.wikiink.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// E-Ink Paper (light)
val PaperBackground = Color(0xFFF4F1EA)
val PaperSurface = Color(0xFFEBE7DE)
val PaperInk = Color(0xFF141414)
val PaperInkSecondary = Color(0xFF4A4A4A)
val PaperBorder = Color(0xFF141414)

// E-Ink Night (dark)
val NightBackground = Color(0xFF121212)
val NightSurface = Color(0xFF1C1C1C)
val NightInk = Color(0xFFE6E3DC)
val NightInkSecondary = Color(0xFF9E9B95)
val NightBorder = Color(0xFFE6E3DC)

// AMOLED (true black): pixels off on OLED screens, softer ink and borders so it doesn't glare.
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0B0B0B)
val AmoledInk = Color(0xFFE2DFD8)
val AmoledInkSecondary = Color(0xFF8E8B85)
val AmoledBorder = Color(0xFF8E8B85)

val HairlineWidth: Dp = 1.5.dp

@Immutable
data class InkColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val inkSecondary: Color,
    val border: Color,
    val isDark: Boolean,
)

val PaperColors = InkColors(PaperBackground, PaperSurface, PaperInk, PaperInkSecondary, PaperBorder, isDark = false)
val NightColors = InkColors(NightBackground, NightSurface, NightInk, NightInkSecondary, NightBorder, isDark = true)
val AmoledColors = InkColors(AmoledBackground, AmoledSurface, AmoledInk, AmoledInkSecondary, AmoledBorder, isDark = true)

/** App themes, cycled by the reader's theme button in this order. */
enum class AppTheme(val label: String, val colors: InkColors) {
    PAPER("Paper", PaperColors),
    NIGHT("Night", NightColors),
    AMOLED("AMOLED", AmoledColors),
    ;

    fun next(): AppTheme = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): AppTheme? = entries.firstOrNull { it.name == name }
    }
}

val LocalInkColors = staticCompositionLocalOf { PaperColors }

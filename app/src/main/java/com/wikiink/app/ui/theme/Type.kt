package com.wikiink.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.wikiink.app.R
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Bundled reading typefaces (SIL OFL — see assets/licenses). Each is chosen for E-Ink-style reading:
 * open counters, sturdy serifs/strokes and no hairline details that break up on low-contrast panels.
 */
enum class ReaderTypeface(val label: String, val blurb: String, val family: FontFamily) {
    /** The original InkyNerd face (system serif) — stays the default. */
    CLASSIC("Classic", "The original InkyNerd serif.", FontFamily.Serif),
    LITERATA(
        "Literata", "Designed for e-readers. Calm and sturdy.",
        FontFamily(
            Font(R.font.literata_regular, FontWeight.Normal),
            Font(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
            Font(R.font.literata_semibold, FontWeight.SemiBold),
            Font(R.font.literata_bold, FontWeight.Bold),
        ),
    ),
    GARAMOND(
        "EB Garamond", "Old-book Garamond. Feels like a library edition.",
        FontFamily(
            Font(R.font.eb_garamond_regular, FontWeight.Normal),
            Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
            Font(R.font.eb_garamond_bold, FontWeight.SemiBold),
            Font(R.font.eb_garamond_bold, FontWeight.Bold),
        ),
    ),
    PLEX_SERIF(
        "IBM Plex Serif", "Crisp and technical. Lab-notebook energy.",
        FontFamily(
            Font(R.font.ibm_plex_serif_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_serif_italic, FontWeight.Normal, FontStyle.Italic),
            Font(R.font.ibm_plex_serif_semibold, FontWeight.SemiBold),
            Font(R.font.ibm_plex_serif_semibold, FontWeight.Bold),
        ),
    ),
    HYPERLEGIBLE(
        "Atkinson Hyperlegible", "Built for low vision. Maximum clarity at any size.",
        FontFamily(
            Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
            Font(R.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
            Font(R.font.atkinson_hyperlegible_bold, FontWeight.SemiBold),
            Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
        ),
    ),
    PLEX_MONO(
        "IBM Plex Mono", "Typewriter-style mono. Terminal-nerd mode.",
        FontFamily(
            Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
            Font(R.font.ibm_plex_mono_medium, FontWeight.SemiBold),
            Font(R.font.ibm_plex_mono_medium, FontWeight.Bold),
        ),
    ),
    ;

    companion object {
        val DEFAULT = CLASSIC
        fun parse(name: String?): ReaderTypeface = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** App-wide active reading typeface. Snapshot state, so every composable using [SerifFamily] recomposes on change. */
object ActiveTypeface {
    var current by mutableStateOf(ReaderTypeface.DEFAULT)
}

val SerifFamily: FontFamily get() = ActiveTypeface.current.family

/** Tags, buttons and labels keep the original system monospace. */
val MonoFamily = FontFamily.Monospace

/** Reader body scale selected via the S / M / L toggle. */
enum class ReaderFontSize(val label: String, val bodySp: Float, val titleSp: Float) {
    S("S", 15f, 26f),
    M("M", 18f, 30f),
    L("L", 21f, 34f),
}

fun inkTypography() = Typography(
    displaySmall = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = SerifFamily, fontSize = 18.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = SerifFamily, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = SerifFamily, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

fun monoTag(size: TextUnit = 11.sp) = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    letterSpacing = 1.4.sp,
)

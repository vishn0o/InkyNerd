package com.wikiink.app.ui.theme

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistent reader preferences. Exposed as StateFlows so switches are instant (no recreate). */
class ReaderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        AppTheme.parse(prefs.getString(KEY_THEME, null))
            // Older installs stored only a light/dark flag.
            ?: if (prefs.contains(KEY_DARK)) (if (prefs.getBoolean(KEY_DARK, false)) AppTheme.NIGHT else AppTheme.PAPER) else null,
    )
    /** null = follow the system (Paper or Night) until the user picks one. */
    val theme: StateFlow<AppTheme?> = _theme.asStateFlow()

    private val _fontSize = MutableStateFlow(
        runCatching { ReaderFontSize.valueOf(prefs.getString(KEY_FONT, null) ?: "M") }.getOrDefault(ReaderFontSize.M),
    )
    val fontSize: StateFlow<ReaderFontSize> = _fontSize.asStateFlow()

    private val _flash = MutableStateFlow(prefs.getBoolean(KEY_FLASH, false))
    val refreshFlash: StateFlow<Boolean> = _flash.asStateFlow()

    private val _typeface = MutableStateFlow(ReaderTypeface.parse(prefs.getString(KEY_TYPEFACE, null)))
    val typeface: StateFlow<ReaderTypeface> = _typeface.asStateFlow()

    init {
        ActiveTypeface.current = _typeface.value
    }

    fun setTypeface(value: ReaderTypeface) {
        _typeface.value = value
        ActiveTypeface.current = value
        prefs.edit { putString(KEY_TYPEFACE, value.name) }
    }

    fun setTheme(value: AppTheme) {
        _theme.value = value
        prefs.edit { putString(KEY_THEME, value.name) }
    }

    fun setFontSize(value: ReaderFontSize) {
        _fontSize.value = value
        prefs.edit { putString(KEY_FONT, value.name) }
    }

    fun setRefreshFlash(value: Boolean) {
        _flash.value = value
        prefs.edit { putBoolean(KEY_FLASH, value) }
    }

    private companion object {
        const val KEY_DARK = "dark"
        const val KEY_THEME = "theme"
        const val KEY_FONT = "font"
        const val KEY_FLASH = "flash"
        const val KEY_TYPEFACE = "typeface"
    }
}

/** Indication that draws nothing — E-Ink panels don't ripple. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

private val InkShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

private fun schemeFor(c: InkColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.ink, onPrimary = c.background,
        secondary = c.inkSecondary, onSecondary = c.background,
        tertiary = c.ink, onTertiary = c.background,
        background = c.background, onBackground = c.ink,
        surface = c.background, onSurface = c.ink,
        surfaceVariant = c.surface, onSurfaceVariant = c.inkSecondary,
        surfaceTint = c.background,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
        surfaceBright = c.surface, surfaceDim = c.surface,
        primaryContainer = c.surface, onPrimaryContainer = c.ink,
        secondaryContainer = c.surface, onSecondaryContainer = c.ink,
        outline = c.border, outlineVariant = c.inkSecondary,
        error = c.ink, onError = c.background,
        inverseSurface = c.ink, inverseOnSurface = c.background, inversePrimary = c.background,
        scrim = c.background,
    )
} else {
    lightColorScheme(
        primary = c.ink, onPrimary = c.background,
        secondary = c.inkSecondary, onSecondary = c.background,
        tertiary = c.ink, onTertiary = c.background,
        background = c.background, onBackground = c.ink,
        surface = c.background, onSurface = c.ink,
        surfaceVariant = c.surface, onSurfaceVariant = c.inkSecondary,
        surfaceTint = c.background,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
        surfaceBright = c.surface, surfaceDim = c.surface,
        primaryContainer = c.surface, onPrimaryContainer = c.ink,
        secondaryContainer = c.surface, onSecondaryContainer = c.ink,
        outline = c.border, outlineVariant = c.inkSecondary,
        error = c.ink, onError = c.background,
        inverseSurface = c.ink, inverseOnSurface = c.background, inversePrimary = c.background,
        scrim = c.background,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkyTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val colors = theme.colors
    MaterialTheme(colorScheme = schemeFor(colors), typography = inkTypography(), shapes = InkShapes) {
        CompositionLocalProvider(
            LocalInkColors provides colors,
            LocalIndication provides NoIndication,
            LocalRippleConfiguration provides null,
            LocalOverscrollFactory provides null,
            content = content,
        )
    }
}

object Ink {
    val colors: InkColors
        @Composable get() = LocalInkColors.current
}

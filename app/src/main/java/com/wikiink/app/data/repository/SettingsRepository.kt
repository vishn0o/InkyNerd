package com.wikiink.app.data.repository

import android.content.Context
import com.wikiink.app.ui.theme.EInkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared preference keys — also read directly by the home-screen widget. */
object WikiInkPrefs {
    const val NAME = "wikiink_settings"
    const val KEY_THEME = "theme"
    const val KEY_FONT_SCALE = "font_scale"
    const val KEY_REFRESH_FLASH = "refresh_flash"
    const val KEY_FIRST_SYNC_DONE = "first_sync_done"
}

/** Reader display preferences, persisted so the widget and app stay in sync. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        WikiInkPrefs.NAME,
        Context.MODE_PRIVATE
    )

    private val storedTheme = prefs.getString(WikiInkPrefs.KEY_THEME, EInkTheme.LIGHT.name)
    private val _theme = MutableStateFlow(
        if (storedTheme == EInkTheme.DARK.name) EInkTheme.DARK else EInkTheme.LIGHT
    )
    val theme: StateFlow<EInkTheme> = _theme.asStateFlow()

    private val _fontScale = MutableStateFlow(
        prefs.getString(WikiInkPrefs.KEY_FONT_SCALE, "M") ?: "M"
    )
    val fontScale: StateFlow<String> = _fontScale.asStateFlow()

    private val _refreshFlash = MutableStateFlow(
        prefs.getBoolean(WikiInkPrefs.KEY_REFRESH_FLASH, false)
    )
    val refreshFlash: StateFlow<Boolean> = _refreshFlash.asStateFlow()

    fun toggleTheme() {
        val next = if (_theme.value == EInkTheme.LIGHT) EInkTheme.DARK else EInkTheme.LIGHT
        prefs.edit().putString(WikiInkPrefs.KEY_THEME, next.name).apply()
        _theme.value = next
    }

    fun setFontScale(scale: String) {
        if (scale !in setOf("S", "M", "L")) return
        prefs.edit().putString(WikiInkPrefs.KEY_FONT_SCALE, scale).apply()
        _fontScale.value = scale
    }

    fun setRefreshFlash(enabled: Boolean) {
        prefs.edit().putBoolean(WikiInkPrefs.KEY_REFRESH_FLASH, enabled).apply()
        _refreshFlash.value = enabled
    }

    /** Returns true exactly once, so the immediate sync runs only on first launch. */
    fun consumeFirstLaunch(): Boolean {
        if (prefs.getBoolean(WikiInkPrefs.KEY_FIRST_SYNC_DONE, false)) return false
        prefs.edit().putBoolean(WikiInkPrefs.KEY_FIRST_SYNC_DONE, true).apply()
        return true
    }
}

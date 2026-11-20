package com.wikiink.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wikiink.app.widget.WikiWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

enum class WidgetStyle { CLASSIC_EINK, SWISS_ALABASTER, FROSTED_GLASS, DOT_MATRIX, ALMANAC;
    companion object {
        val DEFAULT = SWISS_ALABASTER
        fun parse(name: String?): WidgetStyle = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

enum class WidgetThemeMode { SYSTEM, LIGHT, DARK;
    companion object {
        val DEFAULT = SYSTEM
        fun parse(name: String?): WidgetThemeMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** How cards are presented: a swipeable deck, or one full card at a time with a "next" control. */
enum class WidgetLayout { STACK, SINGLE;
    companion object {
        val DEFAULT = STACK
        fun parse(name: String?): WidgetLayout = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

data class WidgetPreferences(
    val style: WidgetStyle = WidgetStyle.DEFAULT,
    val themeMode: WidgetThemeMode = WidgetThemeMode.DEFAULT,
    val layout: WidgetLayout = WidgetLayout.DEFAULT,
) {
    /** Resolves light/dark; [systemIsNight] is consulted only for [WidgetThemeMode.SYSTEM]. */
    fun isDark(systemIsNight: Boolean): Boolean = when (themeMode) {
        WidgetThemeMode.DARK -> true
        WidgetThemeMode.LIGHT -> false
        WidgetThemeMode.SYSTEM -> systemIsNight
    }
}

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_prefs")

/** Widget appearance settings (Preferences DataStore). Every write refreshes live home-screen widgets. */
class WidgetPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.widgetDataStore

    val preferencesFlow: Flow<WidgetPreferences> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            WidgetPreferences(
                WidgetStyle.parse(p[KEY_STYLE]),
                WidgetThemeMode.parse(p[KEY_MODE]),
                WidgetLayout.parse(p[KEY_LAYOUT]),
            )
        }
        .distinctUntilChanged()

    /**
     * Blocking snapshot for RemoteViews code (`RemoteViewsFactory` runs on a binder thread).
     * Never call from the main thread.
     */
    fun currentBlocking(): WidgetPreferences = runBlocking { current() }

    /** Suspending snapshot for coroutine callers; prefer this over [currentBlocking]. */
    suspend fun current(): WidgetPreferences = preferencesFlow.first()

    suspend fun setStyle(style: WidgetStyle) {
        store.edit { it[KEY_STYLE] = style.name }
        WikiWidgetProvider.rebuildAll(appContext)
    }

    suspend fun setThemeMode(mode: WidgetThemeMode) {
        store.edit { it[KEY_MODE] = mode.name }
        WikiWidgetProvider.rebuildAll(appContext)
    }

    suspend fun setLayout(layout: WidgetLayout) {
        store.edit { it[KEY_LAYOUT] = layout.name }
        WikiWidgetProvider.rebuildAll(appContext)
    }

    private companion object {
        val KEY_LAYOUT = stringPreferencesKey("widgetLayout")
        val KEY_STYLE = stringPreferencesKey("widgetStyle")
        val KEY_MODE = stringPreferencesKey("widgetThemeMode")
    }
}

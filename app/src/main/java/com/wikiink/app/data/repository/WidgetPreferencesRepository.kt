package com.wikiink.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wikiink.app.widget.WikiWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

enum class WidgetStyle { CLASSIC_EINK, SWISS_ALABASTER, FROSTED_GLASS }

enum class WidgetThemeMode { SYSTEM, LIGHT, DARK }

private val Context.widgetPreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "widget_preferences"
)

/** Persisted widget appearance, shared by the UI, the provider and the factory. */
class WidgetPreferencesRepository(context: Context) {

    private val dataStoreContext: Context = context.applicationContext
    private val dataStore = dataStoreContext.widgetPreferencesStore

    data class WidgetPreferences(
        val style: WidgetStyle = WidgetStyle.SWISS_ALABASTER,
        val themeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM
    )

    val preferencesFlow: Flow<WidgetPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            WidgetPreferences(
                style = runCatching { WidgetStyle.valueOf(prefs[KEY_STYLE] ?: "") }
                    .getOrDefault(WidgetStyle.SWISS_ALABASTER),
                themeMode = runCatching { WidgetThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: "") }
                    .getOrDefault(WidgetThemeMode.SYSTEM)
            )
        }

    suspend fun setWidgetStyle(style: WidgetStyle) {
        dataStore.edit { it[KEY_STYLE] = style.name }
        // Push the change to every active widget immediately.
        WikiWidgetProvider.refreshAll(dataStoreContext)
    }

    suspend fun setWidgetThemeMode(mode: WidgetThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
        WikiWidgetProvider.refreshAll(dataStoreContext)
    }

    /**
     * Synchronous snapshot for RemoteViewsFactory.getViewAt(), which cannot
     * itself be a suspend function.
     */
    fun snapshot(): WidgetPreferences = runBlocking { preferencesFlow.first() }

    companion object {
        private val KEY_STYLE = stringPreferencesKey("widget_style")
        private val KEY_THEME_MODE = stringPreferencesKey("widget_theme_mode")
    }
}

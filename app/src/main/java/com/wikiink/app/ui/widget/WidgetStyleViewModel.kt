package com.wikiink.app.ui.widget

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wikiink.app.data.repository.WidgetPreferencesRepository
import com.wikiink.app.data.repository.WidgetStyle
import com.wikiink.app.data.repository.WidgetThemeMode
import com.wikiink.app.widget.WikiWidgetProvider
import com.wikiink.app.worker.FactSyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WidgetStyleViewModel(private val repository: WidgetPreferencesRepository) : ViewModel() {

    val preferences: StateFlow<WidgetPreferencesRepository.WidgetPreferences> =
        repository.preferencesFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WidgetPreferencesRepository.WidgetPreferences()
        )

    /** Saving a style/theme triggers an immediate widget refresh in the repository. */
    fun setStyle(style: WidgetStyle) {
        viewModelScope.launch { repository.setWidgetStyle(style) }
    }

    fun setThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch { repository.setWidgetThemeMode(mode) }
    }

    /** Refresh button: re-run sync and force a full widget rebind. */
    fun syncAndRefresh(context: Context) {
        FactSyncWorker.enqueueImmediate(context)
        WikiWidgetProvider.refreshAll(context)
    }
}

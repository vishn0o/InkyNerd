package com.wikiink.app.ui.widgetstyle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wikiink.app.WikiInkApp
import com.wikiink.app.data.prefs.WidgetLayout
import com.wikiink.app.data.prefs.WidgetPreferences
import com.wikiink.app.data.prefs.WidgetPreferencesRepository
import com.wikiink.app.data.prefs.WidgetStyle
import com.wikiink.app.data.prefs.WidgetThemeMode
import com.wikiink.app.widget.WidgetTopic
import com.wikiink.app.widget.WikiWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A topic the widget can show, with how many facts are cached for it. */
data class TopicOption(val topic: WidgetTopic, val count: Int)

class WidgetStyleViewModel(
    private val prefs: WidgetPreferencesRepository,
    private val syncAndRefreshAction: () -> Unit,
    topicCounts: Flow<Pair<Map<String, Int>, Map<String, Int>>>,
    private val currentTopic: () -> WidgetTopic,
    private val applyTopic: (WidgetTopic) -> Unit,
    private val hasWidgets: () -> Boolean,
) : ViewModel() {

    val topics: StateFlow<List<TopicOption>> = topicCounts
        .map { (categories, subjects) ->
            WidgetTopic.available(categories, subjects).map { t ->
                val n = when {
                    t.category != null -> categories[t.category] ?: 0
                    t.subject != null -> subjects[t.subject.name] ?: 0
                    else -> categories.values.sum()
                }
                TopicOption(t, n)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTopic = MutableStateFlow(currentTopic())
    val selectedTopic: StateFlow<WidgetTopic> = _selectedTopic.asStateFlow()

    private val _widgetsPlaced = MutableStateFlow(hasWidgets())
    val widgetsPlaced: StateFlow<Boolean> = _widgetsPlaced.asStateFlow()

    /** Points every placed widget at [topic]. */
    fun selectTopic(topic: WidgetTopic) {
        applyTopic(topic)
        _selectedTopic.value = topic
    }

    /** Re-reads widget state when the screen is shown (a widget may have been added or re-topic'd). */
    fun refreshWidgetState() {
        _widgetsPlaced.value = hasWidgets()
        _selectedTopic.value = currentTopic()
    }

    val preferences: StateFlow<WidgetPreferences> = prefs.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetPreferences())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Saves instantly; the repository refreshes live home-screen widgets on every write. */
    fun selectStyle(style: WidgetStyle) {
        viewModelScope.launch { prefs.setStyle(style) }
    }

    fun selectLayout(layout: WidgetLayout) {
        viewModelScope.launch { prefs.setLayout(layout) }
    }

    fun selectMode(mode: WidgetThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun syncAndRefresh() {
        syncAndRefreshAction()
        _message.value = "WIDGET REFRESHED // SYNC QUEUED"
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WikiInkApp
                WidgetStyleViewModel(
                    prefs = app.container.widgetPrefs,
                    syncAndRefreshAction = {
                        com.wikiink.app.worker.FactSyncWorker.enqueueImmediate(app)
                        WikiWidgetProvider.reloadCards(app)
                    },
                    topicCounts = app.container.repository.observeTopicCounts(),
                    currentTopic = { WikiWidgetProvider.currentTopic(app) },
                    applyTopic = { WikiWidgetProvider.setTopicForAll(app, it) },
                    hasWidgets = { WikiWidgetProvider.allWidgetIds(app).isNotEmpty() },
                )
            }
        }
    }
}

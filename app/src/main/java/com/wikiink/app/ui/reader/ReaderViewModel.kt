package com.wikiink.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wikiink.app.WikiInkApp
import com.wikiink.app.data.local.FactEntity
import com.wikiink.app.data.local.FolderEntity
import com.wikiink.app.data.repository.ReaderArticle
import com.wikiink.app.data.repository.WikiRepository
import com.wikiink.app.ui.theme.ReaderFontSize
import com.wikiink.app.ui.theme.ReaderTypeface
import com.wikiink.app.ui.theme.ReaderPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    /** No title given and the cache is empty — waiting for the first sync. */
    data object Empty : ReaderUiState
    data class Content(val article: ReaderArticle) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

class ReaderViewModel(
    private val savedState: SavedStateHandle,
    private val repository: WikiRepository,
    private val prefs: ReaderPrefs,
    private val requestSync: () -> Unit,
) : ViewModel() {

    private val requestedTitle: String? = savedState.get<String>(ARG_TITLE)?.takeIf { it.isNotBlank() }
    private val requestedFactId: String? = savedState.get<String>(ARG_FACT_ID)?.takeIf { it.isNotBlank() }

    /** Only one article load at a time: a rapid Next / Prev cancels the load it overtakes. */
    private var loadJob: Job? = null

    /** Guards against a double-tapped "new folder + save" creating two folders. */
    private var creatingFolder = false

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Bumped on every successful article open so the screen can run the refresh flash. */
    private val _openCount = MutableStateFlow(0)
    val openCount: StateFlow<Int> = _openCount.asStateFlow()

    val fontSize: StateFlow<ReaderFontSize> = prefs.fontSize
    val typeface: StateFlow<ReaderTypeface> = prefs.typeface

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val isBookmarked: StateFlow<Boolean> = _state
        .filterIsInstance<ReaderUiState.Content>()
        .map { it.article.title }
        .flatMapLatest<String, Boolean> { repository.observeIsBookmarked(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Cached facts in deck order, for Prev / Next. */
    private val deck: StateFlow<List<FactEntity>> = repository.observeFacts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Prev / Next are usable once there's more than one fact to move between. */
    val canStep: StateFlow<Boolean> = deck.map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * What's on screen now (title, fact id), so Prev / Next know where they are in the deck.
     * Mirrored into [savedState] so the same article comes back after process death.
     */
    private var current: Pair<String, String?>? = savedState.get<String>(KEY_CURRENT_TITLE)
        ?.let { it to savedState.get<String>(KEY_CURRENT_FACT_ID) }
        set(value) {
            field = value
            savedState[KEY_CURRENT_TITLE] = value?.first
            savedState[KEY_CURRENT_FACT_ID] = value?.second
        }

    /** Where you've been (most recent last), so ‹ walks back along a Rabbit Hole trail. */
    private val trail = ArrayDeque<Pair<String, String?>>()

    private val _diving = MutableStateFlow(false)
    /** True while a Rabbit Hole jump is fetching links. */
    val diving: StateFlow<Boolean> = _diving.asStateFlow()

    init {
        load()
        // Empty archive on first launch: auto-open the first fact as soon as the sync lands.
        if (requestedTitle == null) {
            viewModelScope.launch {
                repository.observeFacts().first { it.isNotEmpty() && _state.value == ReaderUiState.Empty }
                load()
            }
        }
    }

    fun next() = step(+1)

    /** Back along the trail if there is one (e.g. after a Rabbit Hole dive), else the previous fact. */
    fun previous() {
        val back = trail.removeLastOrNull()
        if (back != null) open(back.first, back.second, remember = false) else step(-1)
    }

    /** Rabbit Hole: jump to a random article linked from the current one. */
    fun rabbitHole() {
        val (title, _) = current ?: return
        if (_diving.value) return
        viewModelScope.launch {
            _diving.value = true
            val next = repository.rabbitHoleFrom(title)
            _diving.value = false
            if (next == null) {
                _message.value = "Rabbit Hole needs internet (or this page has no links)."
            } else {
                _message.value = "↘ DOWN THE RABBIT HOLE"
                open(next, null)
            }
        }
    }

    /**
     * Moves through the fact deck, wrapping at either end. An article opened from a bookmark (not
     * in the deck) steps to the first fact.
     */
    private fun step(delta: Int) {
        val list = deck.value
        if (list.isEmpty()) return
        val (title, factId) = current ?: (null to null)
        val i = list.indexOfFirst { it.id == factId }.takeIf { it >= 0 }
            ?: list.indexOfFirst { it.articleTitle.equals(title, ignoreCase = true) }
        val target = if (i < 0) list.first() else list[(i + delta).mod(list.size)]
        open(target.articleTitle, target.id, remember = false)
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val (title, factId) = when {
                current != null -> current!!
                requestedTitle != null -> requestedTitle to requestedFactId
                else -> repository.newestFact()?.let { it.articleTitle to it.id }
                    ?: run {
                        _state.value = ReaderUiState.Empty
                        return@launch
                    }
            }
            openNow(title, factId, remember = false)
        }
    }

    private fun open(title: String, factId: String?, remember: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { openNow(title, factId, remember) }
    }

    /** @param remember push the article we're leaving onto the back trail */
    private suspend fun openNow(title: String, factId: String?, remember: Boolean = true) {
        run {
            if (remember) current?.let { if (it.first != title) trail.addLast(it) }
            while (trail.size > MAX_TRAIL) trail.removeFirst()
            _state.value = ReaderUiState.Loading
            current = title to factId
            _state.value = try {
                ReaderUiState.Content(repository.loadArticle(title, factId)).also {
                    current = it.article.title to (it.article.factId ?: factId)
                    _openCount.update { n -> n + 1 }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReaderUiState.Error(e.message ?: "Could not load article")
            }
        }
    }

    fun syncNow() {
        requestSync()
        _message.value = "Sync requested — facts will appear shortly."
    }

    fun setFontSize(size: ReaderFontSize) = prefs.setFontSize(size)
    fun setTypeface(typeface: ReaderTypeface) = prefs.setTypeface(typeface)

    fun bookmarkTo(folderId: Long) {
        val article = (state.value as? ReaderUiState.Content)?.article ?: return
        viewModelScope.launch {
            repository.bookmark(article, folderId)
            _message.value = "Saved to folder."
        }
    }

    fun createFolderAndBookmark(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || creatingFolder) return
        creatingFolder = true
        viewModelScope.launch {
            try {
                bookmarkTo(repository.createFolder(trimmed))
            } finally {
                creatingFolder = false
            }
        }
    }

    fun removeBookmark() {
        val article = (state.value as? ReaderUiState.Content)?.article ?: return
        viewModelScope.launch {
            repository.removeBookmark(article.title)
            _message.value = "Bookmark removed."
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        const val ARG_TITLE = "title"
        const val ARG_FACT_ID = "factId"
        private const val MAX_TRAIL = 50
        private const val KEY_CURRENT_TITLE = "reader.currentTitle"
        private const val KEY_CURRENT_FACT_ID = "reader.currentFactId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WikiInkApp
                ReaderViewModel(
                    savedState = createSavedStateHandle(),
                    repository = app.container.repository,
                    prefs = app.container.readerPrefs,
                    requestSync = { com.wikiink.app.worker.FactSyncWorker.enqueueImmediate(app) },
                )
            }
        }
    }
}


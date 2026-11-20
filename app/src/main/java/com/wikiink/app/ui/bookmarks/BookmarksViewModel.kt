package com.wikiink.app.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wikiink.app.WikiInkApp
import com.wikiink.app.data.local.BookmarkEntity
import com.wikiink.app.data.local.FolderEntity
import com.wikiink.app.data.local.FolderWithCount
import com.wikiink.app.data.repository.WikiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarksViewModel(private val repository: WikiRepository) : ViewModel() {

    val folders: StateFlow<List<FolderWithCount>> = repository.observeFoldersWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openFolderId = MutableStateFlow<Long?>(null)
    val openFolderId: StateFlow<Long?> = _openFolderId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val openFolder: StateFlow<FolderEntity?> = _openFolderId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeFolder(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarks: StateFlow<List<BookmarkEntity>> = _openFolderId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeBookmarksIn(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** id of the seeded default folder (the oldest one); it cannot be deleted. */
    val defaultFolderId: Long?
        get() = folders.value.minByOrNull { it.id }?.id

    fun openFolder(id: Long?) {
        _openFolderId.value = id
    }

    /** Guards against a double-tapped "Create" making two folders. */
    private var creating: Job? = null

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || creating?.isActive == true) return
        creating = viewModelScope.launch { repository.createFolder(trimmed) }
    }

    fun deleteFolder(id: Long) {
        if (id == defaultFolderId) return
        viewModelScope.launch {
            repository.deleteFolder(id)
            if (_openFolderId.value == id) _openFolderId.value = null
        }
    }

    fun removeBookmark(title: String) {
        viewModelScope.launch { repository.removeBookmark(title) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WikiInkApp
                BookmarksViewModel(app.container.repository)
            }
        }
    }
}

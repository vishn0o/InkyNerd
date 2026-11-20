package com.wikiink.app.ui.bookmarks

import app.cash.turbine.test
import com.wikiink.app.data.local.BookmarkEntity
import com.wikiink.app.data.local.FolderEntity
import com.wikiink.app.data.local.FolderWithCount
import com.wikiink.app.data.repository.WikiRepository
import com.wikiink.app.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class BookmarksViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val folders = MutableStateFlow(
        listOf(FolderWithCount(1, "General", 0, 0), FolderWithCount(2, "Space", 0, 1)),
    )
    private val saved = BookmarkEntity("Voyager 1", 2, "Hook", "Text", null, 0)
    private val repository = mockk<WikiRepository>(relaxed = true) {
        every { observeFoldersWithCount() } returns folders
        every { observeFolder(2) } returns flowOf(FolderEntity(2, "Space", 0))
        every { observeBookmarksIn(2) } returns flowOf(listOf(saved))
    }

    @Test
    fun openingAFolderStreamsItsBookmarks() = runTest(main.dispatcher) {
        val vm = BookmarksViewModel(repository)
        vm.bookmarks.test {
            assertEquals(emptyList<BookmarkEntity>(), awaitItem())
            vm.openFolder(2)
            assertEquals(listOf(saved), awaitItem())
            vm.openFolder(null)
            assertEquals(emptyList<BookmarkEntity>(), awaitItem())
        }
    }

    @Test
    fun doubleTappedCreateMakesOneFolder() = runTest(main.dispatcher) {
        val slow = CompletableDeferred<Long>()
        coEvery { repository.createFolder(any()) } coAnswers { slow.await() }
        val vm = BookmarksViewModel(repository)
        vm.createFolder(" Maps ")
        vm.createFolder(" Maps ")
        advanceUntilIdle()
        slow.complete(3)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.createFolder("Maps") }
    }

    @Test
    fun blankFolderNameIsIgnored() = runTest(main.dispatcher) {
        BookmarksViewModel(repository).createFolder("   ")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.createFolder(any()) }
    }

    @Test
    fun defaultFolderCannotBeDeleted() = runTest(main.dispatcher) {
        val vm = BookmarksViewModel(repository)
        vm.folders.test {
            awaitItem() // initial
            awaitItem() // from the repository
            vm.deleteFolder(1)
            advanceUntilIdle()
            coVerify(exactly = 0) { repository.deleteFolder(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingTheOpenFolderClosesIt() = runTest(main.dispatcher) {
        val vm = BookmarksViewModel(repository)
        vm.folders.test {
            awaitItem(); awaitItem()
            vm.openFolder(2)
            vm.deleteFolder(2)
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.deleteFolder(2) }
            assertNull(vm.openFolderId.value)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

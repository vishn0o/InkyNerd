package com.wikiink.app.data.repository

import com.wikiink.app.data.local.BookmarkDao
import com.wikiink.app.data.local.BookmarkEntity
import com.wikiink.app.data.local.FactDao
import com.wikiink.app.data.local.FolderEntity
import com.wikiink.app.data.remote.MediaWikiApiService
import com.wikiink.app.data.remote.WikipediaApiService
import com.wikiink.app.testing.fact
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WikiRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val offline = IOException("offline")
    private val wikiApi = mockk<WikipediaApiService> { coEvery { pageSummary(any()) } throws offline }
    private val mediaWikiApi = mockk<MediaWikiApiService> {
        coEvery { plainTextExtract(any()) } throws offline
        coEvery { links(any()) } throws offline
    }
    private val factDao = mockk<FactDao>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private var changes = 0

    private val repository = WikiRepository(
        wikiApi = wikiApi,
        mediaWikiApi = mediaWikiApi,
        factDao = factDao,
        bookmarkDao = bookmarkDao,
        onFactsChanged = { changes++ },
        clock = { 1_000L },
        io = dispatcher,
    )

    @Test
    fun widgetBookmarkTapRemovesAnExistingBookmark() = runTest(dispatcher) {
        coEvery { bookmarkDao.getBookmark("Voyager 1") } returns BookmarkEntity("Voyager 1", 2, "h", "t", null, 0)
        coEvery { bookmarkDao.folderName(2) } returns "Space"

        val result = repository.toggleQuickBookmark("Voyager 1", null)

        assertEquals(WikiRepository.QuickSaveResult(saved = false, folderName = "Space"), result)
        coVerify { bookmarkDao.delete("Voyager 1") }
        coVerify(exactly = 0) { bookmarkDao.upsert(any()) }
        assertEquals(1, changes)
    }

    @Test
    fun widgetBookmarkOfflineSavesTheCachedText() = runTest(dispatcher) {
        coEvery { bookmarkDao.getBookmark(any()) } returns null
        coEvery { bookmarkDao.defaultFolderId() } returns 1
        coEvery { bookmarkDao.folderName(1) } returns "General"
        coEvery { factDao.getById("id-Alpha") } returns fact("Alpha")
        val saved = slot<BookmarkEntity>()
        coEvery { bookmarkDao.upsert(capture(saved)) } returns Unit

        val result = repository.toggleQuickBookmark("Alpha", "id-Alpha")

        assertTrue(result.saved)
        assertEquals("General", result.folderName)
        assertEquals("Summary of Alpha", saved.captured.fullPlainText) // no network: falls back to the summary
        assertEquals(1, saved.captured.folderId)
        assertEquals(1_000L, saved.captured.savedAt)
    }

    @Test
    fun widgetBookmarkRecreatesAMissingDefaultFolder() = runTest(dispatcher) {
        coEvery { bookmarkDao.getBookmark(any()) } returns null
        coEvery { bookmarkDao.defaultFolderId() } returns null
        coEvery { bookmarkDao.insertFolder(any()) } returns 9
        coEvery { factDao.getByTitle(any()) } returns fact("Alpha")

        repository.toggleQuickBookmark("Alpha", null)

        coVerify { bookmarkDao.insertFolder(FolderEntity(name = "General", createdAt = 1_000L)) }
        coVerify { bookmarkDao.upsert(match { it.folderId == 9L }) }
    }

    @Test
    fun articleOpensOfflineFromTheCacheAndIsMarkedSeen() = runTest(dispatcher) {
        coEvery { factDao.getById("id-Alpha") } returns fact("Alpha", fullText = "Cached body of Alpha.")
        coEvery { bookmarkDao.getBookmark(any()) } returns null

        val article = repository.loadArticle("Alpha", "id-Alpha")

        assertEquals("Alpha", article.title)
        assertEquals("Cached body of Alpha.", article.rawPlainText)
        coVerify { factDao.markSeen("id-Alpha") }
        coVerify(exactly = 0) { factDao.updateFullText(any(), any()) }
    }

    @Test(expected = IllegalStateException::class)
    fun articleWithNoCopyAnywhereFailsOffline() = runTest(dispatcher) {
        coEvery { factDao.getById(any()) } returns null
        coEvery { factDao.getByTitle(any()) } returns null
        coEvery { bookmarkDao.getBookmark(any()) } returns null
        repository.loadArticle("Nowhere", null)
    }

    @Test
    fun rabbitHoleIsNullOffline() = runTest(dispatcher) {
        assertEquals(null, repository.rabbitHoleFrom("Alpha"))
    }

    @Test
    fun quizPoolIsEmptyOffline() = runTest(dispatcher) {
        coEvery { mediaWikiApi.randomPool() } throws offline
        coEvery { mediaWikiApi.searchPool(any()) } throws offline
        assertTrue(repository.quizPool(com.wikiink.app.data.quiz.QuizTopics.AUTO).isEmpty())
    }

    @Test
    fun createFolderTrimsTheName() = runTest(dispatcher) {
        repository.createFolder("  Maps ")
        coVerify { bookmarkDao.insertFolder(FolderEntity(name = "Maps", createdAt = 1_000L)) }
    }

    @Test
    fun removingABookmarkRefreshesTheWidget() = runTest(dispatcher) {
        repository.removeBookmark("Alpha")
        coVerify { bookmarkDao.delete("Alpha") }
        assertFalse(changes == 0)
    }
}

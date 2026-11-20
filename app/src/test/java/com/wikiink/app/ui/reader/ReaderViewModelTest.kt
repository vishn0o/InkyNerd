package com.wikiink.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.wikiink.app.data.repository.WikiRepository
import com.wikiink.app.testing.MainDispatcherRule
import com.wikiink.app.testing.article
import com.wikiink.app.testing.fact
import com.wikiink.app.ui.theme.ReaderPrefs
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val facts = listOf(fact("Alpha"), fact("Beta"), fact("Gamma"))
    private val repository = mockk<WikiRepository>(relaxed = true) {
        every { observeFacts() } returns MutableStateFlow(facts)
        every { observeFolders() } returns flowOf(emptyList())
        every { observeIsBookmarked(any()) } returns flowOf(false)
        coEvery { loadArticle(any(), any()) } answers { article(firstArg(), secondArg()) }
    }
    private val prefs = mockk<ReaderPrefs>(relaxed = true)

    private fun viewModel(vararg saved: Pair<String, Any?>) =
        ReaderViewModel(SavedStateHandle(mapOf(*saved)), repository, prefs, requestSync = {})

    private val ReaderViewModel.title get() = (state.value as ReaderUiState.Content).article.title

    @Test
    fun opensTheRequestedArticle() = runTest(main.dispatcher) {
        val vm = viewModel(ReaderViewModel.ARG_TITLE to "Beta", ReaderViewModel.ARG_FACT_ID to "id-Beta")
        vm.state.test {
            assertEquals(ReaderUiState.Loading, awaitItem())
            assertEquals("Beta", (awaitItem() as ReaderUiState.Content).article.title)
        }
    }

    @Test
    fun emptyCacheShowsEmptyState() = runTest(main.dispatcher) {
        every { repository.observeFacts() } returns MutableStateFlow(emptyList())
        coEvery { repository.newestFact() } returns null
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ReaderUiState.Empty, vm.state.value)
    }

    @Test
    fun offlineWithNoCopyShowsError() = runTest(main.dispatcher) {
        coEvery { repository.loadArticle(any(), any()) } throws IllegalStateException("Article unavailable offline")
        val vm = viewModel(ReaderViewModel.ARG_TITLE to "Alpha")
        advanceUntilIdle()
        assertEquals(ReaderUiState.Error("Article unavailable offline"), vm.state.value)
    }

    @Test
    fun rapidNextCancelsTheLoadItOvertakes() = runTest(main.dispatcher) {
        val slowBeta = CompletableDeferred<Unit>()
        coEvery { repository.loadArticle("Beta", any()) } coAnswers {
            slowBeta.await()
            article("Beta")
        }
        val vm = viewModel(ReaderViewModel.ARG_TITLE to "Alpha", ReaderViewModel.ARG_FACT_ID to "id-Alpha")
        advanceUntilIdle()

        vm.next() // Beta: hangs on the network
        advanceUntilIdle()
        vm.next() // Gamma: overtakes it
        advanceUntilIdle()
        assertEquals("Gamma", vm.title)

        slowBeta.complete(Unit) // the stale Beta load must not land on top of Gamma
        advanceUntilIdle()
        assertEquals("Gamma", vm.title)
    }

    @Test
    fun currentArticleSurvivesProcessDeath() = runTest(main.dispatcher) {
        val handle = SavedStateHandle(mapOf(ReaderViewModel.ARG_TITLE to "Alpha"))
        val first = ReaderViewModel(handle, repository, prefs, requestSync = {})
        advanceUntilIdle()
        first.next()
        advanceUntilIdle()
        assertEquals("Beta", first.title)

        // The system restores only the saved-state bundle into a brand new ViewModel.
        val restored = ReaderViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), repository, prefs, requestSync = {})
        advanceUntilIdle()
        assertEquals("Beta", restored.title)
    }

    @Test
    fun previousWalksBackAlongTheRabbitHoleTrail() = runTest(main.dispatcher) {
        coEvery { repository.rabbitHoleFrom("Alpha") } returns "Zeta"
        val vm = viewModel(ReaderViewModel.ARG_TITLE to "Alpha", ReaderViewModel.ARG_FACT_ID to "id-Alpha")
        advanceUntilIdle()
        vm.rabbitHole()
        advanceUntilIdle()
        assertEquals("Zeta", vm.title)
        vm.previous()
        advanceUntilIdle()
        assertEquals("Alpha", vm.title)
    }

    @Test
    fun rabbitHoleOfflineExplainsWhy() = runTest(main.dispatcher) {
        coEvery { repository.rabbitHoleFrom(any()) } returns null
        val vm = viewModel(ReaderViewModel.ARG_TITLE to "Alpha")
        advanceUntilIdle()
        vm.rabbitHole()
        advanceUntilIdle()
        assertTrue(vm.message.value!!.contains("internet"))
        assertEquals("Alpha", vm.title)
    }

    @Test
    fun doubleTappedNewFolderCreatesOneFolder() = runTest(main.dispatcher) {
        coEvery { repository.createFolder(any()) } returns 7L
        val vm = viewModel(ReaderViewModel.ARG_TITLE to "Alpha")
        advanceUntilIdle()
        vm.createFolderAndBookmark("  Space  ")
        vm.createFolderAndBookmark("  Space  ")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.createFolder("Space") }
        coVerify(exactly = 1) { repository.bookmark(match { it.title == "Alpha" }, 7L) }
    }

    @Test
    fun blankFolderNameIsIgnored() = runTest(main.dispatcher) {
        val vm = viewModel(ReaderViewModel.ARG_TITLE to "Alpha")
        advanceUntilIdle()
        vm.createFolderAndBookmark("   ")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.createFolder(any()) }
    }
}

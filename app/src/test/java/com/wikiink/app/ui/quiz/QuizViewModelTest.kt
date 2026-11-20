package com.wikiink.app.ui.quiz

import com.wikiink.app.data.quiz.DailyChallenge
import com.wikiink.app.data.quiz.DailyState
import com.wikiink.app.data.quiz.NerdPoints
import com.wikiink.app.data.quiz.QuizSource
import com.wikiink.app.data.repository.WikiRepository
import com.wikiink.app.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class QuizViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val repository = mockk<WikiRepository>(relaxed = true) {
        every { observeFacts() } returns MutableStateFlow(emptyList())
        coEvery { quizPool(any()) } returns emptyList<QuizSource>()
    }
    private val points = mockk<NerdPoints>(relaxed = true) {
        every { stats } returns MutableStateFlow(emptyMap())
    }
    private val daily = mockk<DailyChallenge>(relaxed = true) {
        every { state } returns MutableStateFlow(DailyState(LocalDate.of(2026, 11, 20), emptyList(), emptyList(), 0))
    }

    private fun viewModel() = QuizViewModel(repository, points, daily)

    @Test
    fun doubleTappedNextFetchesOnce() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.nextRanked()
        vm.nextRanked()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.quizPool(any()) }
    }

    @Test
    fun offlineRankedShowsErrorNotSpinner() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.startRanked()
        advanceUntilIdle()
        val s = vm.ranked.value
        assertNotNull(s.error)
        assertFalse(s.loading)
        assertNull(s.round)
    }

    @Test
    fun endingRankedCancelsTheFetchInFlight() = runTest(main.dispatcher) {
        val network = CompletableDeferred<List<QuizSource>>()
        coEvery { repository.quizPool(any()) } coAnswers { network.await() }
        val vm = viewModel()
        vm.startRanked()
        advanceUntilIdle()
        vm.endRanked()
        network.complete(emptyList())
        advanceUntilIdle()
        // The late network reply must not pop an error onto the setup screen.
        assertNull(vm.ranked.value.error)
        assertFalse(vm.ranked.value.loading)
    }

    @Test
    fun openingDailyTwiceGeneratesOnce() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.openDaily()
        vm.openDaily()
        advanceUntilIdle()
        // One generation makes up to MAX_FETCHES (3) pool requests.
        coVerify(exactly = 3) { repository.quizPool(any()) }
        assertNotNull(vm.dailyUi.value.error)
    }

    @Test
    fun quickWithTooFewFactsSaysSo() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.nextQuick()
        assertEquals(true, vm.quick.value.notEnough)
    }

    @Test
    fun resetNeedsTwoTaps() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.setLevel(com.wikiink.app.data.quiz.QuizLevel.CURIOUS)
        vm.resetLevel()
        io.mockk.verify(exactly = 0) { points.reset(any()) }
        vm.resetLevel()
        io.mockk.verify(exactly = 1) { points.reset(com.wikiink.app.data.quiz.QuizLevel.CURIOUS) }
    }
}

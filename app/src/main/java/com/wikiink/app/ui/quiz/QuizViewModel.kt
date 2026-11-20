package com.wikiink.app.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wikiink.app.WikiInkApp
import com.wikiink.app.data.local.FactEntity
import com.wikiink.app.data.quiz.DailyChallenge
import com.wikiink.app.data.quiz.DailyState
import com.wikiink.app.data.quiz.LevelStats
import com.wikiink.app.data.quiz.NerdPoints
import com.wikiink.app.data.quiz.QuizBuilder
import com.wikiink.app.data.quiz.QuizLevel
import com.wikiink.app.data.quiz.QuizTopic
import com.wikiink.app.data.quiz.QuizTopics
import com.wikiink.app.data.quiz.formatPoints
import com.wikiink.app.data.repository.NerdGames
import com.wikiink.app.data.repository.QuizQuestion
import com.wikiink.app.data.repository.WikiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuizMode { QUICK, DAILY, RANKED }

/** One question on screen: what was picked (null = unanswered) and, for ranked, the points it earned. */
data class Round(val question: QuizQuestion, val picked: String? = null, val delta: Double? = null)

/** Casual Guess It from cached facts. */
data class QuickState(val round: Round? = null, val score: Int = 0, val asked: Int = 0, val notEnough: Boolean = false)

/** Ranked quiz: chosen level and topic, the topic being browsed, and the live round. */
data class RankedState(
    val level: QuizLevel = QuizLevel.CURIOUS,
    val topic: QuizTopic = QuizTopics.AUTO,
    val browse: QuizTopic = QuizTopics.AUTO,
    val round: Round? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val session: Double = 0.0,
    val confirmReset: Boolean = false,
    /** NERD only: seconds left on the current question (null = no timer running). */
    val secondsLeft: Int? = null,
)

/** Daily challenge screen state on top of the stored [DailyState]. */
data class DailyUi(val loading: Boolean = false, val error: String? = null)

class QuizViewModel(
    private val repository: WikiRepository,
    private val points: NerdPoints,
    private val daily: DailyChallenge,
) : ViewModel() {

    val dailyState: StateFlow<DailyState> = daily.state

    private val _dailyUi = MutableStateFlow(DailyUi())
    val dailyUi: StateFlow<DailyUi> = _dailyUi.asStateFlow()

    private var timer: Job? = null

    /** The in-flight ranked fetch; a second tap on NEXT while it runs is ignored. */
    private var rankedJob: Job? = null

    /** The in-flight daily-set generation. */
    private var dailyJob: Job? = null

    private val deck: StateFlow<List<FactEntity>> = repository.observeFacts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<Map<QuizLevel, LevelStats>> = points.stats

    private val _mode = MutableStateFlow(QuizMode.QUICK)
    val mode: StateFlow<QuizMode> = _mode.asStateFlow()

    private val _quick = MutableStateFlow(QuickState())
    val quick: StateFlow<QuickState> = _quick.asStateFlow()

    private val _ranked = MutableStateFlow(RankedState())
    val ranked: StateFlow<RankedState> = _ranked.asStateFlow()

    /** Upcoming ranked questions, and every article shown this session (never reused as answer or option). */
    private val queue = ArrayDeque<QuizQuestion>()
    private val rankedUsed = mutableSetOf<String>()

    init {
        // Start a Quick round as soon as there are enough cached facts.
        viewModelScope.launch {
            deck.first { it.size >= 3 }
            if (_quick.value.round == null) nextQuick()
        }
    }

    fun setMode(mode: QuizMode) {
        _mode.value = mode
        if (mode == QuizMode.QUICK && _quick.value.round == null) nextQuick()
        if (mode == QuizMode.DAILY) openDaily()
    }

    // ------------------------------------------------------------------ Daily

    /** Loads today's set, generating and saving it on the first open of the day. */
    fun openDaily() {
        daily.refresh()
        if (daily.state.value.questions.isNotEmpty() || dailyJob?.isActive == true) return
        dailyJob = viewModelScope.launch {
            _dailyUi.value = DailyUi(loading = true)
            val today = daily.state.value.date
            // Everyone's topic for the day is fixed by the date; the questions are saved on first open.
            val rng = kotlin.random.Random(today.toEpochDay())
            val topic = QuizTopics.AUTO.children[rng.nextInt(QuizTopics.AUTO.children.size)]
            val questions = mutableListOf<QuizQuestion>()
            val used = mutableSetOf<String>()
            repeat(MAX_FETCHES) {
                if (questions.size >= DailyChallenge.SIZE) return@repeat
                questions += QuizBuilder.round(repository.quizPool(topic), QuizLevel.GEEK, used, rng)
            }
            if (questions.size >= DailyChallenge.SIZE) {
                daily.saveQuestions(questions.take(DailyChallenge.SIZE))
                _dailyUi.value = DailyUi()
            } else {
                _dailyUi.value = DailyUi(error = "Couldn't fetch today's challenge. It needs internet the first time each day.")
            }
        }
    }

    fun answerDaily(choice: String) = daily.answer(choice)

    // ------------------------------------------------------------------ Share

    /** A plain-text score card for the share sheet. */
    fun shareText(): String {
        val s = stats.value
        val lines = QuizLevel.entries.reversed().joinToString("\n") { level ->
            val st = s[level] ?: LevelStats()
            val acc = if (st.answered > 0) "  (${st.correct * 100 / st.answered}% of ${st.answered})" else ""
            "${level.label.padEnd(9)} ${formatPoints(st.points)}$acc"
        }
        val streak = daily.state.value.streak
        return buildString {
            appendLine("INKYNERD // NERD POINTS")
            appendLine(lines)
            if (streak > 0) appendLine("Daily streak: $streak day${if (streak == 1) "" else "s"}")
            append("Think you can out-nerd me?")
        }
    }

    // ------------------------------------------------------------------ Quick

    /** Every article shown in Quick this session (answers and options), so none repeat. */
    private val quickUsed = mutableSetOf<String>()

    fun nextQuick() {
        var q = NerdGames.quiz(deck.value, excludeTitle = _quick.value.round?.question?.answer, used = quickUsed)
        if (q == null && quickUsed.isNotEmpty()) {
            // Went through the whole deck: start a fresh cycle.
            quickUsed.clear()
            q = NerdGames.quiz(deck.value, excludeTitle = _quick.value.round?.question?.answer)
        }
        val question = q
        question?.let { quickUsed += it.options.map(String::lowercase) }
        _quick.update {
            if (question == null) it.copy(round = null, notEnough = true)
            else it.copy(round = Round(question), asked = it.asked + 1, notEnough = false)
        }
    }

    fun answerQuick(choice: String) {
        val r = _quick.value.round ?: return
        if (r.picked != null) return
        _quick.update { it.copy(round = r.copy(picked = choice), score = it.score + if (choice == r.question.answer) 1 else 0) }
    }

    // ------------------------------------------------------------------ Ranked setup

    fun setLevel(level: QuizLevel) {
        if (level != _ranked.value.level) resetPool()
        _ranked.update { it.copy(level = level, confirmReset = false) }
    }

    /** Selects [topic] as the quiz topic and browses into it (shows its sub-topics). */
    fun chooseTopic(topic: QuizTopic) {
        if (topic != _ranked.value.topic) resetPool()
        _ranked.update { it.copy(topic = topic, browse = topic) }
    }

    /** Goes one level up the tree (selecting the parent as the topic). */
    fun topicUp() {
        QuizTopics.parent(_ranked.value.browse)?.let { chooseTopic(it) }
    }

    /** First tap arms the reset, second tap performs it. Never resets NERD. */
    fun resetLevel() {
        val s = _ranked.value
        if (!s.level.resettable) return
        if (!s.confirmReset) {
            _ranked.update { it.copy(confirmReset = true) }
        } else {
            points.reset(s.level)
            _ranked.update { it.copy(confirmReset = false) }
        }
    }

    // ------------------------------------------------------------------ Ranked play

    fun startRanked() {
        resetPool()
        _ranked.update { it.copy(session = 0.0, error = null, confirmReset = false) }
        nextRanked()
    }

    fun nextRanked() {
        val s = _ranked.value
        if (s.loading || rankedJob?.isActive == true) return
        rankedJob = viewModelScope.launch {
            val q = nextRankedQuestion(s.topic, s.level)
            _ranked.update {
                if (q == null) {
                    it.copy(loading = false, round = null, error = "Couldn't fetch questions. Ranked needs internet. QUICK works offline.")
                } else {
                    it.copy(loading = false, round = Round(q), error = null)
                }
            }
            if (q != null) startTimerIfNerd()
        }
    }

    /** NERD questions get [NERD_SECONDS]; running out counts as a wrong answer. */
    private fun startTimerIfNerd() {
        timer?.cancel()
        if (_ranked.value.level != QuizLevel.NERD) {
            _ranked.update { it.copy(secondsLeft = null) }
            return
        }
        timer = viewModelScope.launch {
            for (left in NERD_SECONDS downTo 1) {
                _ranked.update { it.copy(secondsLeft = left) }
                delay(1_000)
            }
            _ranked.update { it.copy(secondsLeft = 0) }
            answerRanked(TIMEOUT)
        }
    }

    private suspend fun nextRankedQuestion(topic: QuizTopic, level: QuizLevel): QuizQuestion? {
        repeat(MAX_FETCHES) {
            queue.removeFirstOrNull()?.let { return it }
            _ranked.update { it.copy(loading = true) }
            val pool = repository.quizPool(topic)
            if (pool.isEmpty()) return null
            queue.addAll(QuizBuilder.round(pool, level, rankedUsed))
        }
        return queue.removeFirstOrNull()
    }

    fun answerRanked(choice: String) {
        val s = _ranked.value
        val r = s.round ?: return
        if (r.picked != null) return
        if (choice != TIMEOUT) timer?.cancel()
        val delta = points.record(s.level, correct = choice == r.question.answer)
        _ranked.update { it.copy(round = r.copy(picked = choice, delta = delta), session = it.session + delta) }
    }

    /** Ends the round and returns to level / topic selection. */
    fun endRanked() {
        timer?.cancel()
        rankedJob?.cancel()
        _ranked.update { it.copy(round = null, loading = false, error = null, secondsLeft = null) }
    }

    /** New session (or topic / level): drop queued questions and forget what was shown. */
    private fun resetPool() {
        queue.clear()
        rankedUsed.clear()
    }

    companion object {
        private const val MAX_FETCHES = 3
        const val NERD_SECONDS = 20
        /** Sentinel "answer" recorded when the NERD timer runs out. */
        const val TIMEOUT = "\u0000timeout"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WikiInkApp
                QuizViewModel(app.container.repository, app.container.nerdPoints, app.container.dailyChallenge)
            }
        }
    }
}

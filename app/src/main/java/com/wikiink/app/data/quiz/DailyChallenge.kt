package com.wikiink.app.data.quiz

import android.content.Context
import com.wikiink.app.data.repository.QuizQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import androidx.core.content.edit

/** Today's Daily Nerd Challenge as stored: the fixed questions, answers so far, and the streak. */
data class DailyState(
    val date: LocalDate,
    val questions: List<QuizQuestion>,
    val answers: List<String?>,
    val streak: Int,
) {
    val done: Boolean get() = questions.isNotEmpty() && answers.size >= questions.size
    val correct: Int get() = answers.withIndex().count { (i, a) -> a == questions.getOrNull(i)?.answer }
}

/**
 * Daily Nerd Challenge: [SIZE] questions per day, generated once on the first open of the day and
 * then saved, so everyone gets one fixed set (no rerolls). Finishing on consecutive days builds a
 * streak; missing a day resets it.
 */
class DailyChallenge(context: Context, private val today: () -> LocalDate = LocalDate::now) {
    private val prefs = context.applicationContext.getSharedPreferences("daily_challenge", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val questionsSer = ListSerializer(QuizQuestion.serializer())
    private val answersSer = ListSerializer(String.serializer().nullable)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<DailyState> = _state.asStateFlow()

    /** Re-reads storage (call on screen open: the date may have rolled over). */
    fun refresh() {
        _state.value = load()
    }

    private fun load(): DailyState {
        val date = today()
        val stored = prefs.getString(KEY_DATE, null)
        val sameDay = stored == date.toString()
        return DailyState(
            date = date,
            questions = if (sameDay) decode(prefs.getString(KEY_QUESTIONS, null), questionsSer) else emptyList(),
            answers = if (sameDay) decode(prefs.getString(KEY_ANSWERS, null), answersSer) else emptyList(),
            streak = currentStreak(date),
        )
    }

    /** A streak only counts if the last completed day was today or yesterday. */
    private fun currentStreak(date: LocalDate): Int {
        val last = prefs.getString(KEY_LAST_DONE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 0
        return if (last == date || last == date.minusDays(1)) prefs.getInt(KEY_STREAK, 0) else 0
    }

    /** Saves today's generated set (only if today has none yet, so the set stays fixed). */
    fun saveQuestions(questions: List<QuizQuestion>) {
        if (_state.value.questions.isNotEmpty()) return
        prefs.edit {
            putString(KEY_DATE, today().toString())
            putString(KEY_QUESTIONS, json.encodeToString(questionsSer, questions))
            putString(KEY_ANSWERS, json.encodeToString(answersSer, emptyList()))
        }
        _state.value = load()
    }

    /** Records the answer to the next unanswered question; finishing the set extends the streak. */
    fun answer(choice: String?) {
        val s = _state.value
        if (s.done || s.questions.isEmpty()) return
        val answers = s.answers + choice
        prefs.edit {
            putString(KEY_ANSWERS, json.encodeToString(answersSer, answers))
            if (answers.size >= s.questions.size) {
                val last = prefs.getString(KEY_LAST_DONE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val streak = if (last == s.date.minusDays(1)) prefs.getInt(KEY_STREAK, 0) + 1 else 1
                putInt(KEY_STREAK, streak)
                putString(KEY_LAST_DONE, s.date.toString())
            }
        }
        _state.value = load()
    }

    private fun <T> decode(raw: String?, ser: kotlinx.serialization.KSerializer<List<T>>): List<T> =
        raw?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }.orEmpty()

    companion object {
        const val SIZE = 5
        private const val KEY_DATE = "date"
        private const val KEY_QUESTIONS = "questions"
        private const val KEY_ANSWERS = "answers"
        private const val KEY_STREAK = "streak"
        private const val KEY_LAST_DONE = "last_done"
    }
}

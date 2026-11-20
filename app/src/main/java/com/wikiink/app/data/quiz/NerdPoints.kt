package com.wikiink.app.data.quiz

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/** Lifetime ranked points for one level. */
data class LevelStats(val points: Double = 0.0, val answered: Int = 0, val correct: Int = 0)

/**
 * Lifetime Nerd Points per [QuizLevel], stored as exact quarter-points (no float drift).
 * NERD can never be reset; the practice levels can.
 */
class NerdPoints(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nerd_points", Context.MODE_PRIVATE)

    private val _stats = MutableStateFlow(readAll())
    val stats: StateFlow<Map<QuizLevel, LevelStats>> = _stats.asStateFlow()

    private fun read(level: QuizLevel) = LevelStats(
        points = prefs.getLong("q_${level.name}", 0L) / 4.0,
        answered = prefs.getInt("n_${level.name}", 0),
        correct = prefs.getInt("c_${level.name}", 0),
    )

    private fun readAll() = QuizLevel.entries.associateWith { read(it) }

    /** Records an answer and returns the points it earned (e.g. +1.0 or −0.25). */
    fun record(level: QuizLevel, correct: Boolean): Double {
        val delta = level.delta(correct)
        val s = read(level)
        prefs.edit {
            putLong("q_${level.name}", Math.round((s.points + delta) * 4))
            putInt("n_${level.name}", s.answered + 1)
            putInt("c_${level.name}", s.correct + if (correct) 1 else 0)
        }
        _stats.value = readAll()
        return delta
    }

    /** Resets a practice level. Does nothing for NERD. */
    fun reset(level: QuizLevel) {
        if (!level.resettable) return
        prefs.edit {
            remove("q_${level.name}")
            remove("n_${level.name}")
            remove("c_${level.name}")
        }
        _stats.value = readAll()
    }
}

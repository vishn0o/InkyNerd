package com.wikiink.app.widget

import android.content.Context
import com.wikiink.app.data.local.FactCategory
import com.wikiink.app.data.repository.Subject
import androidx.core.content.edit

/**
 * What a widget is narrowed to, cycled by tapping its tag: all facts, one source (Did you know,
 * Oddities, On this day), or one of the many [Subject]s. [chip] is shown on the card's tag while
 * the topic is active (null = show each fact's own label).
 */
data class WidgetTopic(
    val key: String,
    val label: String,
    val chip: String?,
    val category: String? = null,
    val subject: Subject? = null,
) {
    companion object {
        val ALL = WidgetTopic("ALL", "All topics", null)

        private val SOURCES = listOf(
            WidgetTopic("SRC_DYK", "Did you know", FactCategory.DID_YOU_KNOW, category = FactCategory.DID_YOU_KNOW),
            WidgetTopic("SRC_ODDITY", "Oddities", FactCategory.ODDITY, category = FactCategory.ODDITY),
            WidgetTopic("SRC_OTD", "On this day", FactCategory.ON_THIS_DAY, category = FactCategory.ON_THIS_DAY),
        )

        private val SUBJECTS = Subject.entries.map { WidgetTopic("SUB_${it.name}", it.label, it.chip, subject = it) }

        /** Cycle order: everything, the three sources, then every subject. */
        val entries: List<WidgetTopic> = listOf(ALL) + SOURCES + SUBJECTS

        fun parse(key: String?): WidgetTopic = entries.firstOrNull { it.key == key } ?: ALL

        /** Topics that currently have at least one cached fact (ALL always does). */
        fun available(categoryCounts: Map<String, Int>, subjectCounts: Map<String, Int>): List<WidgetTopic> =
            entries.filter { t ->
                when {
                    t.category != null -> (categoryCounts[t.category] ?: 0) > 0
                    t.subject != null -> (subjectCounts[t.subject.name] ?: 0) > 0
                    else -> true
                }
            }

        /** The topic after [current] among [available] ones, wrapping back to ALL. */
        fun next(current: WidgetTopic, categoryCounts: Map<String, Int>, subjectCounts: Map<String, Int>): WidgetTopic {
            val list = available(categoryCounts, subjectCounts)
            val i = list.indexOf(current)
            return list[(i + 1).mod(list.size)]
        }
    }
}

/** Small per-widget-instance state: flipper position, deck size, topic, and whether hints were shown. */
object WidgetInstanceState {
    private const val PREFS = "widget_instance_state"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun count(context: Context, id: Int): Int = prefs(context).getInt("count_$id", 0)

    fun position(context: Context, id: Int): Int {
        val p = prefs(context)
        val count = p.getInt("count_$id", 0)
        val pos = p.getInt("pos_$id", 0)
        return if (count > 0) pos % count else 0
    }

    /** Advances the remembered position by one (mod the last known deck size). */
    fun setPosition(context: Context, id: Int, position: Int) {
        prefs(context).edit { putInt("pos_$id", position.coerceAtLeast(0)) }
    }

    fun advance(context: Context, id: Int) {
        val p = prefs(context)
        val count = p.getInt("count_$id", 0).coerceAtLeast(1)
        p.edit { putInt("pos_$id", (p.getInt("pos_$id", 0) + 1) % count) }
    }

    fun setCount(context: Context, id: Int, count: Int) {
        prefs(context).edit { putInt("count_$id", count) }
    }

    fun topic(context: Context, id: Int): WidgetTopic = WidgetTopic.parse(prefs(context).getString("topic_$id", null))

    fun setTopic(context: Context, id: Int, topic: WidgetTopic) {
        prefs(context).edit {
            putString("topic_$id", topic.key)
            putInt("pos_$id", 0)
        }
    }

    /** True the first time it's called for [id]; false afterwards. */
    fun claimFirstHint(context: Context, id: Int): Boolean {
        val p = prefs(context)
        if (p.getBoolean("hinted_$id", false)) return false
        p.edit { putBoolean("hinted_$id", true) }
        return true
    }

    fun forget(context: Context, ids: IntArray) {
        prefs(context).edit {
            ids.forEach { id -> listOf("pos_", "count_", "topic_", "hinted_").forEach { remove("$it$id") } }
        }
    }
}

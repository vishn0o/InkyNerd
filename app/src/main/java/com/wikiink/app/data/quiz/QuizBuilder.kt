package com.wikiink.app.data.quiz

import com.wikiink.app.data.repository.NerdGames
import com.wikiink.app.data.repository.QuizQuestion
import kotlin.random.Random

/** One candidate article for a ranked question. */
data class QuizSource(val title: String, val description: String?, val extract: String)

/** Builds ranked questions from a pool of Wikipedia intros (pure; unit-tested). */
object QuizBuilder {

    const val MIN_EXTRACT = 80
    private const val MIN_SENTENCE = 40
    private const val MIN_CLUE = 35
    private val SENTENCE_BREAK = Regex("""(?<=[.!?])\s+(?=[A-Z0-9"“(])""")
    private val PARENS = Regex("""\s*\([^()]*\)""")
    private val DISAMBIG_LEAD = Regex("""\b(may|can|might) (also )?refer to\b""", RegexOption.IGNORE_CASE)

    /** Articles worth asking about: a real intro, and not a list page. */
    fun usable(sources: List<QuizSource>): List<QuizSource> = sources
        .filter { it.extract.length >= MIN_EXTRACT && !it.title.startsWith("List of", ignoreCase = true) }
        // Disambiguation pages ("X may refer to: …") aren't about one thing, so they can't be quizzed.
        .filterNot { isDisambiguation(it) }
        .distinctBy { it.title.lowercase() }

    fun isDisambiguation(source: QuizSource): Boolean =
        source.title.endsWith("(disambiguation)", ignoreCase = true) ||
            source.description?.contains("Topics referred to by the same term", ignoreCase = true) == true ||
            DISAMBIG_LEAD.containsMatchIn(source.extract.take(300))

    fun sentences(extract: String): List<String> =
        PARENS.replace(extract, "").split(SENTENCE_BREAK).map { it.trim() }.filter { it.length >= MIN_SENTENCE }

    /** Picks the clue sentence for [level]; harder levels avoid the giveaway opening line. */
    fun clue(source: QuizSource, level: QuizLevel): String? {
        val s = sentences(source.extract).ifEmpty { return null }
        val raw = when (level.clue) {
            ClueStyle.DESCRIBED -> listOfNotNull(source.description?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() }, s.first())
                .joinToString(". ")
            ClueStyle.LEAD -> s.first()
            ClueStyle.DEEP -> s.getOrNull(1) ?: s.first()
            ClueStyle.OBSCURE -> if (s.size > 1) s.drop(1).minBy { it.length } else s.first()
        }
        val masked = NerdGames.maskAnswer(raw, source.title)
        if (masked.contains(source.title.substringBefore(" (").trim(), ignoreCase = true)) return null
        return masked.takeIf { it.length >= MIN_CLUE }
    }

    /**
     * Turns a pool into questions where **every article appears at most once**: answers come from
     * the front of the shuffled pool, decoys from the back, and nothing in [used] (answers and
     * decoys already shown this session) is reused. So a wrong option is never the answer to an
     * earlier or later question, and vice versa. Adds everything it uses to [used].
     */
    fun round(pool: List<QuizSource>, level: QuizLevel, used: MutableSet<String>, random: Random = Random.Default): List<QuizQuestion> {
        val remaining = pool.filter { it.title.lowercase() !in used }.shuffled(random).toMutableList()
        val out = mutableListOf<QuizQuestion>()
        while (true) {
            val i = remaining.indexOfFirst { clue(it, level) != null }
            if (i < 0) break
            val answer = remaining.removeAt(i)
            if (remaining.size < level.choices - 1) break
            val decoys = List(level.choices - 1) { remaining.removeAt(remaining.lastIndex) }
            out += QuizQuestion(
                clue = clue(answer, level)!!,
                options = (decoys.map { it.title } + answer.title).shuffled(random),
                answer = answer.title,
                factId = "",
            )
            used += answer.title.lowercase()
            decoys.forEach { used += it.title.lowercase() }
        }
        return out
    }

    /**
     * A question about [answer] with decoys from the same [pool] (so they're on-topic), or null if
     * the clue would give it away or there aren't enough decoys for [level].
     */
    fun build(answer: QuizSource, pool: List<QuizSource>, level: QuizLevel, random: Random = Random.Default): QuizQuestion? {
        val clue = clue(answer, level) ?: return null
        val decoys = pool.filter { !it.title.equals(answer.title, ignoreCase = true) }.shuffled(random).take(level.choices - 1)
        if (decoys.size < level.choices - 1) return null
        return QuizQuestion(
            clue = clue,
            options = (decoys.map { it.title } + answer.title).shuffled(random),
            answer = answer.title,
            factId = "",
        )
    }
}

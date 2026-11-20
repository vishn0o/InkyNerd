package com.wikiink.app.data.repository

import com.wikiink.app.data.local.FactEntity
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One quiz question: a clue with its answer hidden, and the article titles to pick from. */
@Serializable
data class QuizQuestion(
    val clue: String,
    val options: List<String>,
    val answer: String,
    val factId: String,
)

/** Pure logic for the reader's Rabbit Hole and Guess It features (unit-tested). */
object NerdGames {

    private val YEARISH = Regex("""^\d{1,4}( (BC|BCE|AD|CE))?$""")
    private val DATEISH = Regex("""^(January|February|March|April|May|June|July|August|September|October|November|December) \d{1,2}$""")
    private val BORING_PREFIXES = listOf("List of", "Lists of", "Index of", "Outline of", "Timeline of", "Glossary of", "Bibliography of")
    private const val MASK = "▇▇▇"

    /** Links worth falling into: no years, bare dates, lists, indexes or the article itself. */
    fun rabbitHoleCandidates(from: String, links: List<String>): List<String> = links.filter { t ->
        t.isNotBlank() &&
            !t.equals(from, ignoreCase = true) &&
            !YEARISH.matches(t) &&
            !DATEISH.matches(t) &&
            BORING_PREFIXES.none { t.startsWith(it, ignoreCase = true) } &&
            !t.contains("(disambiguation)")
    }

    /**
     * Hides the answer in [hook]: the full title, then each distinctive word of it (4+ letters,
     * ignoring a trailing "(qualifier)"), so the clue doesn't give itself away.
     */
    fun maskAnswer(hook: String, title: String): String {
        val core = title.substringBefore(" (").trim()
        var out = hook.replace(Regex(Regex.escape(core), RegexOption.IGNORE_CASE), MASK)
        // Then the name's distinctive words on their own — but only as names (capitalised), and
        // not generic nouns: masking every "building" and "camp" left clues unreadable.
        core.split(' ', ',', '-')
            .filter { it.length >= 4 && it.lowercase() !in GENERIC_NAME_WORDS }
            .forEach { word ->
                val name = word.replaceFirstChar { it.uppercaseChar() }
                out = out.replace(Regex("\\b" + Regex.escape(name) + "\\b"), MASK)
            }
        return out
    }

    private val GENERIC_NAME_WORDS = setOf(
        "building", "buildings", "house", "hall", "tower", "park", "station", "church", "chapel",
        "cathedral", "school", "college", "university", "museum", "hotel", "bridge", "street", "road",
        "avenue", "river", "lake", "mountain", "island", "county", "city", "town", "village", "state",
        "national", "company", "club", "centre", "center", "district", "trail", "camp", "fort",
        "castle", "palace", "square", "garden", "gardens", "theatre", "theater", "library", "airport",
        "railway", "line", "film", "album", "song", "novel", "band", "team", "game", "series",
        // Institutions and countries: shared by countless names, so hiding them hides the clue.
        "united", "states", "kingdom", "republic", "royal", "army", "navy", "force", "command",
        "corps", "department", "ministry", "council", "society", "association", "institute",
        "party", "league", "union", "american", "british", "french", "german", "new", "north",
        "south", "east", "west", "saint",
    )

    /**
     * Builds a question from [deck]: a random fact (not [excludeTitle]) plus two other articles as
     * decoys, preferring decoys of the same subject so it isn't trivially easy. Articles in [used]
     * are skipped entirely. Null if fewer than three distinct articles remain.
     */
    fun quiz(
        deck: List<FactEntity>,
        excludeTitle: String? = null,
        random: Random = Random.Default,
        used: Set<String> = emptySet(),
    ): QuizQuestion? {
        // Articles already shown this session (as answer or option) are never reused.
        val distinct = deck.distinctBy { it.articleTitle.lowercase() }.filter { it.articleTitle.lowercase() !in used }
        if (distinct.size < 3) return null
        val pool = distinct.filter { !it.articleTitle.equals(excludeTitle, ignoreCase = true) }.ifEmpty { distinct }
        val fact = pool[random.nextInt(pool.size)]
        val others = distinct.filter { it.articleTitle != fact.articleTitle }.shuffled(random)
        val decoys = (others.filter { it.subject == fact.subject } + others).distinctBy { it.articleTitle }.take(2)
        if (decoys.size < 2) return null
        return QuizQuestion(
            clue = maskAnswer(fact.hookText, fact.articleTitle),
            options = (decoys.map { it.articleTitle } + fact.articleTitle).shuffled(random),
            answer = fact.articleTitle,
            factId = fact.id,
        )
    }
}

package com.wikiink.app.data.repository

import com.wikiink.app.data.local.FactCategory
import com.wikiink.app.data.local.FactEntity
import com.wikiink.app.data.remote.FeaturedFeedDto
import com.wikiink.app.data.remote.PageSummaryDto
import java.security.MessageDigest

/** A reader section: optional heading followed by plain paragraphs. */
data class ArticleSection(val heading: String?, val paragraphs: List<String>)

/** Pure, Android-free text helpers (unit-tested). */
object WikiText {

    // Long enough that a big widget can show the whole fact; small cards fit/ellipsize on their own.
    const val HOOK_MAX = 320
    const val MIN_RANDOM_EXTRACT = 120
    const val MIN_ODDITY_DESCRIPTION = 20

    private val TAIL_SECTIONS = setOf(
        "references", "external links", "see also", "notes", "further reading",
        "bibliography", "sources", "citations", "footnotes", "notes and references",
        "references and notes", "works cited", "explanatory notes",
    )

    private val TAG = Regex("<[^>]+>")
    private val WS = Regex("\\s+")
    private val BOLD_LINK_TITLE = Regex("<b>\\s*(?:<i>)?\\s*<a[^>]*\\btitle=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val ITALIC_BOLD_LINK_TITLE = Regex("<i>\\s*<b>\\s*<a[^>]*\\btitle=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val LINK_TITLE = Regex("<a[^>]*\\btitle=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val LIST_ITEM = Regex("<li(?:\\s[^>]*)?>(.*?)</li>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TABLE_ROW = Regex("<tr(?:\\s[^>]*)?>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TABLE_CELL = Regex("<t[dh](?:\\s[^>]*)?>(.*?)</t[dh]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val REF_SUP = Regex("<sup(?:\\s[^>]*)?>.*?</sup>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val CITATION = Regex("\\[(?:\\d+|[a-z]|citation needed|note \\d+)]", RegexOption.IGNORE_CASE)
    private val PICTURED = Regex("\\s*\\((?:[^()]*\\s)?pictured\\)", RegexOption.IGNORE_CASE)
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
    private val LEADING_DOTS = Regex("^(?:\\.\\.\\.|…)\\s*")

    fun stripHtml(s: String): String =
        TAG.replace(s, "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .let { decodeNumericEntities(it) }
            .let { WS.replace(it, " ") }
            .trim()

    private fun decodeNumericEntities(s: String): String = NUMERIC_ENTITY.replace(s) { m ->
        val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
        if (code == null || code > 0x10FFFF) m.value else String(Character.toChars(code))
    }

    /** Link title attribute → article title, or null for red links and non-article namespaces. */
    private fun articleTitle(raw: String?): String? {
        val t = raw?.let { stripHtml(it) }?.takeIf { it.isNotBlank() } ?: return null
        if (t.contains("page does not exist")) return null
        if (t.substringBefore(':', "").let { it in NON_ARTICLE_NAMESPACES }) return null
        return t
    }

    private val NON_ARTICLE_NAMESPACES = setOf(
        "File", "Image", "Wikipedia", "Template", "Help", "Category", "Portal", "Special", "Talk", "User", "Draft",
    )

    private fun cleanText(html: String): String =
        stripHtml(REF_SUP.replace(html, "")).let { CITATION.replace(it, "") }.let { WS.replace(it, " ").trim() }

    /**
     * Did-you-know hooks from rendered `Wikipedia:Recent additions` HTML (current page or a monthly
     * archive). Each hook is a `<li>` starting with "... that" whose bolded link is the main article.
     */
    fun dykFromParsedHtml(html: String, baseTime: Long): List<FactEntity> {
        var t = baseTime
        return LIST_ITEM.findAll(html).mapNotNull { m ->
            val inner = m.groupValues[1]
            val text = cleanText(inner)
            if (!LEADING_DOTS.containsMatchIn(text) || !text.contains("that", ignoreCase = true)) return@mapNotNull null
            val title = articleTitle(BOLD_LINK_TITLE.find(inner)?.groupValues?.get(1))
                ?: articleTitle(ITALIC_BOLD_LINK_TITLE.find(inner)?.groupValues?.get(1))
                ?: return@mapNotNull null
            val hook = trimHook("… " + LEADING_DOTS.replace(PICTURED.replace(text, ""), ""))
            fact(FactCategory.DID_YOU_KNOW, title, hook, null, t--)
        }.toList()
    }

    /**
     * Entries from rendered `Wikipedia:Unusual articles` HTML: table rows of
     * [linked article | one-line description]. Header rows and red links are skipped.
     */
    fun odditiesFromParsedHtml(html: String, baseTime: Long): List<FactEntity> {
        var t = baseTime
        return TABLE_ROW.findAll(html).mapNotNull { row ->
            val cells = TABLE_CELL.findAll(row.groupValues[1]).map { it.groupValues[1] }.toList()
            if (cells.size < 2) return@mapNotNull null
            val title = articleTitle(LINK_TITLE.find(cells[0])?.groupValues?.get(1)) ?: return@mapNotNull null
            val name = cleanText(cells[0]).ifBlank { title }
            val description = cleanText(cells[1])
            if (description.length < MIN_ODDITY_DESCRIPTION) return@mapNotNull null
            // Navigation boxes ("Lists of silly things: Content … Deleted articles …") are rows of
            // links, not an entry with a description: skip them.
            if (name.startsWith("List", ignoreCase = true)) return@mapNotNull null
            if (LINK_TITLE.findAll(cells[1]).count() >= 4 && '.' !in description) return@mapNotNull null
            fact(FactCategory.ODDITY, title, trimHook("$name: $description"), null, t--)
        }.toList()
    }

    /**
     * Round-robins facts across categories (in first-seen category order) so the deck alternates
     * trivia types, then restamps `fetchedAt` descending from [newestTime] to lock in that order.
     */
    fun interleave(facts: List<FactEntity>, newestTime: Long): List<FactEntity> {
        val queues = facts.groupBy { it.sourceCategory }.values.map { ArrayDeque(it) }
        val out = ArrayList<FactEntity>(facts.size)
        while (queues.any { it.isNotEmpty() }) queues.forEach { q -> q.removeFirstOrNull()?.let(out::add) }
        return out.mapIndexed { i, f -> f.copy(fetchedAt = newestTime - i) }
    }

    /** Trims to [max] chars on a word boundary, adding an ellipsis when cut. */
    fun trimHook(raw: String, max: Int = HOOK_MAX): String {
        val s = WS.replace(raw, " ").trim()
        if (s.length <= max) return s
        val cut = s.substring(0, max - 1)
        val lastSpace = cut.lastIndexOf(' ')
        val base = if (lastSpace > max / 2) cut.substring(0, lastSpace) else cut
        return base.trimEnd(',', ';', ':', ' ', '.') + "…"
    }

    fun stableId(category: String, title: String, hook: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$category|$title|$hook".toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    /** Maps a featured feed into facts. [baseTime] is decremented per item to preserve feed order. */
    fun factsFromFeed(feed: FeaturedFeedDto, baseTime: Long): List<FactEntity> {
        val out = mutableListOf<FactEntity>()
        var t = baseTime
        feed.dyk.forEach { item ->
            val boldTitle = item.html?.let { BOLD_LINK_TITLE.find(it)?.groupValues?.get(1) }
                ?.let { stripHtml(it) }
            val page = item.articles.firstOrNull()
            val title = boldTitle ?: page?.bestTitle ?: return@forEach
            val hook = trimHook(stripHtml(item.text))
            if (hook.isBlank()) return@forEach
            val matched = item.articles.firstOrNull { it.bestTitle == title } ?: page
            out += fact(FactCategory.DID_YOU_KNOW, title, hook, matched, t--)
        }
        feed.onthisday.forEach { item ->
            val page = item.pages.firstOrNull() ?: return@forEach
            val title = page.bestTitle ?: return@forEach
            val text = stripHtml(item.text)
            if (text.isBlank()) return@forEach
            val hook = trimHook(if (item.year != null) "${item.year}: $text" else text)
            out += fact(FactCategory.ON_THIS_DAY, title, hook, page, t--)
        }
        return out
    }

    /** Returns a RANDOM ARCHIVE fact, or null for disambiguations / stubs. */
    fun factFromRandom(page: PageSummaryDto, time: Long): FactEntity? {
        if (page.type == "disambiguation") return null
        val extract = page.extract?.trim().orEmpty()
        if (extract.length < MIN_RANDOM_EXTRACT) return null
        if (isDullStub(extract)) return null
        val title = page.bestTitle ?: return null
        return fact(FactCategory.RANDOM_ARCHIVE, title, trimHook(tidyLine(extract)), page, time)
    }

    /**
     * Random articles are mostly gazetteer and taxonomy stubs ("X is a village in…", "X is a
     * species of beetle…"): true, but not the niche trivia the widget promises. Skip those leads.
     */
    fun isDullStub(extract: String): Boolean = DULL_STUB.containsMatchIn(extract.take(160))

    private val DULL_STUB = Regex(
        """^[^.]{0,90}?\b(?:is|was|are) (?:an? |the )?(?:[\w-]+ ){0,3}?""" +
            """(?:village|town|city|borough|township|hamlet|commune|municipality|parish|county|district|""" +
            """census-designated place|unincorporated community|neighbourhood|neighborhood|suburb|""" +
            """locality|settlement|river|tributary|mountain|lake|""" +
            """species|genus|subspecies|moth|beetle|snail|plant)s?\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun fact(category: String, title: String, hook: String, page: PageSummaryDto?, time: Long) =
        FactEntity(
            id = stableId(category, title, hook),
            articleTitle = title,
            hookText = hook,
            sourceCategory = category,
            summaryExtract = page?.extract?.trim().orEmpty(),
            fullPlainText = null,
            thumbnailUrl = page?.thumbnail?.source,
            fetchedAt = time,
            isSeen = false,
            subject = Subjects.classify(page?.description, title, hook).name,
        )

    /**
     * Maps each title the caller asked for to its short description, following the API's
     * normalisation and redirect hops back to the requested spelling (keys are lower-cased).
     */
    fun descriptionsByRequestedTitle(body: com.wikiink.app.data.remote.DescriptionQueryBody): Map<String, String> {
        val out = HashMap<String, String>()
        body.pages.forEach { page ->
            val d = page.description?.takeIf { it.isNotBlank() } ?: return@forEach
            val names = mutableSetOf(page.title)
            body.redirects.filter { it.to == page.title }.mapTo(names) { it.from }
            body.normalized.filter { it.to in names }.mapTo(names) { it.from }
            names.forEach { out[it.lowercase()] = d }
        }
        return out
    }

    /**
     * "All topics" ordering: keeps the incoming priority (unseen, newest first) but reorders
     * greedily so no two neighbouring cards share a subject, and where possible not a source
     * either. Deterministic, so a rebuild with the same facts yields the same deck.
     */
    fun spreadForVariety(facts: List<FactEntity>): List<FactEntity> {
        val left = facts.toMutableList()
        val out = ArrayList<FactEntity>(facts.size)
        while (left.isNotEmpty()) {
            val prev = out.lastOrNull()
            val i = if (prev == null) 0 else {
                left.indexOfFirst { it.subject != prev.subject && it.sourceCategory != prev.sourceCategory }
                    .takeIf { it >= 0 }
                    ?: left.indexOfFirst { it.subject != prev.subject }.takeIf { it >= 0 }
                    ?: 0
            }
            out += left.removeAt(i)
        }
        return out
    }

    /** Keeps the first fact per article title (case-insensitive), dropping titles in [existing]. */
    fun dedupe(facts: List<FactEntity>, existing: Collection<String> = emptyList()): List<FactEntity> {
        val seen = existing.mapTo(HashSet()) { it.lowercase() }
        return facts.filter { seen.add(it.articleTitle.lowercase()) }
    }

    /**
     * Parses MediaWiki `explaintext` + `exsectionformat=plain` output. Section headings appear as a
     * lone line preceded by a blank-line triple (`\n\n\n`). Stops at reference-style tail sections.
     */
    fun parseSections(plain: String): List<ArticleSection> {
        val blocks = plain.replace("\r\n", "\n").split(Regex("\n{3,}"))
        val sections = mutableListOf<ArticleSection>()
        blocks.forEachIndexed { index, block ->
            val lines = block.split('\n').map { tidyLine(it) }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return@forEachIndexed
            if (index == 0) {
                sections += ArticleSection(null, lines)
            } else {
                val heading = lines.first()
                if (heading.lowercase() in TAIL_SECTIONS) return sections.dropEmptyTail()
                sections += ArticleSection(heading, lines.drop(1))
            }
        }
        return sections.dropEmptyTail()
    }

    /**
     * Plain-text extracts drop pronunciation/IPA markup but keep its brackets, leaving
     * "Kirkification ( ) is…" or "(; born 1929)". Remove the husks.
     */
    fun tidyLine(raw: String): String = raw.trim()
        .replace(EMPTY_PARENS, "")
        .replace(LEADING_SEMI_PARENS, "(")
        .replace(SPACE_BEFORE_PUNCT, "$1")
        .replace(WS, " ")
        .trim()

    private val EMPTY_PARENS = Regex("""\s*\(\s*[;,]?\s*\)""")
    private val LEADING_SEMI_PARENS = Regex("""\(\s*[;,]\s*""")
    private val SPACE_BEFORE_PUNCT = Regex("""\s+([,.;:])""")

    private fun List<ArticleSection>.dropEmptyTail(): List<ArticleSection> =
        dropLastWhile { it.paragraphs.isEmpty() }
}

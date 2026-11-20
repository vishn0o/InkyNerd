package com.wikiink.app.data.repository

import com.wikiink.app.data.local.FactCategory
import com.wikiink.app.data.local.FactEntity
import com.wikiink.app.data.remote.DykItemDto
import com.wikiink.app.data.remote.ExtractQueryDto
import com.wikiink.app.data.remote.FeaturedFeedDto
import com.wikiink.app.data.remote.PageSummaryDto
import com.wikiink.app.data.remote.TitlesDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiTextTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    @Test
    fun trimHook_keepsShortText() {
        assertEquals("Short hook.", WikiText.trimHook("  Short   hook. "))
    }

    @Test
    fun trimHook_cutsOnWordBoundaryWithEllipsis() {
        val long = (1..60).joinToString(" ") { "word$it" }
        val hook = WikiText.trimHook(long)
        assertTrue(hook.length <= WikiText.HOOK_MAX)
        assertTrue(hook.endsWith("…"))
        assertFalse(hook.removeSuffix("…").endsWith(" "))
    }

    @Test
    fun feed_realResponse_mapsOnThisDay() {
        val feed = json.decodeFromString<FeaturedFeedDto>(resource("featured_feed.json"))
        val facts = WikiText.factsFromFeed(feed, baseTime = 1_000_000L)
        val otd = facts.filter { it.sourceCategory == FactCategory.ON_THIS_DAY }
        assertTrue("expected on-this-day facts", otd.isNotEmpty())
        otd.forEach {
            assertTrue(it.hookText.matches(Regex("^-?\\d+: .+")))
            assertTrue(it.hookText.length <= WikiText.HOOK_MAX)
            assertTrue(it.articleTitle.isNotBlank())
            assertFalse(it.articleTitle.contains('_'))
        }
        assertEquals(facts.size, facts.map { it.id }.toSet().size)
    }

    @Test
    fun dyk_usesBoldLinkedArticleFromHtml() {
        val feed = FeaturedFeedDto(
            dyk = listOf(
                DykItemDto(
                    text = "... that the Bloop was loud?",
                    html = "... that the <b><a href=\"./Bloop\" title=\"Bloop\">Bloop</a></b> was <a title=\"NOAA\">loud</a>?",
                    articles = listOf(
                        PageSummaryDto(titles = TitlesDto(normalized = "NOAA")),
                        PageSummaryDto(titles = TitlesDto(normalized = "Bloop"), extract = "x"),
                    ),
                ),
            ),
        )
        val fact = WikiText.factsFromFeed(feed, 0L).single()
        assertEquals("Bloop", fact.articleTitle)
        assertEquals(FactCategory.DID_YOU_KNOW, fact.sourceCategory)
        assertEquals("x", fact.summaryExtract)
    }

    @Test
    fun dyk_fallsBackToFirstArticle() {
        val feed = FeaturedFeedDto(
            dyk = listOf(DykItemDto(text = "... that X?", articles = listOf(PageSummaryDto(normalizedtitle = "X thing")))),
        )
        assertEquals("X thing", WikiText.factsFromFeed(feed, 0L).single().articleTitle)
    }

    @Test
    fun random_filtersDisambiguationAndStubs() {
        val longExtract = "a".repeat(WikiText.MIN_RANDOM_EXTRACT)
        assertNull(WikiText.factFromRandom(PageSummaryDto(type = "disambiguation", title = "A", extract = longExtract), 0))
        assertNull(WikiText.factFromRandom(PageSummaryDto(type = "standard", title = "A", extract = "too short"), 0))
        val ok = WikiText.factFromRandom(PageSummaryDto(type = "standard", title = "Some_Page", extract = longExtract), 0)
        assertNotNull(ok)
        assertEquals("Some Page", ok!!.articleTitle)
        assertEquals(FactCategory.RANDOM_ARCHIVE, ok.sourceCategory)
    }

    @Test
    fun dedupe_isCaseInsensitiveAndRespectsExisting() {
        val a = WikiText.factFromRandom(PageSummaryDto(title = "Alpha", extract = "a".repeat(200)), 0)!!
        val a2 = a.copy(id = "other", articleTitle = "ALPHA")
        val b = a.copy(id = "b", articleTitle = "Beta")
        assertEquals(listOf(a, b), WikiText.dedupe(listOf(a, a2, b)))
        assertEquals(listOf(b), WikiText.dedupe(listOf(a, b), existing = listOf("alpha")))
    }

    @Test
    fun parseSections_realExtract_splitsHeadingsAndDropsTail() {
        val dto = json.decodeFromString<ExtractQueryDto>(resource("extract_bloop.json"))
        val text = dto.query!!.pages.first().extract!!
        val sections = WikiText.parseSections(text)
        assertNull(sections.first().heading)
        assertTrue(sections.first().paragraphs.first().startsWith("Bloop"))
        val headings = sections.mapNotNull { it.heading }
        assertTrue("headings: $headings", "Sound profile" in headings)
        assertFalse(headings.any { it.equals("References", true) || it.equals("External links", true) })
    }

    @Test
    fun parseSections_handlesNoHeadings() {
        val s = WikiText.parseSections("Para one.\nPara two.")
        assertEquals(1, s.size)
        assertEquals(listOf("Para one.", "Para two."), s[0].paragraphs)
    }

    @Test
    fun dykArchive_parsesHooksAndBoldArticle() {
        val html = """
            <link rel="mw-deduplicated-inline-style" href="x"/>
            <ul>
            <li>... that <b><a href="/wiki/Punggol_station" title="Punggol station">Punggol station</a></b> (pictured) is the longest station on its line?<sup class="reference">[1]</sup></li>
            <li>... that the <i><b><a href="/wiki/Bloop" title="Bloop">Bloop</a></b></i> was an icequake?</li>
            <li>... that <a href="/wiki/X" title="X">an unbolded link</a> has no main article?</li>
            <li>Not a hook at all</li>
            <li>... that <b><a href="/w/index.php?title=Red&amp;action=edit" title="Red (page does not exist)">Red</a></b> is missing?</li>
            </ul>
        """.trimIndent()
        val facts = WikiText.dykFromParsedHtml(html, 100L)
        assertEquals(listOf("Punggol station", "Bloop"), facts.map { it.articleTitle })
        assertTrue(facts.all { it.sourceCategory == FactCategory.DID_YOU_KNOW })
        assertEquals("… that Punggol station is the longest station on its line?", facts[0].hookText)
        assertEquals(listOf(100L, 99L), facts.map { it.fetchedAt })
    }

    @Test
    fun unusualArticles_parsesTableRowsAndSkipsHeaders() {
        val html = """
            <table class="wikitable"><thead><tr><th>Article</th><th>Summary</th></tr></thead><tbody>
            <tr><td><a href="/wiki/Boring,_Oregon" title="Boring, Oregon">Boring, Oregon</a></td><td>An American town &#8212; paired with Dull, Scotland.<sup>[2]</sup></td></tr>
            <tr><td><a href="/wiki/Tiny" title="Tiny">Tiny</a></td><td>Too short</td></tr>
            <tr><td>No link here</td><td>A description that is certainly long enough.</td></tr>
            </tbody></table>
        """.trimIndent()
        val fact = WikiText.odditiesFromParsedHtml(html, 0L).single()
        assertEquals("Boring, Oregon", fact.articleTitle)
        assertEquals(FactCategory.ODDITY, fact.sourceCategory)
        assertEquals("Boring, Oregon: An American town — paired with Dull, Scotland.", fact.hookText)
    }

    @Test
    fun interleave_alternatesCategoriesAndRestampsTime() {
        fun f(cat: String, title: String) = FactEntity(
            id = title, articleTitle = title, hookText = title, sourceCategory = cat,
            summaryExtract = "", fullPlainText = null, thumbnailUrl = null, fetchedAt = 0L,
        )
        val input = listOf(
            f(FactCategory.ON_THIS_DAY, "a"), f(FactCategory.ON_THIS_DAY, "b"), f(FactCategory.ON_THIS_DAY, "c"),
            f(FactCategory.DID_YOU_KNOW, "d"), f(FactCategory.ODDITY, "e"),
        )
        val out = WikiText.interleave(input, 50L)
        assertEquals(listOf("a", "d", "e", "b", "c"), out.map { it.articleTitle })
        assertEquals(listOf(50L, 49L, 48L, 47L, 46L), out.map { it.fetchedAt })
    }

    @Test
    fun spreadForVariety_avoidsNeighboursWithTheSameSubject() {
        fun f(id: String, subject: String, cat: String = FactCategory.DID_YOU_KNOW) = FactEntity(
            id = id, articleTitle = id, hookText = id, sourceCategory = cat,
            summaryExtract = "", fullPlainText = null, thumbnailUrl = null, fetchedAt = 0L, subject = subject,
        )
        val input = listOf(f("a", "SPACE"), f("b", "SPACE"), f("c", "SPACE"), f("d", "FOOD"), f("e", "MYTH", FactCategory.ODDITY))
        val out = WikiText.spreadForVariety(input)
        assertEquals(listOf("a", "e", "b", "d", "c"), out.map { it.id })
        assertEquals(input.toSet(), out.toSet())
    }

    @Test
    fun dullGazetteerAndTaxonomyStubsAreSkipped() {
        assertTrue(WikiText.isDullStub("Wildwood Crest is a borough in Cape May County, in the U.S. state of New Jersey."))
        assertTrue(WikiText.isDullStub("Microlera yayeyamensis is a species of beetle in the family Cerambycidae."))
        assertTrue(WikiText.isDullStub("Kushchyovsky is a rural locality in Krasnodar Krai, Russia."))
        assertTrue(WikiText.isDullStub("North Okaw Township is one of twelve townships in Coles County, Illinois."))
        assertFalse(WikiText.isDullStub("La Gruta is a Mexican restaurant located inside a natural volcanic cave."))
        assertFalse(WikiText.isDullStub("The VAXstation is a discontinued family of workstation computers."))
    }

    @Test
    fun tidyLineRemovesEmptyPronunciationBrackets() {
        assertEquals("Kirkification is an internet meme", WikiText.tidyLine("Kirkification ( ) is an internet meme"))
        assertEquals("Hit (born 1929) is a village.", WikiText.tidyLine("Hit (; born 1929) is a village ."))
        assertEquals("Lelchuk (Russian: X; born 1929) is", WikiText.tidyLine("Lelchuk (Russian: X; born 1929) is"))
        assertEquals("Historic site in Brighton and Hove, United Kingdom", WikiText.tidyLine("Historic site in Brighton and Hove , United Kingdom"))
    }
}

package com.wikiink.app.data.repository

import com.wikiink.app.data.local.FactCategory
import com.wikiink.app.data.local.FactEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NerdGamesTest {

    private fun f(title: String, hook: String = "… that $title is odd?", subject: String = "MISC") = FactEntity(
        id = title, articleTitle = title, hookText = hook, sourceCategory = FactCategory.DID_YOU_KNOW,
        summaryExtract = "", fullPlainText = null, thumbnailUrl = null, fetchedAt = 0L, subject = subject,
    )

    @Test
    fun rabbitHoleSkipsYearsDatesListsAndSelf() {
        val links = listOf("1917", "300 BC", "March 4", "List of beetles", "Bloop", "Icequake", "Bloop (disambiguation)", "Hydrophone")
        assertEquals(listOf("Icequake", "Hydrophone"), NerdGames.rabbitHoleCandidates("Bloop", links))
    }

    @Test
    fun maskHidesTitleAndItsDistinctiveWords() {
        val masked = NerdGames.maskAnswer("… that Punggol station is the longest, and Punggol is new?", "Punggol station (MRT)")
        assertFalse(masked.contains("Punggol", ignoreCase = true))
        assertFalse(masked.contains("station", ignoreCase = true))
        assertTrue(masked.contains("▇▇▇"))
        assertTrue(masked.contains("longest"))
    }

    @Test
    fun maskKeepsGenericWordsReadable() {
        val masked = NerdGames.maskAnswer(
            "The Camp Lincoln Commissary Building is a historic building on the grounds of Camp Lincoln.",
            "Camp Lincoln Commissary Building",
        )
        assertFalse(masked.contains("Lincoln"))
        assertFalse(masked.contains("Commissary"))
        assertTrue(masked.contains("historic building"))
    }

    @Test
    fun quizHasThreeDistinctOptionsIncludingTheAnswer() {
        val deck = listOf(f("Bloop", subject = "EARTH"), f("Bunyip", subject = "MYTH"), f("Haggis", subject = "FOOD"), f("Kraken", subject = "MYTH"))
        repeat(20) { seed ->
            val q = assertNotNullAndGet(NerdGames.quiz(deck, random = Random(seed)))
            assertEquals(3, q.options.toSet().size)
            assertTrue(q.answer in q.options)
            assertFalse(q.clue.contains(q.answer, ignoreCase = true))
        }
    }

    @Test
    fun quizNeedsThreeArticlesAndRespectsExclude() {
        assertNull(NerdGames.quiz(listOf(f("A1aa"), f("B2bb"))))
        val deck = listOf(f("Alpha"), f("Bravo"), f("Charlie"))
        repeat(10) { seed ->
            assertTrue(NerdGames.quiz(deck, excludeTitle = "Alpha", random = Random(seed))!!.answer != "Alpha")
        }
    }

    private fun <T> assertNotNullAndGet(v: T?): T {
        assertNotNull(v)
        return v!!
    }

    @Test
    fun quickQuizSkipsArticlesAlreadyShown() {
        val deck = listOf(f("Alpha"), f("Bravo"), f("Charlie"), f("Delta"), f("Echo"), f("Foxtrot"))
        val used = mutableSetOf<String>()
        val first = NerdGames.quiz(deck, random = Random(4), used = used)!!
        used += first.options.map { it.lowercase() }
        val second = NerdGames.quiz(deck, random = Random(5), used = used)!!
        assertTrue(second.options.none { it.lowercase() in first.options.map(String::lowercase) })
        used += second.options.map { it.lowercase() }
        assertNull(NerdGames.quiz(deck, used = used))
    }
}

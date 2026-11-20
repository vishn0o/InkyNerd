package com.wikiink.app.data.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizTest {

    private fun src(title: String, extract: String, description: String? = "Something") = QuizSource(title, description, extract)

    private val pool = listOf(
        src("Monaco Grand Prix", "The Monaco Grand Prix is a Formula One motor race held each year on the Circuit de Monaco. It has been run since 1929 and is widely considered one of the most prestigious races. Overtaking is famously difficult on its narrow streets."),
        src("Ayrton Senna", "Ayrton Senna was a Brazilian racing driver who won three Formula One world titles. He is regarded as one of the greatest drivers in the history of the sport. Senna won the Monaco race a record six times."),
        src("Pit stop", "In motorsport, a pit stop is a pause for refuelling, new tyres, repairs or driver changes. Modern stops in top-tier racing can take under two seconds to complete."),
        src("Scuderia Ferrari", "Scuderia Ferrari is the racing division of the luxury Italian car maker Ferrari. It is the oldest surviving and most successful team in Formula One history."),
        src("Safety car", "A safety car is a car which limits the speed of competing cars on a racetrack in the case of a caution period. It is deployed after crashes or when debris is on the track surface."),
        src("Drag reduction system", "The drag reduction system is a form of driver-adjustable bodywork aimed at reducing aerodynamic drag in order to increase top speed and promote overtaking."),
    )

    @Test
    fun topicTreeIdsAreUniqueAndPathsResolve() {
        val ids = QuizTopics.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val f1 = QuizTopics.find("f1")
        assertEquals(listOf("auto", "sports", "motorsport", "f1"), QuizTopics.path(f1).map { it.id })
        assertEquals("Motorsport", QuizTopics.parent(f1)?.label)
        assertEquals(QuizTopics.AUTO, QuizTopics.find("nope"))
        assertEquals("Astrophysics", QuizTopics.find("astrophysics").category)
    }

    @Test
    fun autoDrawsAnythingAndBroadTopicsDrawFromTheirSubtree() {
        assertNull(QuizTopics.drawCategory(QuizTopics.AUTO) { 0 })
        val motorsport = QuizTopics.find("motorsport")
        val drawn = (0 until 5).map { i -> QuizTopics.drawCategory(motorsport) { i % it } }.toSet()
        assertTrue(drawn.all { it in setOf("Motorsport", "Formula One", "Grand Prix motorcycle racing", "Rallying", "NASCAR") })
    }

    @Test
    fun everyLevelBuildsAQuestionWithTheRightNumberOfChoices() {
        QuizLevel.entries.forEach { level ->
            val q = QuizBuilder.build(pool[1], pool, level, Random(3))
            assertNotNull("$level", q)
            assertEquals(level.choices, q!!.options.size)
            assertTrue(q.answer in q.options)
            assertFalse("$level clue leaks: ${q.clue}", q.clue.contains("Senna", ignoreCase = true))
        }
    }

    @Test
    fun harderLevelsAvoidTheOpeningLine() {
        val geek = QuizBuilder.clue(pool[0], QuizLevel.GEEK)!!
        val brainiac = QuizBuilder.clue(pool[0], QuizLevel.BRAINIAC)!!
        assertTrue(geek.contains("Circuit de"))
        assertFalse(brainiac.contains("Circuit de"))
        assertTrue(QuizBuilder.clue(pool[0], QuizLevel.CURIOUS)!!.startsWith("Something"))
    }

    @Test
    fun scoringAndFormatting() {
        assertEquals(1.0, QuizLevel.NERD.delta(true), 0.0)
        assertEquals(-0.25, QuizLevel.NERD.delta(false), 0.0)
        assertEquals(0.0, QuizLevel.GEEK.delta(false), 0.0)
        assertFalse(QuizLevel.NERD.resettable)
        assertTrue(QuizLevel.entries.filter { it != QuizLevel.NERD }.all { it.resettable })
        assertEquals("+7", formatPoints(7.0))
        assertEquals("−2.25", formatPoints(-2.25))
        assertEquals("+0.75", formatPoints(0.75))
        assertEquals("0", formatPoints(0.0))
        assertEquals("5", formatPoints(5.0, signed = false))
    }

    @Test
    fun usableDropsListsShortIntrosAndDuplicates() {
        val raw = pool + src("List of F1 drivers", pool[0].extract) + src("Stub", "Too short.") + pool[2]
        assertEquals(pool.size, QuizBuilder.usable(raw).size)
    }

    @Test
    fun roundNeverReusesAnArticleAsAnswerOrOption() {
        val big = (1..20).map { n ->
            src("Driver Number$n", "Driver Number$n was a racing driver from the country of Somewhere$n. They won several races in the early years of their long career on track.")
        }
        val used = mutableSetOf<String>()
        val first = QuizBuilder.round(big, QuizLevel.GEEK, used, Random(1))
        val second = QuizBuilder.round(big, QuizLevel.GEEK, used, Random(2))
        val all = (first + second).flatMap { it.options }
        assertEquals("an article appeared twice: $all", all.size, all.map { it.lowercase() }.toSet().size)
        val answers = (first + second).map { it.answer }.toSet()
        (first + second).forEach { q -> assertTrue(q.options.filter { it != q.answer }.none { it in answers }) }
        assertTrue(first.isNotEmpty())
        assertTrue(second.isEmpty()) // pool exhausted: nothing reused
    }

    @Test
    fun disambiguationPagesAreNotQuizzed() {
        val long = " Plenty of further text so the extract clears the minimum length for a usable intro."
        val pool = listOf(
            QuizSource("160th", "Topics referred to by the same term", "160th may refer to: 160th Regiment, 160th Brigade.$long"),
            QuizSource("Mercury (disambiguation)", null, "Mercury is a planet, an element and a god.$long"),
            QuizSource("Bermen", null, "Bermen can refer to several villages in Belgium and the Netherlands.$long"),
            QuizSource("Value per action", "Online marketing model", "Value per action is an online marketing business model.$long"),
        )
        assertEquals(listOf("Value per action"), QuizBuilder.usable(pool).map { it.title })
    }
}

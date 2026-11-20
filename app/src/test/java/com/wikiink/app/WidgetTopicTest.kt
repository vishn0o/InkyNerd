package com.wikiink.app

import com.wikiink.app.data.local.FactCategory
import com.wikiink.app.data.repository.Subject
import com.wikiink.app.widget.WidgetTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTopicTest {

    private val categories = mapOf(FactCategory.DID_YOU_KNOW to 10, FactCategory.ON_THIS_DAY to 4)
    private val subjects = mapOf(Subject.SPACE.name to 3, Subject.FOOD.name to 2)

    @Test
    fun cyclesAllThenSourcesThenSubjectsAndWraps() {
        var t = WidgetTopic.ALL
        val seen = mutableListOf<String>()
        repeat(5) { t = WidgetTopic.next(t, categories, subjects); seen += t.key }
        assertEquals(listOf("SRC_DYK", "SRC_OTD", "SUB_SPACE", "SUB_FOOD", "ALL"), seen)
    }

    @Test
    fun everySubjectIsATopic() {
        val keys = WidgetTopic.entries.map { it.key }.toSet()
        assertTrue(Subject.entries.all { "SUB_${it.name}" in keys })
    }

    @Test
    fun emptyCacheAndStaleTopicsFallBackToAll() {
        assertEquals(WidgetTopic.ALL, WidgetTopic.next(WidgetTopic.ALL, emptyMap(), emptyMap()))
        assertEquals(WidgetTopic.ALL, WidgetTopic.next(WidgetTopic.parse("SUB_MUSIC"), emptyMap(), emptyMap()))
        assertEquals(WidgetTopic.ALL, WidgetTopic.parse("not-a-topic"))
    }
}

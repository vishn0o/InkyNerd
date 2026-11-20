package com.wikiink.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectsTest {

    private fun of(description: String?, title: String = "X", hook: String = "") =
        Subjects.classify(description, title, hook)

    @Test
    fun descriptionsMapToSubjects() {
        assertEquals(Subject.ANIMALS, of("Species of deep-sea anglerfish"))
        assertEquals(Subject.PLANTS, of("Species of flowering plant"))
        assertEquals(Subject.SPACE, of("Planet orbiting Proxima Centauri"))
        assertEquals(Subject.GAMES, of("1998 video game"))
        assertEquals(Subject.FOOD, of("Traditional Scottish dish"))
        assertEquals(Subject.MILITARY, of("British warship"))
        assertEquals(Subject.TRANSPORT, of("Railway station in Singapore"))
        assertEquals(Subject.SPORT, of("English footballer (born 1990)"))
        assertEquals(Subject.HISTORY, of("Roman emperor from 98 to 117"))
        assertEquals(Subject.PLACES, of("Town in Oregon, United States"))
    }

    @Test
    fun wordStartsOnlySoArtDoesNotMatchStart() {
        assertEquals(Subject.MISC, of("A starting point"))
        assertEquals(Subject.SCREEN, of("American film star"))
    }

    @Test
    fun fallsBackToTitleAndHookThenMisc() {
        assertEquals(Subject.MYTH, of(null, "Bunyip", "… that the bunyip is a creature from Aboriginal folklore?"))
        assertEquals(Subject.MISC, of(null, "Bloop", "… that the Bloop was an icequake?"))
    }

    @Test
    fun attacksAreConflictNotTheActorTheyMention() {
        assertEquals(
            Subject.MILITARY,
            Subjects.classify(null, "2003 Istanbul bombings", "2003: Suicide bombers blew up the British consulate, killing 31 people, including actor Kerem Yılmazer."),
        )
    }
}

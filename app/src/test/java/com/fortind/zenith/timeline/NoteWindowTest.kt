package com.fortind.zenith.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NoteWindowTest {

    private val notes = (5 downTo 1).map { testNote("n$it") }

    @Test
    fun trimmingTheNewestEndKeepsTheOldestNotes() {
        assertEquals(listOf("n3", "n2", "n1"), trimNewest(notes, 3).map { it.id })
    }

    @Test
    fun trimmingTheOldestEndKeepsTheNewestNotes() {
        assertEquals(listOf("n5", "n4", "n3"), trimOldest(notes, 3).map { it.id })
    }

    @Test
    fun aListWithinTheWindowIsReturnedAsIs() {
        assertSame(notes, trimNewest(notes, 5))
        assertSame(notes, trimOldest(notes, 10))
    }
}

package com.fortind.zenith.mfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpoilerStandInTest {

    private val label = "Hidden text"
    private val filler = ' '

    @Test
    fun neverContainsTheHiddenText() {
        val standIn = spoilerStandIn("the butler did it", label)
        assertFalse(standIn.contains("butler"))
        assertEquals(label + filler.toString().repeat("the butler did it".length - label.length), standIn)
    }

    @Test
    fun shortTextStillReadsAsTheLabel() {
        assertEquals(label, spoilerStandIn("no", label))
    }

    @Test
    fun keepsLineBreaksSoEachLineStaysABox() {
        val hidden = "first line here\nsecond line here"
        val standIn = spoilerStandIn(hidden, label)
        assertEquals(hidden.count { it == '\n' }, standIn.count { it == '\n' })
        assertEquals(hidden.length, standIn.length)
        assertFalse(standIn.contains("second"))
    }

    @Test
    fun countsCodePointsNotUtf16Units() {
        val hidden = "😀😀😀😀😀😀😀😀😀😀😀😀"
        val standIn = spoilerStandIn(hidden, label)
        assertEquals(label + filler, standIn)
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", spoilerStandIn("", label))
        assertEquals("  ", spoilerStandIn("  ", label))
    }
}

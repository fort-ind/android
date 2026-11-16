package com.fortind.zenith.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeSelectionTest {
    private val items = listOf("a", "b", "c", "d", "e")

    private fun select(selected: List<String>, anchor: String?, target: String, slots: Int = 16, blocked: Set<String> = emptySet()) =
        rangeSelection(items, { it }, selected, anchor, target, slots, blocked)

    @Test
    fun shiftClickAddsEverythingBetweenTheAnchorAndTheTarget() {
        assertEquals(listOf("b", "c", "d"), select(listOf("b"), anchor = "b", target = "d"))
    }

    @Test
    fun worksBackwards() {
        assertEquals(listOf("d", "b", "c"), select(listOf("d"), anchor = "d", target = "b"))
    }

    @Test
    fun withoutAnAnchorOnlyTheTargetIsAdded() {
        assertEquals(listOf("c"), select(emptyList(), anchor = null, target = "c"))
    }

    @Test
    fun respectsFreeSlotsAndAttachedFiles() {
        assertEquals(listOf("a", "c"), select(listOf("a"), anchor = "a", target = "e", slots = 2, blocked = setOf("b")))
    }
}

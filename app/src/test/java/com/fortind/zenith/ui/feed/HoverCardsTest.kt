package com.fortind.zenith.ui.feed

import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HoverCardsTest {
    private val anchor = IntRect(10, 20, 50, 60)
    private val target = HoverTarget.Reactors(ReactorsKey("n1", "🎉", 3))
    private val other = HoverTarget.Reactors(ReactorsKey("n2", "❤", 1))

    @Test
    fun aCardShowsOnlyAfterTheMouseRests() = runTest {
        val cards = HoverCards(backgroundScope)
        cards.anchorEntered(target, anchor)
        advanceTimeBy(HOVER_CARD_SHOW_MS - 1)
        assertNull(cards.shown)
        advanceTimeBy(2)
        assertEquals(ShownHoverCard(target, anchor), cards.shown)
    }

    @Test
    fun leavingBeforeTheDelayShowsNothing() = runTest {
        val cards = HoverCards(backgroundScope)
        cards.anchorEntered(target, anchor)
        advanceTimeBy(HOVER_CARD_SHOW_MS / 2)
        cards.anchorExited(target)
        advanceTimeBy(HOVER_CARD_SHOW_MS)
        assertNull(cards.shown)
    }

    @Test
    fun movingIntoTheCardKeepsItOpen() = runTest {
        val cards = HoverCards(backgroundScope)
        cards.anchorEntered(target, anchor)
        advanceTimeBy(HOVER_CARD_SHOW_MS + 1)
        cards.anchorExited(target)
        advanceTimeBy(HOVER_CARD_HIDE_MS / 2)
        cards.cardHovered(true)
        advanceTimeBy(HOVER_CARD_HIDE_MS * 2)
        assertTrue(cards.shown != null)

        cards.cardHovered(false)
        advanceTimeBy(HOVER_CARD_HIDE_MS + 1)
        assertNull(cards.shown)
    }

    @Test
    fun anotherAnchorReplacesTheCardAfterItsOwnDelay() = runTest {
        val cards = HoverCards(backgroundScope)
        cards.anchorEntered(target, anchor)
        advanceTimeBy(HOVER_CARD_SHOW_MS + 1)
        cards.anchorExited(target)
        cards.anchorEntered(other, anchor)
        advanceTimeBy(HOVER_CARD_SHOW_MS + 1)
        assertEquals(other, cards.shown?.target)
    }

    @Test
    fun aPressDismissesEverything() = runTest {
        val cards = HoverCards(backgroundScope)
        cards.anchorEntered(target, anchor)
        cards.dismiss()
        advanceTimeBy(HOVER_CARD_SHOW_MS * 2)
        runCurrent()
        assertNull(cards.shown)
    }

    @Test
    fun reactorPreviewsAreLoadedOnceUnlessTheyFailed() {
        val key = ReactorsKey("n1", "🎉", 3)
        assertTrue(emptyMap<ReactorsKey, ReactorsPreview>().needsLoad(key))
        assertFalse(mapOf(key to ReactorsPreview(loading = true)).needsLoad(key))
        assertTrue(mapOf(key to ReactorsPreview(failed = true)).needsLoad(key))
    }

    @Test
    fun theReactorCacheKeepsOneCountPerReactionAndStaysBounded() {
        var cache = emptyMap<ReactorsKey, ReactorsPreview>()
        cache = cache.withPreview(ReactorsKey("n1", "🎉", 3), ReactorsPreview())
        cache = cache.withPreview(ReactorsKey("n1", "🎉", 4), ReactorsPreview())
        assertEquals(listOf(4), cache.keys.map { it.count })
        repeat(REACTORS_CACHE_SIZE + 5) { cache = cache.withPreview(ReactorsKey("note$it", "❤", 1), ReactorsPreview()) }
        assertEquals(REACTORS_CACHE_SIZE, cache.size)
        assertEquals("note${REACTORS_CACHE_SIZE + 4}", cache.keys.last().noteId)
    }
}

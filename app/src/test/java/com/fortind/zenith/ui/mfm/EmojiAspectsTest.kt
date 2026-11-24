package com.fortind.zenith.ui.mfm

import androidx.compose.runtime.snapshots.Snapshot
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiAspectsTest {

    private fun freshUrl(): String = "https://x/emoji/${counter.incrementAndGet()}.webp"

    private fun readsDuring(block: () -> Unit): Set<Any> {
        val read = mutableSetOf<Any>()
        val snapshot = Snapshot.takeSnapshot(readObserver = { read += it })
        try {
            snapshot.enter(block)
        } finally {
            snapshot.dispose()
        }
        return read
    }

    private fun changesDuring(block: () -> Unit): Set<Any> {
        val changed = mutableSetOf<Any>()
        val handle = Snapshot.registerApplyObserver { objects, _ -> changed.addAll(objects) }
        try {
            block()
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        return changed
    }

    @Test
    fun anUnknownEmojiIsSquareUntilAWideOneIsRecorded() {
        val url = freshUrl()
        assertEquals(1f, EmojiAspects.ratio(url))

        EmojiAspects.record(url, 3f)

        assertEquals(3f, EmojiAspects.ratio(url))
    }

    @Test
    fun aNearlySquareOrInvalidRecordChangesNothing() {
        val url = freshUrl()

        EmojiAspects.record(url, 1.03f)
        EmojiAspects.record(url, 0f)
        EmojiAspects.record(url, -2f)
        EmojiAspects.record(url, Float.NaN)

        assertEquals(1f, EmojiAspects.ratio(url))
    }

    @Test
    fun recordingOneEmojiDoesNotInvalidateTextThatReadsAnother() {
        val shown = freshUrl()
        val other = freshUrl()
        EmojiAspects.ratio(other)
        val read = readsDuring { EmojiAspects.ratio(shown) }

        val otherChanged = changesDuring { EmojiAspects.record(other, 2f) }
        val shownChanged = changesDuring { EmojiAspects.record(shown, 2f) }

        assertTrue(otherChanged.isNotEmpty())
        assertTrue(read.intersect(otherChanged).isEmpty())
        assertTrue(read.intersect(shownChanged).isNotEmpty())
    }

    @Test
    fun aRatioFirstReadInsideASnapshotStaysReadableOutsideIt() {
        val url = freshUrl()
        readsDuring { EmojiAspects.ratio(url) }

        EmojiAspects.record(url, 4f)

        assertEquals(4f, EmojiAspects.ratio(url))
    }

    private companion object {
        val counter = AtomicInteger()
    }
}

package com.fortind.zenith.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollbarGeometryTest {

    @Test
    fun noThumbWhenContentFits() {
        assertNull(scrollbarThumb(ScrollMetrics(offset = 0f, content = 500f, viewport = 500f), trackLength = 500f, minLength = 32f))
    }

    @Test
    fun thumbLengthFollowsViewportShare() {
        val thumb = scrollbarThumb(ScrollMetrics(offset = 0f, content = 2000f, viewport = 500f), trackLength = 500f, minLength = 32f)!!
        assertEquals(125f, thumb.length, 0.01f)
        assertEquals(0f, thumb.start, 0.01f)
    }

    @Test
    fun thumbReachesTheEndAtMaximumOffset() {
        val thumb = scrollbarThumb(ScrollMetrics(offset = 1500f, content = 2000f, viewport = 500f), trackLength = 500f, minLength = 32f)!!
        assertEquals(375f, thumb.start, 0.01f)
    }

    @Test
    fun longContentKeepsTheMinimumThumb() {
        val thumb = scrollbarThumb(ScrollMetrics(offset = 0f, content = 1_000_000f, viewport = 500f), trackLength = 500f, minLength = 32f)!!
        assertEquals(32f, thumb.length, 0.01f)
    }

    @Test
    fun draggingTheThumbMapsBackToTheOffset() {
        val metrics = ScrollMetrics(offset = 0f, content = 2000f, viewport = 500f)
        assertEquals(750f, scrollOffsetForThumb(start = 187.5f, thumbLength = 125f, trackLength = 500f, metrics = metrics), 0.01f)
        assertEquals(0f, scrollOffsetForThumb(start = -40f, thumbLength = 125f, trackLength = 500f, metrics = metrics), 0.01f)
        assertEquals(1500f, scrollOffsetForThumb(start = 900f, thumbLength = 125f, trackLength = 500f, metrics = metrics), 0.01f)
    }

    @Test
    fun estimatedListMetricsPinTheEnd() {
        val metrics = estimatedMetrics(firstLine = 3, firstLineOffset = 20, lineCount = 10, averageLine = 100f, viewport = 400f, padding = 0f, atEnd = true)!!
        assertEquals(600f, metrics.offset, 0.01f)
        assertEquals(1000f, metrics.content, 0.01f)
    }

    @Test
    fun estimatedListMetricsUseTheFirstVisibleLine() {
        val metrics = estimatedMetrics(firstLine = 3, firstLineOffset = 20, lineCount = 10, averageLine = 100f, viewport = 400f, padding = 0f, atEnd = false)!!
        assertEquals(320f, metrics.offset, 0.01f)
    }

    @Test
    fun emptyListHasNoMetrics() {
        assertNull(estimatedMetrics(firstLine = 0, firstLineOffset = 0, lineCount = 0, averageLine = 0f, viewport = 400f, padding = 0f, atEnd = true))
    }
}

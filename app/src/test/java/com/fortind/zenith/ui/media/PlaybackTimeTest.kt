package com.fortind.zenith.ui.media

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeTest {

    private fun format(millis: Long) = formatPlaybackTime(millis, Locale.US)

    @Test
    fun shortClipsShowMinutesAndSeconds() {
        assertEquals("0:00", format(0))
        assertEquals("0:09", format(9_999))
        assertEquals("1:05", format(65_000))
        assertEquals("59:59", format(3_599_999))
    }

    @Test
    fun longClipsShowHours() {
        assertEquals("1:00:00", format(3_600_000))
        assertEquals("2:03:04", format(7_384_000))
    }

    @Test
    fun unknownOrNegativeTimesShowZero() {
        assertEquals("0:00", format(-1))
        assertEquals("0:00", format(Long.MIN_VALUE))
    }
}

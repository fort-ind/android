package com.fortind.zenith.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowBoundsTest {
    @Test
    fun boundsSurviveARoundTrip() {
        val bounds = WindowBounds(207, 224, 2280, 1501)

        assertEquals(bounds, WindowBounds.decode(bounds.encode()))
    }

    @Test
    fun malformedOrEmptyBoundsAreDropped() {
        assertNull(WindowBounds.decode("1,2,3"))
        assertNull(WindowBounds.decode("a,b,c,d"))
        assertNull(WindowBounds.decode("10,10,5,20"))
        assertNull(WindowBounds.decode("10,10,20,10"))
    }
}

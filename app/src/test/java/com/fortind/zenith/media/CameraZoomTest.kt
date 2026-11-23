package com.fortind.zenith.media

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraZoomTest {

    @Test
    fun aCameraThatCannotZoomOffersNoStops() {
        assertTrue(zoomStops(1f, 1f, emptyList()).isEmpty())
    }

    @Test
    fun aSingleReachableStopIsNotWorthARow() {
        assertTrue(zoomStops(1f, 1.5f, emptyList()).isEmpty())
    }

    @Test
    fun aPlainCameraOffersOneAndTwo() {
        assertEquals(listOf(1f, 2f), zoomStops(1f, 8f, emptyList()))
    }

    @Test
    fun anUltraWideAddsItsOwnRatioBelowOne() {
        assertEquals(listOf(0.55f, 1f, 2f), zoomStops(0.55f, 10f, emptyList()))
    }

    @Test
    fun aRatioJustUnderOneIsNotTreatedAsAnUltraWide() {
        assertEquals(listOf(1f, 2f), zoomStops(0.96f, 8f, emptyList()))
    }

    @Test
    fun onlyTheLongestTelephotoLensGetsAStop() {
        assertEquals(listOf(0.5f, 1f, 2f, 5f), zoomStops(0.5f, 30f, listOf(0.5f, 1f, 3f, 5f)))
    }

    @Test
    fun aTwoTimesLensSharesTheTwoStop() {
        assertEquals(listOf(1f, 2f), zoomStops(1f, 10f, listOf(1f, 2f)))
    }

    @Test
    fun aLensBeyondTheMaximumIsIgnored() {
        assertEquals(listOf(1f, 2f), zoomStops(1f, 4f, listOf(5f)))
    }

    @Test
    fun theActiveStopIsTheLastOneAtOrBelowTheRatio() {
        val stops = listOf(0.5f, 1f, 2f, 5f)

        assertEquals(0, activeZoomStop(stops, 0.5f))
        assertEquals(0, activeZoomStop(stops, 0.9f))
        assertEquals(1, activeZoomStop(stops, 1.7f))
        assertEquals(2, activeZoomStop(stops, 1.98f))
        assertEquals(3, activeZoomStop(stops, 12f))
    }

    @Test
    fun aRatioBelowEveryStopStillPicksTheFirst() {
        assertEquals(0, activeZoomStop(listOf(1f, 2f), 0.8f))
        assertEquals(-1, activeZoomStop(emptyList(), 1f))
    }

    @Test
    fun aStopCountsAsReachedWithinTheTolerance() {
        assertTrue(isAtZoomStop(2f, 1.97f))
        assertFalse(isAtZoomStop(2f, 1.8f))
    }

    @Test
    fun keysStepByAQuarterAndStayInRange() {
        assertEquals(1.25f, keyZoom(1f, zoomIn = true, minRatio = 0.5f, maxRatio = 10f), 0.001f)
        assertEquals(0.8f, keyZoom(1f, zoomIn = false, minRatio = 0.5f, maxRatio = 10f), 0.001f)
        assertEquals(10f, keyZoom(9.5f, zoomIn = true, minRatio = 0.5f, maxRatio = 10f), 0.001f)
        assertEquals(0.5f, keyZoom(0.55f, zoomIn = false, minRatio = 0.5f, maxRatio = 10f), 0.001f)
    }

    @Test
    fun scrollingUpZoomsInAndDownZoomsOut() {
        assertTrue(wheelZoom(1f, scrollY = -1f, minRatio = 1f, maxRatio = 8f) > 1f)
        assertEquals(1f, wheelZoom(1f, scrollY = 1f, minRatio = 1f, maxRatio = 8f), 0.001f)
        val twoNotchesIn = wheelZoom(1f, scrollY = -2f, minRatio = 1f, maxRatio = 8f)
        assertEquals(keyZoom(1f, zoomIn = true, minRatio = 1f, maxRatio = 8f), twoNotchesIn, 0.001f)
    }

    @Test
    fun zoomTextKeepsOneDecimalAndDropsTrailingZero() {
        assertEquals("1", zoomText(1f, Locale.US))
        assertEquals("0.5", zoomText(0.5479f, Locale.US))
        assertEquals("1.8", zoomText(1.75f, Locale.US))
        assertEquals("0,6", zoomText(0.6f, Locale.GERMANY))
    }

    @Test
    fun exposureIsTheIndexTimesTheStep() {
        assertEquals(0.67f, exposureValue(2, 1, 3), 0.01f)
        assertEquals(-1f, exposureValue(-2, 1, 2), 0.001f)
        assertEquals(0f, exposureValue(3, 1, 0), 0.001f)
    }

    @Test
    fun exposureTextIsSignedExceptAtZero() {
        assertEquals("+0.7", exposureText(0.67f, Locale.US))
        assertEquals("-1", exposureText(-1f, Locale.US))
        assertEquals("0", exposureText(0.02f, Locale.US))
    }
}

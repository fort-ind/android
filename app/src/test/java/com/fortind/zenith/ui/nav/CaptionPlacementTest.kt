package com.fortind.zenith.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPlacementTest {
    private val zoneLeft = 120
    private val zoneRight = 2586
    private val min = 72

    private fun place(vararg panes: CaptionSpan?) = placeCaptionClaims(panes.toList(), zoneLeft, zoneRight, min)

    @Test
    fun aSingleClaimSpansTheWholeSafeZone() {
        val placed = place(CaptionSpan(330, 2880))
        assertEquals(listOf(CaptionPlacement(0, CaptionSpan(zoneLeft, zoneRight))), placed)
    }

    @Test
    fun sideBySidePanesBothKeepTheirClaims() {
        val placed = place(CaptionSpan(330, 990), CaptionSpan(1026, 2880))
        assertEquals(
            listOf(
                CaptionPlacement(0, CaptionSpan(zoneLeft, 990)),
                CaptionPlacement(1, CaptionSpan(1026, zoneRight)),
            ),
            placed,
        )
    }

    @Test
    fun theNewestOfOverlappingClaimsWins() {
        val placed = place(CaptionSpan(330, 2880), CaptionSpan(330, 2880))
        assertEquals(listOf(CaptionPlacement(1, CaptionSpan(zoneLeft, zoneRight))), placed)
    }

    @Test
    fun aClaimWithoutAPaneCoversTheZoneAndHidesOlderOverlaps() {
        val placed = place(CaptionSpan(330, 990), null)
        assertEquals(listOf(CaptionPlacement(1, CaptionSpan(zoneLeft, zoneRight))), placed)
    }

    @Test
    fun aCollapsedPaneIsDropped() {
        val placed = place(CaptionSpan(330, 330), CaptionSpan(354, 2880))
        assertEquals(listOf(CaptionPlacement(1, CaptionSpan(zoneLeft, zoneRight))), placed)
    }

    @Test
    fun aZoneTooNarrowForAnyClaimPlacesNothing() {
        assertTrue(placeCaptionClaims(listOf(CaptionSpan(0, 400)), 100, 150, min).isEmpty())
    }

    @Test
    fun placementsAreOrderedLeftToRightWhateverTheClaimOrder() {
        val placed = place(CaptionSpan(1026, 2880), CaptionSpan(330, 990))
        assertEquals(listOf(1, 0), placed.map { it.index })
    }

    @Test
    fun aCenteredItemSitsOnTheDesiredCentreWhenItFits() {
        assertEquals(700, centeredStart(itemWidth = 600, desiredCenter = 1000, minStart = 0, maxEnd = 2000))
    }

    @Test
    fun aCenteredItemIsPushedBackInsideItsBounds() {
        assertEquals(0, centeredStart(itemWidth = 600, desiredCenter = 100, minStart = 0, maxEnd = 2000))
        assertEquals(1400, centeredStart(itemWidth = 600, desiredCenter = 1900, minStart = 0, maxEnd = 2000))
    }

    @Test
    fun aCenteredItemWiderThanItsBoundsStartsAtTheMinimum() {
        assertEquals(150, centeredStart(itemWidth = 900, desiredCenter = 400, minStart = 150, maxEnd = 800))
    }
}

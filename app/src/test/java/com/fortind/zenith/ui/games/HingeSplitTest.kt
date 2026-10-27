package com.fortind.zenith.ui.games

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HingeSplitTest {

    private val hinge = Rect(500f, 0f, 500f, 1000f)
    private val grid = Rect(200f, 0f, 1000f, 1000f)

    @Test
    fun cellsMeetAtTheHingeNotTheGridCenter() {
        val cells = hingeSplitCells(hinge, grid, gutterPx = 20f, clearancePx = 10, LayoutDirection.Ltr)

        assertEquals(HingeSplitCells(startWidth = 270, endOffset = 290), cells)
    }

    @Test
    fun rtlMeasuresFromTheRightEdge() {
        val cells = hingeSplitCells(hinge, grid, gutterPx = 20f, clearancePx = 10, LayoutDirection.Rtl)

        assertEquals(HingeSplitCells(startWidth = 470, endOffset = 490), cells)
    }

    @Test
    fun hingeOutsideTheGridMeansNoSplit() {
        val offToTheSide = Rect(100f, 0f, 100f, 1000f)

        assertNull(hingeSplitCells(offToTheSide, grid, gutterPx = 20f, clearancePx = 10, LayoutDirection.Ltr))
    }

    @Test
    fun unmeasuredGridMeansNoSplit() {
        assertNull(hingeSplitCells(hinge, Rect.Zero, gutterPx = 20f, clearancePx = 10, LayoutDirection.Ltr))
    }
}

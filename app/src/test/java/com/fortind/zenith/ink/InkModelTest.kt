package com.fortind.zenith.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkHistoryTest {
    @Test
    fun undoAndRedoWalkTheSnapshots() {
        val history = InkHistory(listOf<String>())
        history.push(listOf("a"))
        history.push(listOf("a", "b"))
        assertEquals(listOf("a"), history.undo())
        assertEquals(emptyList<String>(), history.undo())
        assertFalse(history.canUndo)
        assertEquals(emptyList<String>(), history.undo())
        assertEquals(listOf("a"), history.redo())
        assertTrue(history.canRedo)
    }

    @Test
    fun aNewEditDropsTheRedoBranch() {
        val history = InkHistory(0)
        history.push(1)
        history.push(2)
        history.undo()
        history.push(3)
        assertFalse(history.canRedo)
        assertEquals(3, history.current)
        assertEquals(1, history.undo())
    }

    @Test
    fun theOldestSnapshotFallsOffAtTheLimit() {
        val history = InkHistory(0, limit = 3)
        (1..5).forEach(history::push)
        assertEquals(4, history.undo())
        assertEquals(3, history.undo())
        assertFalse(history.canUndo)
    }

    @Test
    fun resetForgetsEverything() {
        val history = InkHistory(0)
        history.push(1)
        history.reset(9)
        assertEquals(9, history.current)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}

class InkInputPolicyTest {
    private val policy = InkInputPolicy(drawWithMouse = true, touchStopsAfterPen = true)

    @Test
    fun theStylusEraserAndButtonAlwaysErase() {
        InkTool.entries.forEach { tool ->
            assertEquals(InkAction.Erase, policy.actionFor(InkPointer.StylusEraser, tool, penSeen = false))
            assertEquals(InkAction.Erase, policy.actionFor(InkPointer.StylusButton, tool, penSeen = true))
        }
    }

    @Test
    fun theStylusFollowsTheTool() {
        assertEquals(InkAction.Draw, policy.actionFor(InkPointer.Stylus, InkTool.Pen, penSeen = true))
        assertEquals(InkAction.Erase, policy.actionFor(InkPointer.Stylus, InkTool.Eraser, penSeen = true))
    }

    @Test
    fun touchStopsOnceAPenWasSeen() {
        assertEquals(InkAction.Draw, policy.actionFor(InkPointer.Touch, InkTool.Marker, penSeen = false))
        assertEquals(InkAction.Ignore, policy.actionFor(InkPointer.Touch, InkTool.Marker, penSeen = true))
        val keepTouch = policy.copy(touchStopsAfterPen = false)
        assertEquals(InkAction.Draw, keepTouch.actionFor(InkPointer.Touch, InkTool.Marker, penSeen = true))
    }

    @Test
    fun theMouseDrawsOnlyWhenAllowedAndOnlyWithThePrimaryButton() {
        assertEquals(InkAction.Draw, policy.actionFor(InkPointer.Mouse, InkTool.Pen, penSeen = false))
        assertEquals(InkAction.Ignore, policy.actionFor(InkPointer.MouseOtherButton, InkTool.Pen, penSeen = false))
        val noMouse = policy.copy(drawWithMouse = false)
        assertEquals(InkAction.Ignore, noMouse.actionFor(InkPointer.Mouse, InkTool.Pen, penSeen = false))
    }

    @Test
    fun onlyStylusPointersCountAsAPen() {
        assertTrue(policy.isPen(InkPointer.Stylus))
        assertTrue(policy.isPen(InkPointer.StylusEraser))
        assertFalse(policy.isPen(InkPointer.Touch))
        assertFalse(policy.isPen(InkPointer.Mouse))
    }
}

class SheetGeometryTest {
    @Test
    fun drawingsAre800OnTheLongSideAndExportAtTwice() {
        assertEquals(SheetSize(800f, 600f), SheetGeometry.drawing(SheetShape.Landscape))
        assertEquals(SheetSize(800f, 450f), SheetGeometry.drawing(SheetShape.Wide))
        assertEquals(SheetSize(800f, 800f), SheetGeometry.drawing(SheetShape.Square))
        assertEquals(SheetSize(600f, 800f), SheetGeometry.drawing(SheetShape.Portrait))
        assertEquals(PixelSize(1600, 1200), SheetGeometry.drawingExport(SheetGeometry.drawing(SheetShape.Landscape)))
    }

    @Test
    fun markupKeepsThePhotoAspectAt1000() {
        assertEquals(SheetSize(1000f, 500f), SheetGeometry.markup(2000, 1000))
        assertEquals(SheetSize(750f, 1000f), SheetGeometry.markup(1536, 2048))
    }

    @Test
    fun fitScaleUsesTheTighterSide() {
        val sheet = SheetGeometry.drawing(SheetShape.Landscape)
        assertEquals(0.5f, SheetGeometry.fitScale(sheet, 400f, 1000f))
        assertEquals(0.25f, SheetGeometry.fitScale(sheet, 1000f, 150f))
    }

    @Test
    fun photosAreCappedAt2560() {
        assertEquals(PixelSize(2560, 1440), SheetGeometry.limitPixels(4000, 2250))
        assertEquals(PixelSize(1200, 900), SheetGeometry.limitPixels(1200, 900))
    }
}

class InkStylesTest {
    @Test
    fun aDarkBackgroundTurnsBlackInkWhite() {
        val dark = InkStyles.forBackground(InkStyles.defaults, SheetBackground.Black)
        assertEquals(InkColor.White, dark.getValue(InkTool.Pen).color)
        assertEquals(InkColor.White, dark.getValue(InkTool.Marker).color)
        assertEquals(InkColor.Yellow, dark.getValue(InkTool.Highlighter).color)
        val back = InkStyles.forBackground(dark, SheetBackground.White)
        assertEquals(InkColor.Black, back.getValue(InkTool.Pen).color)
    }

    @Test
    fun aChosenColorIsLeftAlone() {
        val red = InkStyles.defaults + (InkTool.Pen to ToolStyle(InkColor.Red, InkSize.Large))
        assertEquals(InkColor.Red, InkStyles.forBackground(red, SheetBackground.Theme).getValue(InkTool.Pen).color)
        assertEquals(red, InkStyles.forBackground(red, SheetBackground.Transparent))
    }

    @Test
    fun theHighlighterMultipliesOnlyOverALightBackdrop() {
        val themeArgb = 0xFF1B1B3A.toInt()
        assertTrue(InkStyles.highlighterMultiplies(SheetBackground.White.argb(themeArgb)))
        assertTrue(InkStyles.highlighterMultiplies(SheetBackground.Transparent.argb(themeArgb)))
        assertFalse(InkStyles.highlighterMultiplies(SheetBackground.Black.argb(themeArgb)))
        assertFalse(InkStyles.highlighterMultiplies(SheetBackground.Theme.argb(themeArgb)))
        assertTrue(InkStyles.highlighterMultiplies(SheetBackground.Theme.argb(0xFFD0BCFF.toInt())))
    }

    @Test
    fun noColorIsPureWhite() {
        assertTrue(InkColor.entries.none { it.argb == 0xFFFFFFFF })
        assertTrue(InkColor.White.argb and 0xFFFFFF > 0xFEFEFE)
    }

    @Test
    fun widthsStayAboveTheEpsilon() {
        InkTool.entries.forEach { tool ->
            InkSize.entries.forEach { size -> assertTrue(InkStyles.width(tool, size) > InkStyles.EPSILON) }
        }
    }
}

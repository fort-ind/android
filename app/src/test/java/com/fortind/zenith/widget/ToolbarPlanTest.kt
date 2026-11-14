package com.fortind.zenith.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarPlanTest {
    private fun portrait(columns: Int, rows: Int) = DpSize((73 * columns - 16).dp, (118 * rows - 16).dp)

    private fun landscape(columns: Int, rows: Int) = DpSize((142 * columns - 15).dp, (66 * rows - 15).dp)

    private fun ToolbarPlan.cellSizes(size: DpSize): List<DpSize> {
        if (wholeSurface != null) return listOf(size)
        val contentWidth = size.width - horizontalPadding * 2
        val contentHeight = size.height - verticalPadding * 2
        val gridWidth = if (layout == ToolbarLayout.SideBarGrid) {
            contentWidth - ToolbarDimens.LeadSize - ToolbarDimens.Spacing
        } else {
            contentWidth
        }
        val gridHeight = if (layout == ToolbarLayout.HeaderGrid) {
            contentHeight - ToolbarDimens.LeadSize - ToolbarDimens.Spacing
        } else {
            contentHeight
        }
        val rowHeight = (gridHeight - ToolbarDimens.Spacing * (rows.size - 1)) / rows.size
        val cells = rows.flatMap { row ->
            val width = (gridWidth - ToolbarDimens.Spacing * (row.size - 1)) / row.size
            row.map { DpSize(width, rowHeight) }
        }
        val leadCell = when (layout) {
            ToolbarLayout.SideBarGrid -> DpSize(ToolbarDimens.LeadSize, contentHeight)
            ToolbarLayout.HeaderGrid -> DpSize(contentWidth, ToolbarDimens.LeadSize)
            else -> null
        }
        return cells + listOfNotNull(leadCell)
    }

    private fun sweep(check: (DpSize, Boolean, ToolbarPlan) -> Unit) {
        for (width in 48..624 step 2) {
            for (height in 48..422 step 2) {
                val size = DpSize(width.dp, height.dp)
                for (signedIn in listOf(true, false)) check(size, signedIn, planToolbar(size, signedIn))
            }
        }
    }

    @Test
    fun everyTouchTargetIsAtLeast48dpAtEverySupportedSize() {
        sweep { size, signedIn, plan ->
            plan.cellSizes(size).forEach { cell ->
                assertTrue(
                    "size=$size signedIn=$signedIn layout=${plan.layout} cell=$cell",
                    cell.width >= ToolbarDimens.MinTarget && cell.height >= ToolbarDimens.MinTarget,
                )
            }
        }
    }

    @Test
    fun noItemAppearsTwiceAndSomethingIsAlwaysShown() {
        sweep { size, signedIn, plan ->
            assertTrue("size=$size signedIn=$signedIn", plan.items.isNotEmpty())
            assertEquals("size=$size signedIn=$signedIn", plan.items.size, plan.items.toSet().size)
        }
    }

    @Test
    fun signedInAlwaysOffersSearchAndNeverSignIn() {
        sweep { size, signedIn, plan ->
            if (signedIn) {
                assertTrue("size=$size", ToolbarItem.Search in plan.items)
                assertFalse("size=$size", ToolbarItem.SignIn in plan.items)
            }
        }
    }

    @Test
    fun signedOutAlwaysOffersSignInAndNeverAccountShortcuts() {
        val accountOnly = setOf(ToolbarItem.Post, ToolbarItem.Notifications, ToolbarItem.Profile)
        sweep { size, signedIn, plan ->
            if (!signedIn) {
                assertTrue("size=$size", ToolbarItem.SignIn in plan.items)
                assertTrue("size=$size", plan.items.none { it in accountOnly })
            }
        }
    }

    @Test
    fun oneCellShowsASingleWholeSurfaceAction() {
        assertEquals(ToolbarItem.Search, planToolbar(portrait(1, 1), signedIn = true).wholeSurface)
        assertEquals(ToolbarItem.SignIn, planToolbar(portrait(1, 1), signedIn = false).wholeSurface)
        assertEquals(ToolbarItem.Search, planToolbar(DpSize(48.dp, 48.dp), signedIn = true).wholeSurface)
    }

    @Test
    fun aSingleRowDropsShortcutsFromTheEnd() {
        val twoCells = planToolbar(portrait(2, 1), signedIn = true)
        assertEquals(ToolbarLayout.Row, twoCells.layout)
        assertEquals(listOf(listOf(ToolbarItem.Search, ToolbarItem.Post)), twoCells.rows)

        val fourCells = planToolbar(portrait(4, 1), signedIn = true)
        assertEquals(
            listOf(listOf(ToolbarItem.Search, ToolbarItem.Post, ToolbarItem.Notifications, ToolbarItem.Profile)),
            fourCells.rows,
        )

        val fiveCells = planToolbar(portrait(5, 1), signedIn = true)
        assertEquals(5, fiveCells.rows.single().size)
        assertEquals(ToolbarItem.Games, fiveCells.rows.single().last())
    }

    @Test
    fun aShortLandscapeRowLosesItsPaddingAndFillToKeep48dpTargets() {
        val plan = planToolbar(landscape(2, 1), signedIn = true)
        assertEquals(ToolbarLayout.Row, plan.layout)
        assertEquals(0.dp, plan.verticalPadding)
        assertFalse(plan.filled)
    }

    @Test
    fun signedOutRowKeepsSignInBeforeGamesWhenSpaceIsShort() {
        val plan = planToolbar(portrait(2, 1), signedIn = false)
        assertEquals(listOf(listOf(ToolbarItem.Search, ToolbarItem.SignIn)), plan.rows)
        val wide = planToolbar(portrait(4, 1), signedIn = false)
        assertEquals(listOf(listOf(ToolbarItem.Search, ToolbarItem.Games, ToolbarItem.SignIn)), wide.rows)
    }

    @Test
    fun narrowTallWidgetStacksAColumn() {
        val plan = planToolbar(portrait(1, 3), signedIn = true)
        assertEquals(ToolbarLayout.Column, plan.layout)
        assertTrue(plan.rows.all { it.size == 1 })
        assertEquals(ToolbarItem.Search, plan.rows.first().single())
    }

    @Test
    fun squareMidSizeIsATwoByTwoGrid() {
        val size = DpSize(150.dp, 150.dp)
        val signedIn = planToolbar(size, signedIn = true)
        assertEquals(ToolbarLayout.Grid, signedIn.layout)
        assertEquals(
            listOf(
                listOf(ToolbarItem.Search, ToolbarItem.Post),
                listOf(ToolbarItem.Notifications, ToolbarItem.Profile),
            ),
            signedIn.rows,
        )
        val signedOut = planToolbar(size, signedIn = false)
        assertEquals(listOf(listOf(ToolbarItem.Search, ToolbarItem.Games), listOf(ToolbarItem.SignIn)), signedOut.rows)
        assertTrue(signedOut.signInText)
    }

    @Test
    fun wideTwoRowWidgetPutsSearchInASideBar() {
        val plan = planToolbar(landscape(3, 3), signedIn = true)
        assertEquals(ToolbarLayout.SideBarGrid, plan.layout)
        assertEquals(ToolbarItem.Search, plan.lead)
        assertEquals(4, plan.rows.flatten().size)
        assertFalse(plan.searchText)
    }

    @Test
    fun tallWidgetGetsASearchHeaderWithTextOnceItIsWideEnough() {
        val narrow = planToolbar(portrait(2, 2), signedIn = true)
        assertEquals(ToolbarLayout.HeaderGrid, narrow.layout)
        assertFalse(narrow.searchText)

        val threeCells = planToolbar(portrait(3, 2), signedIn = true)
        assertEquals(ToolbarLayout.HeaderGrid, threeCells.layout)
        assertFalse(threeCells.searchText)

        val wide = planToolbar(portrait(4, 4), signedIn = true)
        assertEquals(ToolbarLayout.HeaderGrid, wide.layout)
        assertTrue(wide.searchText)
        assertEquals(ToolbarItem.Search, wide.lead)
    }

    @Test
    fun anUnknownSizeFromTheHostStillPlansOneAction() {
        val plan = planToolbar(DpSize.Zero, signedIn = true)
        assertEquals(ToolbarItem.Search, plan.wholeSurface)
        assertEquals(1, itemsThatFit(Dp(-20f)))
    }
}

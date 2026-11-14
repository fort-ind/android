package com.fortind.zenith.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class ToolbarItem { Search, Post, Notifications, Profile, Games, SignIn }

enum class ToolbarLayout { Row, Column, Grid, SideBarGrid, HeaderGrid }

data class ToolbarPlan(
    val layout: ToolbarLayout,
    val lead: ToolbarItem?,
    val rows: List<List<ToolbarItem>>,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val filled: Boolean,
    val searchText: Boolean,
    val signInText: Boolean,
) {
    val items: List<ToolbarItem> get() = listOfNotNull(lead) + rows.flatten()

    val wholeSurface: ToolbarItem? get() = items.singleOrNull()
}

object ToolbarDimens {
    val MinTarget = 48.dp
    val Padding = 12.dp
    val Spacing = 8.dp
    val IconSize = 24.dp
    val LeadSize = 52.dp
    val GridBreakpoint = 128.dp
    val LeadBreakpoint = 188.dp
    val SearchTextMinWidth = 240.dp
    val FilledMinSize = 72.dp
    val SignInTextMinWidth = 112.dp
}

private val SignedInItems = listOf(
    ToolbarItem.Search,
    ToolbarItem.Post,
    ToolbarItem.Notifications,
    ToolbarItem.Profile,
    ToolbarItem.Games,
)
private val SignedOutItems = listOf(ToolbarItem.Search, ToolbarItem.Games, ToolbarItem.SignIn)
private val SignedOutPriority = listOf(ToolbarItem.SignIn, ToolbarItem.Search, ToolbarItem.Games)

fun planToolbar(size: DpSize, signedIn: Boolean): ToolbarPlan {
    val horizontalPadding = if (size.width < ToolbarDimens.FilledMinSize) 0.dp else ToolbarDimens.Padding
    val verticalPadding = if (size.height < ToolbarDimens.FilledMinSize) 0.dp else ToolbarDimens.Padding
    val contentWidth = size.width - horizontalPadding * 2
    val contentHeight = size.height - verticalPadding * 2
    val layout = when {
        size.height < ToolbarDimens.GridBreakpoint -> ToolbarLayout.Row
        size.width < ToolbarDimens.GridBreakpoint -> ToolbarLayout.Column
        size.height < ToolbarDimens.LeadBreakpoint && size.width < ToolbarDimens.LeadBreakpoint -> ToolbarLayout.Grid
        size.height < ToolbarDimens.LeadBreakpoint -> ToolbarLayout.SideBarGrid
        else -> ToolbarLayout.HeaderGrid
    }
    val lead = ToolbarItem.Search.takeIf { layout == ToolbarLayout.SideBarGrid || layout == ToolbarLayout.HeaderGrid }
    val rows = when (layout) {
        ToolbarLayout.Row -> listOf(fitting(contentWidth, signedIn))
        ToolbarLayout.Column -> fitting(contentHeight, signedIn).map(::listOf)
        ToolbarLayout.Grid -> if (signedIn) {
            listOf(
                listOf(ToolbarItem.Search, ToolbarItem.Post),
                listOf(ToolbarItem.Notifications, ToolbarItem.Profile),
            )
        } else {
            listOf(listOf(ToolbarItem.Search, ToolbarItem.Games), listOf(ToolbarItem.SignIn))
        }
        ToolbarLayout.SideBarGrid, ToolbarLayout.HeaderGrid -> if (signedIn) {
            listOf(
                listOf(ToolbarItem.Post, ToolbarItem.Notifications),
                listOf(ToolbarItem.Profile, ToolbarItem.Games),
            )
        } else {
            listOf(listOf(ToolbarItem.Games), listOf(ToolbarItem.SignIn))
        }
    }
    val gridWidth = if (layout == ToolbarLayout.SideBarGrid) {
        contentWidth - ToolbarDimens.LeadSize - ToolbarDimens.Spacing
    } else {
        contentWidth
    }
    val signInRow = rows.firstOrNull { ToolbarItem.SignIn in it }
    val signInWidth = signInRow?.let { (gridWidth - ToolbarDimens.Spacing * (it.size - 1)) / it.size }
    return ToolbarPlan(
        layout = layout,
        lead = lead,
        rows = rows,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        filled = size.width >= ToolbarDimens.FilledMinSize && size.height >= ToolbarDimens.FilledMinSize,
        searchText = layout == ToolbarLayout.HeaderGrid && size.width >= ToolbarDimens.SearchTextMinWidth,
        signInText = signInWidth != null && signInWidth >= ToolbarDimens.SignInTextMinWidth,
    )
}

fun itemsThatFit(length: Dp): Int =
    ((length + ToolbarDimens.Spacing) / (ToolbarDimens.MinTarget + ToolbarDimens.Spacing)).toInt().coerceAtLeast(1)

private fun fitting(length: Dp, signedIn: Boolean): List<ToolbarItem> {
    val count = itemsThatFit(length)
    if (signedIn) return SignedInItems.take(count)
    val kept = SignedOutPriority.take(count)
    return SignedOutItems.filter { it in kept }
}

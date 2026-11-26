@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package com.fortind.zenith.ui.nav

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.ui.unit.dp

private val DefaultSplitOffset = 414.dp

val DetailCollapsedAnchor = PaneExpansionAnchor.Proportion(1f)

val PaneExpansionAnchors = listOf(
    PaneExpansionAnchor.Proportion(0f),
    PaneExpansionAnchor.Offset.fromStart(DefaultSplitOffset),
    PaneExpansionAnchor.Proportion(0.5f),
    PaneExpansionAnchor.Offset.fromEnd(DefaultSplitOffset),
    DetailCollapsedAnchor,
)

const val NoSavedAnchor = -1

fun restoredAnchorIndex(saved: Int?, anchorCount: Int = PaneExpansionAnchors.size): Int =
    saved?.takeIf { it in 0 until anchorCount } ?: NoSavedAnchor

fun anchorIndexOf(anchor: PaneExpansionAnchor?): Int? =
    anchor?.let { PaneExpansionAnchors.indexOf(it) }?.takeIf { it >= 0 }

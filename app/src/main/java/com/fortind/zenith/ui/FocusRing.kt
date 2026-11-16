package com.fortind.zenith.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.focusRing(color: Color, shape: Shape, width: Dp = FocusRingWidth): Modifier = this then FocusRingElement(color, shape, width)

val FocusRingWidth = 3.dp

private data class FocusRingElement(val color: Color, val shape: Shape, val width: Dp) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(color, shape, width)

    override fun update(node: FocusRingNode) {
        node.color = color
        node.shape = shape
        node.width = width
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusRing"
    }
}

private class FocusRingNode(var color: Color, var shape: Shape, var width: Dp) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focused == focusState.isFocused) return
        focused = focusState.isFocused
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val stroke = width.toPx()
        val outline = shape.createOutline(Size(size.width - stroke, size.height - stroke), layoutDirection, this)
        translate(stroke / 2, stroke / 2) { drawOutline(outline, color, style = Stroke(width = stroke)) }
    }
}

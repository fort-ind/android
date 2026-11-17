package com.fortind.zenith.ink

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.ink.brush.Brush
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.compose.createWithComposeColor
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.MutableParallelogram
import androidx.ink.geometry.MutableSegment
import androidx.ink.geometry.MutableVec
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

object InkBrushes {
    private val highlighterFamily = StockBrushes.highlighter(SelfOverlap.DISCARD)

    fun brushFor(tool: InkTool, style: ToolStyle): Brush? {
        val family = when (tool) {
            InkTool.Pen -> StockBrushes.pressurePen()
            InkTool.Marker -> StockBrushes.marker()
            InkTool.Highlighter -> highlighterFamily
            InkTool.Eraser -> return null
        }
        val base = Color(style.color.argb)
        val color = if (tool == InkTool.Highlighter) base.copy(alpha = InkStyles.HIGHLIGHTER_ALPHA) else base
        return Brush.createWithComposeColor(family, color, InkStyles.width(tool, style.size), InkStyles.EPSILON)
    }

    fun isHighlighter(stroke: Stroke): Boolean = stroke.brush.family == highlighterFamily
}

class InkPainter(private val renderer: CanvasStrokeRenderer = CanvasStrokeRenderer.create()) {
    private val matrix = Matrix()
    private val multiply = Paint().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.MULTIPLY
    }

    fun draw(canvas: Canvas, strokes: List<Stroke>, scale: Float, multiplyHighlighters: Boolean = true) {
        matrix.setScale(scale, scale)
        strokes.forEach { stroke ->
            val layered = multiplyHighlighters && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && InkBrushes.isHighlighter(stroke)
            val box = if (layered) stroke.shape.computeBoundingBox() else null
            val restore = if (box != null) {
                canvas.saveLayer(box.xMin * scale - 1, box.yMin * scale - 1, box.xMax * scale + 1, box.yMax * scale + 1, multiply)
            } else {
                canvas.save()
            }
            canvas.scale(scale, scale)
            renderer.draw(canvas, stroke, matrix)
            canvas.restoreToCount(restore)
        }
    }
}

class InkEraser {
    private val previous = MutableVec()
    private val current = MutableVec()
    private val segment = MutableSegment(previous, current)
    private val area = MutableParallelogram()
    private var active = false

    fun start(x: Float, y: Float) {
        previous.x = x
        previous.y = y
        active = true
    }

    fun erase(strokes: List<Stroke>, x: Float, y: Float, radius: Float): List<Stroke> {
        if (!active) start(x, y)
        current.x = if (x == previous.x && y == previous.y) x + MIN_SEGMENT else x
        current.y = y
        area.populateFromSegmentAndPadding(segment, radius)
        val kept = strokes.filterNot { it.shape.intersects(area, AffineTransform.IDENTITY) }
        previous.x = x
        previous.y = y
        return if (kept.size == strokes.size) strokes else kept
    }

    fun end() {
        active = false
    }

    private companion object {
        const val MIN_SEGMENT = 0.01f
    }
}

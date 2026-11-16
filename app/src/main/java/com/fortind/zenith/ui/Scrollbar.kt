package com.fortind.zenith.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

data class ScrollMetrics(val offset: Float, val content: Float, val viewport: Float)

data class ScrollbarThumb(val start: Float, val length: Float)

fun scrollbarThumb(metrics: ScrollMetrics, trackLength: Float, minLength: Float): ScrollbarThumb? {
    val scrollable = metrics.content - metrics.viewport
    if (scrollable <= 0f || trackLength <= 0f || metrics.content <= 0f) return null
    val length = (trackLength * metrics.viewport / metrics.content).coerceIn(min(minLength, trackLength), trackLength)
    val progress = (metrics.offset / scrollable).coerceIn(0f, 1f)
    return ScrollbarThumb(progress * (trackLength - length), length)
}

fun scrollOffsetForThumb(start: Float, thumbLength: Float, trackLength: Float, metrics: ScrollMetrics): Float {
    val travel = trackLength - thumbLength
    val scrollable = metrics.content - metrics.viewport
    if (travel <= 0f || scrollable <= 0f) return 0f
    return (start / travel).coerceIn(0f, 1f) * scrollable
}

fun estimatedMetrics(
    firstLine: Int,
    firstLineOffset: Int,
    lineCount: Int,
    averageLine: Float,
    viewport: Float,
    padding: Float,
    atEnd: Boolean,
): ScrollMetrics? {
    if (lineCount <= 0 || averageLine <= 0f || viewport <= 0f) return null
    val content = max(averageLine * lineCount + padding, viewport)
    val offset = if (atEnd) content - viewport else averageLine * firstLine + firstLineOffset
    return ScrollMetrics(offset.coerceIn(0f, content - viewport), content, viewport)
}

sealed interface ScrollbarSource {
    val orientation: Orientation
    val metrics: ScrollMetrics?
    val canScroll: Boolean
    suspend fun scrollToOffset(offset: Float)
}

private data class LazyListSource(val state: LazyListState) : ScrollbarSource {
    override val orientation: Orientation get() = state.layoutInfo.orientation
    override val canScroll: Boolean get() = state.canScrollForward || state.canScrollBackward

    private val averageLine: Float
        get() {
            val info = state.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return 0f
            return items.sumOf { it.size + info.mainAxisItemSpacing }.toFloat() / items.size
        }

    override val metrics: ScrollMetrics?
        get() {
            val info = state.layoutInfo
            val viewport = if (info.orientation == Orientation.Vertical) info.viewportSize.height else info.viewportSize.width
            return estimatedMetrics(
                firstLine = state.firstVisibleItemIndex,
                firstLineOffset = state.firstVisibleItemScrollOffset,
                lineCount = info.totalItemsCount,
                averageLine = averageLine,
                viewport = viewport.toFloat(),
                padding = (info.beforeContentPadding + info.afterContentPadding).toFloat(),
                atEnd = !state.canScrollForward,
            )
        }

    override suspend fun scrollToOffset(offset: Float) {
        val line = averageLine
        val total = state.layoutInfo.totalItemsCount
        if (line <= 0f || total == 0) return
        val index = (offset / line).toInt().coerceIn(0, total - 1)
        state.scrollToItem(index, (offset - index * line).toInt())
    }
}

private data class LazyGridSource(val state: LazyGridState) : ScrollbarSource {
    override val orientation: Orientation get() = state.layoutInfo.orientation
    override val canScroll: Boolean get() = state.canScrollForward || state.canScrollBackward

    private val vertical get() = state.layoutInfo.orientation == Orientation.Vertical

    private val lanes: Int
        get() {
            val items = state.layoutInfo.visibleItemsInfo
            val lane = items.maxOfOrNull { if (vertical) it.column else it.row } ?: 0
            return max(lane + 1, 1)
        }

    private val averageLine: Float
        get() {
            val info = state.layoutInfo
            val lines = info.visibleItemsInfo.groupBy { if (vertical) it.row else it.column }
            if (lines.isEmpty()) return 0f
            val sizes = lines.values.map { line -> line.maxOf { if (vertical) it.size.height else it.size.width } + info.mainAxisItemSpacing }
            return sizes.sum().toFloat() / sizes.size
        }

    override val metrics: ScrollMetrics?
        get() {
            val info = state.layoutInfo
            val viewport = if (vertical) info.viewportSize.height else info.viewportSize.width
            val lanes = lanes
            return estimatedMetrics(
                firstLine = state.firstVisibleItemIndex / lanes,
                firstLineOffset = state.firstVisibleItemScrollOffset,
                lineCount = (info.totalItemsCount + lanes - 1) / lanes,
                averageLine = averageLine,
                viewport = viewport.toFloat(),
                padding = (info.beforeContentPadding + info.afterContentPadding).toFloat(),
                atEnd = !state.canScrollForward,
            )
        }

    override suspend fun scrollToOffset(offset: Float) {
        val line = averageLine
        val total = state.layoutInfo.totalItemsCount
        if (line <= 0f || total == 0) return
        val row = (offset / line).toInt()
        state.scrollToItem((row * lanes).coerceIn(0, total - 1), (offset - row * line).toInt())
    }
}

private data class ScrollStateSource(val state: ScrollState, override val orientation: Orientation) : ScrollbarSource {
    override val canScroll: Boolean get() = state.maxValue > 0

    override val metrics: ScrollMetrics?
        get() {
            val viewport = state.viewportSize.toFloat()
            if (viewport <= 0f || state.maxValue == Int.MAX_VALUE) return null
            return ScrollMetrics(state.value.toFloat(), state.maxValue + viewport, viewport)
        }

    override suspend fun scrollToOffset(offset: Float) {
        state.scrollTo(offset.toInt())
    }
}

fun Modifier.scrollbar(state: LazyListState): Modifier = this then ScrollbarElement(LazyListSource(state))

fun Modifier.scrollbar(state: LazyGridState): Modifier = this then ScrollbarElement(LazyGridSource(state))

fun Modifier.scrollbar(state: ScrollState, orientation: Orientation = Orientation.Vertical): Modifier =
    this then ScrollbarElement(ScrollStateSource(state, orientation))

private data class ScrollbarElement(val source: ScrollbarSource) : ModifierNodeElement<ScrollbarNode>() {
    override fun create() = ScrollbarNode(source)

    override fun update(node: ScrollbarNode) {
        node.update(source)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollbar"
    }
}

private class ScrollbarNode(private var source: ScrollbarSource) :
    DelegatingNode(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {

    private val alpha = Animatable(0f)
    private var cursorMode = false
    private var hovered by mutableStateOf(false)
    private var dragging by mutableStateOf(false)
    private var hideJob: Job? = null
    private var watchJob: Job? = null
    private var scrollJob: Job? = null

    private val pointer = delegate(SuspendingPointerInputModifierNode(PointerInputEventHandler { handlePointer() }))

    override fun onAttach() {
        watch()
    }

    fun update(newSource: ScrollbarSource) {
        if (newSource == source) return
        source = newSource
        pointer.resetPointerInputHandler()
        watch()
        invalidateDraw()
    }

    private fun watch() {
        if (!isAttached) return
        watchJob?.cancel()
        watchJob = coroutineScope.launch {
            snapshotFlow { source.metrics?.offset }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { if (cursorMode) reveal() }
        }
    }

    private fun reveal() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            alpha.animateTo(1f, tween(FADE_IN_MS))
            if (hovered || dragging) return@launch
            delay(LINGER_MS)
            alpha.animateTo(0f, tween(FADE_OUT_MS))
        }
    }

    private suspend fun PointerInputScope.handlePointer() {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                when (change.type) {
                    PointerType.Mouse -> cursorMode = true
                    PointerType.Touch -> if (event.type == PointerEventType.Press) cursorMode = false
                    else -> Unit
                }
                if (change.type != PointerType.Mouse || !source.canScroll) {
                    if (hovered) updateHovered(false)
                    continue
                }
                val vertical = source.orientation == Orientation.Vertical
                val across = if (vertical) change.position.x else change.position.y
                val along = if (vertical) change.position.y else change.position.x
                val crossSize = if (vertical) size.width else size.height
                val zone = HIT_ZONE.toPx()
                val atEdge = when {
                    !vertical -> across >= crossSize - zone
                    requireLayoutDirection() == LayoutDirection.Rtl -> across <= zone
                    else -> across >= crossSize - zone
                }
                updateHovered(atEdge && event.type != PointerEventType.Exit)
                val primary = event.buttons.isPrimaryPressed || !event.buttons.areAnyPressed
                if (event.type != PointerEventType.Press || !atEdge || !primary) continue
                val trackLength = (if (vertical) size.height else size.width) - 2 * TRACK_INSET.toPx()
                val metrics = source.metrics ?: continue
                val thumb = scrollbarThumb(metrics, trackLength, MIN_THUMB.toPx()) ?: continue
                change.consume()
                val pressAt = along - TRACK_INSET.toPx()
                val grab = if (pressAt in thumb.start..(thumb.start + thumb.length)) pressAt - thumb.start else thumb.length / 2
                dragging = true
                dragTo(pressAt - grab, thumb.length, trackLength)
                while (true) {
                    val move = awaitPointerEvent(PointerEventPass.Initial)
                    val moved = move.changes.firstOrNull() ?: break
                    if (!moved.pressed) break
                    moved.consume()
                    val position = if (vertical) moved.position.y else moved.position.x
                    dragTo(position - TRACK_INSET.toPx() - grab, thumb.length, trackLength)
                }
                dragging = false
                reveal()
            }
        }
    }

    private fun dragTo(start: Float, thumbLength: Float, trackLength: Float) {
        val metrics = source.metrics ?: return
        val offset = scrollOffsetForThumb(start, thumbLength, trackLength, metrics)
        scrollJob?.cancel()
        scrollJob = coroutineScope.launch { source.scrollToOffset(offset) }
    }

    private fun updateHovered(value: Boolean) {
        if (hovered == value) return
        hovered = value
        if (value) {
            hideJob?.cancel()
            hideJob = coroutineScope.launch { alpha.animateTo(1f, tween(FADE_IN_MS)) }
        } else {
            reveal()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val opacity = alpha.value
        if (opacity <= 0f) return
        val metrics = source.metrics ?: return
        val vertical = source.orientation == Orientation.Vertical
        val inset = TRACK_INSET.toPx()
        val trackLength = (if (vertical) size.height else size.width) - 2 * inset
        val thumb = scrollbarThumb(metrics, trackLength, MIN_THUMB.toPx()) ?: return
        val active = hovered || dragging
        val thickness = (if (active) THICK else THIN).toPx()
        val edge = EDGE_GAP.toPx()
        val color = currentValueOf(LocalContentColor)
        val cross = when {
            !vertical -> size.height - edge - thickness
            layoutDirection == LayoutDirection.Rtl -> edge
            else -> size.width - edge - thickness
        }
        val radius = CornerRadius(thickness / 2)
        if (active) {
            drawRoundRect(
                color = color.copy(alpha = TRACK_ALPHA * opacity),
                topLeft = if (vertical) Offset(cross, inset) else Offset(inset, cross),
                size = if (vertical) Size(thickness, trackLength) else Size(trackLength, thickness),
                cornerRadius = radius,
            )
        }
        drawRoundRect(
            color = color.copy(alpha = (if (dragging) DRAG_ALPHA else if (active) HOVER_ALPHA else IDLE_ALPHA) * opacity),
            topLeft = if (vertical) Offset(cross, inset + thumb.start) else Offset(inset + thumb.start, cross),
            size = if (vertical) Size(thickness, thumb.length) else Size(thumb.length, thickness),
            cornerRadius = radius,
        )
    }
}

private val THIN = 4.dp
private val THICK = 8.dp
private val EDGE_GAP = 2.dp
private val TRACK_INSET = 4.dp
private val HIT_ZONE = 12.dp
private val MIN_THUMB = 32.dp
private const val IDLE_ALPHA = 0.42f
private const val HOVER_ALPHA = 0.62f
private const val DRAG_ALPHA = 0.78f
private const val TRACK_ALPHA = 0.08f
private const val FADE_IN_MS = 120
private const val FADE_OUT_MS = 300
private const val LINGER_MS = 1_000L

fun Modifier.verticalScrollWithBar(state: ScrollState): Modifier = scrollbar(state, Orientation.Vertical).verticalScroll(state)

fun Modifier.horizontalScrollWithBar(state: ScrollState): Modifier = scrollbar(state, Orientation.Horizontal).horizontalScroll(state)

package com.fortind.zenith.ui.feed

import android.content.ClipData
import android.content.ClipDescription
import android.content.IntentSender
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.ui.media.AppDrag
import kotlin.math.ceil

class ThreadWindowActions(val open: (String) -> Unit, val sender: (String) -> IntentSender?)

fun postClip(note: Note, openSender: IntentSender?): ClipData =
    if (openSender != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        windowClip(note, openSender)
    } else {
        ClipData.newPlainText(note.user.handle, note.webUrl)
    }

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun windowClip(note: Note, openSender: IntentSender): ClipData {
    val description = ClipDescription(note.user.handle, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN, ClipDescription.MIMETYPE_TEXT_INTENT))
    return ClipData(description, ClipData.Item.Builder().setText(note.webUrl).setIntentSender(openSender).build())
}

fun postDragFlags(opensWindow: Boolean): Int {
    val window = if (opensWindow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) View.DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG else 0
    return View.DRAG_FLAG_GLOBAL or window
}

fun startPostDrag(view: View, note: Note, openSender: IntentSender?, shadow: PostDragShadow) {
    val sender = openSender.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM }
    view.startDragAndDrop(postClip(note, sender), shadow, AppDrag, postDragFlags(sender != null))
}

class PostDragShadow(
    private val label: String,
    private val background: Int,
    private val foreground: Int,
    density: Float,
) : View.DragShadowBuilder() {
    private val padding = PADDING_DP * density
    private val radius = RADIUS_DP * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = TEXT_SP * density }
    private val width = ceil(paint.measureText(label) + padding * 2).toInt()
    private val height = ceil(paint.fontMetrics.let { it.descent - it.ascent } + padding * 2).toInt()

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height)
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        paint.color = background
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        paint.color = foreground
        canvas.drawText(label, padding, padding - paint.fontMetrics.ascent, paint)
    }

    private companion object {
        const val PADDING_DP = 12f
        const val RADIUS_DP = 16f
        const val TEXT_SP = 14f
    }
}

fun Modifier.mousePostDrag(note: Note, callbacks: NoteCallbacks): Modifier = this then MousePostDragElement(note, callbacks)

private class MousePostDragElement(val note: Note, val callbacks: NoteCallbacks) : ModifierNodeElement<MousePostDragNode>() {
    override fun create() = MousePostDragNode(note, callbacks)

    override fun update(node: MousePostDragNode) {
        node.note = note
        node.callbacks = callbacks
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "mousePostDrag"
        properties["noteId"] = note.id
    }

    override fun equals(other: Any?): Boolean = other is MousePostDragElement && other.note.id == note.id && other.callbacks === callbacks

    override fun hashCode(): Int = note.id.hashCode() * 31 + System.identityHashCode(callbacks)
}

private class MousePostDragNode(var note: Note, var callbacks: NoteCallbacks) : Modifier.Node(), PointerInputModifierNode, CompositionLocalConsumerModifierNode {
    private var pressedAt: Offset? = null

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        val change = pointerEvent.changes.firstOrNull() ?: return
        if (change.type != PointerType.Mouse) return
        when (pointerEvent.type) {
            PointerEventType.Press -> pressedAt = change.position.takeIf { pointerEvent.buttons.isPrimaryPressed }
            PointerEventType.Release -> pressedAt = null
            PointerEventType.Move -> {
                val start = pressedAt ?: return
                if ((change.position - start).getDistance() < currentValueOf(LocalViewConfiguration).touchSlop) return
                pressedAt = null
                change.consume()
                callbacks.startDrag(note)
            }
        }
    }

    override fun onCancelPointerInput() {
        pressedAt = null
    }
}

fun Modifier.middleClickOpens(note: Note, callbacks: NoteCallbacks, enabled: Boolean = true): Modifier =
    if (enabled) this then MiddleClickElement(note, callbacks) else this

private class MiddleClickElement(val note: Note, val callbacks: NoteCallbacks) : ModifierNodeElement<MiddleClickNode>() {
    override fun create() = MiddleClickNode(note, callbacks)

    override fun update(node: MiddleClickNode) {
        node.note = note
        node.callbacks = callbacks
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "middleClickOpens"
        properties["noteId"] = note.id
    }

    override fun equals(other: Any?): Boolean = other is MiddleClickElement && other.note.id == note.id && other.callbacks === callbacks

    override fun hashCode(): Int = note.id.hashCode() * 31 + System.identityHashCode(callbacks)
}

private class MiddleClickNode(var note: Note, var callbacks: NoteCallbacks) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial || pointerEvent.type != PointerEventType.Press) return
        if (!pointerEvent.buttons.isTertiaryPressed) return
        pointerEvent.changes.forEach { it.consume() }
        callbacks.openInWindow(note)
    }

    override fun onCancelPointerInput() = Unit
}

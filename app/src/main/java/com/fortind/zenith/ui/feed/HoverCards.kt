package com.fortind.zenith.ui.feed

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.fortind.zenith.timeline.NoteUser
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val HOVER_CARD_SHOW_MS = 600L
const val HOVER_CARD_HIDE_MS = 250L

sealed interface HoverTarget {
    data class Profile(val user: NoteUser) : HoverTarget

    data class Reactors(val key: ReactorsKey) : HoverTarget
}

data class ShownHoverCard(val target: HoverTarget, val anchor: IntRect)

@Stable
class HoverCards(private val scope: CoroutineScope) {
    var shown: ShownHoverCard? by mutableStateOf(null)
        private set

    private var pending: HoverTarget? = null
    private var overCard = false
    private var showJob: Job? = null
    private var hideJob: Job? = null

    fun anchorEntered(target: HoverTarget, anchor: IntRect) {
        hideJob?.cancel()
        if (shown?.target == target) return
        showJob?.cancel()
        pending = target
        showJob = scope.launch {
            delay(HOVER_CARD_SHOW_MS)
            pending = null
            overCard = false
            shown = ShownHoverCard(target, anchor)
        }
    }

    fun anchorExited(target: HoverTarget) {
        if (pending == target) {
            showJob?.cancel()
            pending = null
        }
        if (shown?.target == target) scheduleHide()
    }

    fun cardHovered(inside: Boolean) {
        overCard = inside
        if (inside) hideJob?.cancel() else scheduleHide()
    }

    fun dismiss() {
        showJob?.cancel()
        hideJob?.cancel()
        pending = null
        overCard = false
        shown = null
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(HOVER_CARD_HIDE_MS)
            if (!overCard) shown = null
        }
    }
}

val LocalHoverCards = staticCompositionLocalOf<HoverCards?> { null }

fun Modifier.hoverCard(target: HoverTarget): Modifier = this then HoverCardElement(target)

private data class HoverCardElement(val target: HoverTarget) : ModifierNodeElement<HoverCardNode>() {
    override fun create() = HoverCardNode(target)

    override fun update(node: HoverCardNode) {
        if (node.target != target) node.leave()
        node.target = target
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "hoverCard"
        properties["target"] = target
    }
}

private class HoverCardNode(var target: HoverTarget) : Modifier.Node(), PointerInputModifierNode, CompositionLocalConsumerModifierNode {
    private var inside = false

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        if (pointerEvent.changes.firstOrNull()?.type != PointerType.Mouse) return
        val cards = currentValueOf(LocalHoverCards) ?: return
        when (pointerEvent.type) {
            PointerEventType.Enter -> {
                inside = true
                val rect = requireLayoutCoordinates().boundsInWindow()
                cards.anchorEntered(target, IntRect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt()))
            }
            PointerEventType.Exit -> leave()
            PointerEventType.Press -> {
                inside = false
                cards.dismiss()
            }
        }
    }

    fun leave() {
        if (!inside) return
        inside = false
        if (isAttached) currentValueOf(LocalHoverCards)?.anchorExited(target)
    }

    override fun onCancelPointerInput() = leave()

    override fun onDetach() = leave()
}

package com.fortind.zenith.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

val LocalHostedPopups = compositionLocalOf<HostedPopups?> { null }

@Stable
class PopupAnchor {
    internal var coordinates: LayoutCoordinates? = null
    internal var moved: (() -> Unit)? = null
}

@Stable
internal class HostedEntry(val owner: Any, content: @Composable () -> Unit, bounds: IntRect) {
    var content by mutableStateOf(content)
    var bounds by mutableStateOf(bounds)
}

@Stable
class HostedPopups {
    private var layer: LayoutCoordinates? = null
    private val shown = mutableStateMapOf<Any, HostedEntry>()

    internal val entries: Collection<HostedEntry> get() = shown.values

    internal fun attach(coordinates: LayoutCoordinates) {
        layer = coordinates
    }

    internal fun show(owner: Any, anchor: PopupAnchor, content: @Composable () -> Unit) {
        val bounds = boundsOf(anchor)
        val entry = shown[owner]
        if (entry == null) {
            shown[owner] = HostedEntry(owner, content, bounds ?: IntRect.Zero)
        } else {
            entry.content = content
            if (bounds != null) entry.bounds = bounds
        }
    }

    internal fun move(owner: Any, anchor: PopupAnchor) {
        val entry = shown[owner] ?: return
        boundsOf(anchor)?.let { entry.bounds = it }
    }

    internal fun clear(owner: Any) {
        shown.remove(owner)
    }

    private fun boundsOf(anchor: PopupAnchor): IntRect? {
        val layer = layer?.takeIf { it.isAttached } ?: return null
        val target = anchor.coordinates?.takeIf { it.isAttached } ?: return null
        val rect = layer.localBoundingBoxOf(target, clipBounds = false)
        return IntRect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt())
    }
}

fun Modifier.hostedPopupAnchor(anchor: PopupAnchor): Modifier = onPlaced {
    anchor.coordinates = it
    anchor.moved?.invoke()
}

@Composable
fun HostedPopup(anchor: PopupAnchor, open: Boolean, content: @Composable () -> Unit) {
    val host = LocalHostedPopups.current
    if (host == null) {
        if (open) content()
    } else {
        HostedPopupEntry(host, anchor, open, content)
    }
}

@Composable
private fun HostedPopupEntry(host: HostedPopups, anchor: PopupAnchor, open: Boolean, content: @Composable () -> Unit) {
    val owner = remember { Any() }
    DisposableEffect(host, owner, anchor) {
        onDispose {
            anchor.moved = null
            host.clear(owner)
        }
    }
    SideEffect {
        if (open) {
            host.show(owner, anchor, content)
            anchor.moved = { host.move(owner, anchor) }
        } else {
            anchor.moved = null
            host.clear(owner)
        }
    }
}

@Composable
fun HostedPopupsLayer(popups: HostedPopups) {
    Box(Modifier.fillMaxSize().onPlaced { popups.attach(it) }) {
        popups.entries.forEach { entry ->
            key(entry.owner) {
                Box(
                    Modifier.layout { measurable, _ ->
                        val bounds = entry.bounds
                        val placeable = measurable.measure(Constraints.fixed(bounds.width, bounds.height))
                        layout(bounds.width, bounds.height) { placeable.place(bounds.left, bounds.top) }
                    },
                ) {
                    entry.content()
                }
            }
        }
    }
}

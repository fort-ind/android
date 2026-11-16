package com.fortind.zenith.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import android.view.PointerIcon as SystemPointerIcon

object Cursors {
    val ResizeHorizontal = PointerIcon(SystemPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
    val ZoomIn = PointerIcon(SystemPointerIcon.TYPE_ZOOM_IN)
    val ZoomOut = PointerIcon(SystemPointerIcon.TYPE_ZOOM_OUT)
    val Grab = PointerIcon(SystemPointerIcon.TYPE_GRAB)
    val Grabbing = PointerIcon(SystemPointerIcon.TYPE_GRABBING)
    val Wait = PointerIcon(SystemPointerIcon.TYPE_WAIT)
}

fun Modifier.handCursor(enabled: Boolean = true): Modifier =
    pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)

fun Modifier.waitCursor(busy: Boolean): Modifier =
    if (busy) pointerHoverIcon(Cursors.Wait, overrideDescendants = true) else this

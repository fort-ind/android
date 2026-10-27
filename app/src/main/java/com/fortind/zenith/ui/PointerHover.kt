package com.fortind.zenith.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.pointerHover(onHoverChanged: (Boolean) -> Unit): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.first().type != PointerType.Mouse) continue
                when (event.type) {
                    PointerEventType.Enter -> onHoverChanged(true)
                    PointerEventType.Exit -> onHoverChanged(false)
                }
            }
        }
    }
    .pointerHoverIcon(PointerIcon.Hand)

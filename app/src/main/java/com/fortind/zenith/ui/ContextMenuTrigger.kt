package com.fortind.zenith.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset

fun Modifier.contextMenuTrigger(enabled: Boolean = true, onOpen: (DpOffset) -> Unit): Modifier = this
    .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                val position = event.changes.first().position
                event.changes.forEach { it.consume() }
                onOpen(DpOffset(position.x.toDp(), (position.y - size.height).toDp()))
            }
        }
    }
    .onKeyEvent { event ->
        val isMenuKey = event.key == Key.Menu || (event.isShiftPressed && event.key == Key.F10)
        if (enabled && event.type == KeyEventType.KeyDown && isMenuKey) {
            onOpen(DpOffset.Zero)
            true
        } else {
            false
        }
    }

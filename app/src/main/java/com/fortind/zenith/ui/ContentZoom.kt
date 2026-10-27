package com.fortind.zenith.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private const val MinZoom = 0.75f
private const val MaxZoom = 2f

@Composable
fun ZoomableContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    val base = LocalDensity.current
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                        val delta = event.changes.first().scrollDelta.y
                        zoom = (zoom * (1f - delta * 0.1f)).coerceIn(MinZoom, MaxZoom)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(base.density * zoom, base.fontScale), content = content)
    }
}

package com.fortind.zenith.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow

@Stable
class TopBarReveal internal constructor(private val canScrollBack: State<() -> Boolean>) {
    private val wheelRequests = Channel<Unit>(Channel.CONFLATED)

    internal val requests: Flow<Unit> = wheelRequests.receiveAsFlow()

    internal fun onWheelUp() {
        if (!canScrollBack.value()) wheelRequests.trySend(Unit)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberTopBarReveal(
    behavior: TopAppBarScrollBehavior,
    reduceMotion: Boolean,
    contentKey: Any? = null,
    canScrollBack: () -> Boolean,
): TopBarReveal {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val spec by rememberUpdatedState(if (reduceMotion) snap() else spatialSpec)
    val currentKey by rememberUpdatedState(contentKey)
    val currentCanScrollBack = rememberUpdatedState(canScrollBack)
    val reveal = remember { TopBarReveal(currentCanScrollBack) }

    LaunchedEffect(behavior, reveal) {
        merge(
            reveal.requests,
            snapshotFlow { currentKey }.drop(1).map { },
            snapshotFlow { currentCanScrollBack.value() }.drop(1).filter { !it }.map { },
        ).collectLatest {
            val state = behavior.state
            if (state.heightOffset != 0f) {
                animate(state.heightOffset, 0f, animationSpec = spec) { value, _ -> state.heightOffset = value }
            }
        }
    }
    return reveal
}

fun Modifier.revealTopBarOnWheel(reveal: TopBarReveal): Modifier = pointerInput(reveal) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll || event.changes.any { it.isConsumed }) continue
            if (event.changes.first().scrollDelta.y < 0f) reveal.onWheelUp()
        }
    }
}

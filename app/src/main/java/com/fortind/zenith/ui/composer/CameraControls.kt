package com.fortind.zenith.ui.composer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.media.CaptureTimer
import com.fortind.zenith.media.activeZoomStop
import com.fortind.zenith.media.exposureText
import com.fortind.zenith.media.isAtZoomStop
import com.fortind.zenith.media.zoomText
import com.fortind.zenith.ui.feed.LabelTooltip
import com.fortind.zenith.ui.handCursor
import kotlin.math.roundToInt

internal enum class FocusPhase { Focusing, Focused, Failed }

internal data class FocusSpot(val id: Int, val position: Offset, val phase: FocusPhase, val locked: Boolean)

internal val CameraScrim = Color.Black.copy(alpha = 0.55f)
internal val CameraPanelScrim = Color.Black.copy(alpha = 0.85f)

@Composable
internal fun ZoomStops(stops: List<Float>, ratio: Float, onSelect: (Float) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val active = activeZoomStop(stops, ratio)
    val groupLabel = stringResource(R.string.camera_zoom)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(CameraScrim, CircleShape)
            .padding(4.dp)
            .selectableGroup()
            .semantics { contentDescription = groupLabel },
    ) {
        stops.forEachIndexed { index, stop ->
            val selected = index == active
            val shown = if (selected) ratio else stop
            val text = zoomText(shown, locale)
            val description = stringResource(R.string.camera_zoom_to, zoomText(stop, locale))
            val diameter by animateDpAsState(if (selected) 44.dp else 36.dp, label = "zoomStop")
            Surface(
                selected = selected && isAtZoomStop(stop, ratio),
                onClick = { onSelect(stop) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(diameter)
                    .handCursor()
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = description
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selected) stringResource(R.string.camera_zoom_ratio, text) else text,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExposureSlider(
    index: Int,
    range: IntRange,
    value: Float,
    onIndexChange: (Int) -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val label = stringResource(R.string.camera_exposure)
    val text = exposureText(value, locale)
    val state = stringResource(R.string.camera_exposure_value, text)
    Surface(
        shape = CircleShape,
        color = CameraScrim,
        contentColor = Color.White,
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        ) {
            Icon(Icons.Default.Exposure, contentDescription = null, modifier = Modifier.size(20.dp))
            CameraSlider(
                value = index.toFloat(),
                range = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                onChange = { onIndexChange(it.roundToInt()) },
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .handCursor()
                    .semantics {
                        contentDescription = label
                        stateDescription = state
                    },
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(min = 36.dp).clearAndSetSemantics { },
            )
        }
    }
}

@Composable
internal fun CameraSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val state = remember(range, steps) { SliderState(value.coerceIn(range), steps, range) }
    LaunchedEffect(state, value) {
        if (!state.isDragging) state.value = value.coerceIn(range)
    }
    Slider(
        state = state,
        onValueChange = {
            state.value = it
            onChange(it)
        },
        colors = cameraSliderColors(),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
private fun cameraSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Color.White,
    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
internal fun FocusRing(spot: FocusSpot, reduceMotion: Boolean) {
    val motion = MaterialTheme.motionScheme
    val scale = remember(spot.id) { Animatable(if (reduceMotion) 1f else FOCUS_START_SCALE) }
    LaunchedEffect(spot.id) {
        if (!reduceMotion) scale.animateTo(1f, motion.fastSpatialSpec())
    }
    val ringAlpha = when (spot.phase) {
        FocusPhase.Focusing -> 0.8f
        FocusPhase.Focused -> 1f
        FocusPhase.Failed -> 0.4f
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val ringHalf = (FOCUS_RING_SIZE / 2).roundToPx()
                val x = (spot.position.x - placeable.width / 2f).roundToInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                val y = (spot.position.y - ringHalf).roundToInt()
                    .coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
            },
    ) {
        Box(
            modifier = Modifier
                .size(FOCUS_RING_SIZE)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .border(2.dp, Color.White.copy(alpha = ringAlpha), CircleShape),
        )
        if (spot.locked) {
            Surface(
                shape = CircleShape,
                color = CameraScrim,
                contentColor = Color.White,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.camera_focus_locked), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun NightHint(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.camera_night_hint)) },
        leadingIcon = { Icon(Icons.Default.NightsStay, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = CameraScrim,
            labelColor = Color.White,
            leadingIconContentColor = Color.White,
        ),
        border = null,
        modifier = modifier.handCursor().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
internal fun CountdownNumber(seconds: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.camera_countdown, seconds, seconds)
    Surface(
        shape = CircleShape,
        color = CameraScrim,
        contentColor = Color.White,
        modifier = modifier
            .size(120.dp)
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Assertive
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(seconds.toString(), style = MaterialTheme.typography.displayLarge)
        }
    }
}

@Composable
internal fun TimerBadge(timer: CaptureTimer, modifier: Modifier = Modifier) {
    val long = pluralStringResource(R.plurals.camera_timer_long, timer.seconds, timer.seconds)
    val description = stringResource(R.string.camera_timer_set, long)
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.camera_timer_short, timer.seconds),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
internal fun CameraToggleIcon(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    LabelTooltip(label) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = IconButtonDefaults.iconToggleButtonColors(
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.38f),
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.handCursor(enabled),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun CameraRoundButton(label: String, icon: ImageVector, onClick: () -> Unit, checked: Boolean = false) {
    LabelTooltip(label) {
        FilledIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else CameraScrim,
                contentColor = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
            ),
            modifier = Modifier.size(56.dp).handCursor(),
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
internal fun ShutterCluster(
    vertical: Boolean,
    start: (@Composable () -> Unit)?,
    end: (@Composable () -> Unit)?,
    shutter: @Composable () -> Unit,
) {
    val slot: @Composable ((@Composable () -> Unit)?) -> Unit = { content ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) { content?.invoke() }
    }
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            slot(start)
            shutter()
            slot(end)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            slot(start)
            shutter()
            slot(end)
        }
    }
}

internal suspend fun PointerInputScope.detectFocusGestures(onTap: (Offset) -> Unit, onLongPress: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result = GestureOutcome.Aborted
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (event.changes.size > 1 || change == null) break
                if (!change.pressed) {
                    result = GestureOutcome.Tap
                    break
                }
                if ((change.position - down.position).getDistance() > slop) break
            }
            result
        }
        when (outcome) {
            GestureOutcome.Tap -> onTap(down.position)
            null -> onLongPress(down.position)
            GestureOutcome.Aborted -> Unit
        }
    }
}

internal suspend fun PointerInputScope.detectWheel(onWheel: (Float) -> Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                if (delta != 0f) {
                    onWheel(delta)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

private enum class GestureOutcome { Tap, Aborted }

private val FOCUS_RING_SIZE = 72.dp
private const val FOCUS_START_SCALE = 1.3f

@Composable
internal fun CameraNotice(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Black.copy(alpha = 0.75f),
        contentColor = Color.White,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.heightIn(min = 20.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

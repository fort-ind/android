@file:OptIn(ExperimentalMaterial3Api::class)

package com.fortind.zenith.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.composer.Attachment
import com.fortind.zenith.ui.rememberReduceMotion
import kotlin.math.roundToInt

@Composable
fun ModalOverlay(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(motion.defaultEffectsSpec<Float>()),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(motion.fastEffectsSpec<Float>()),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .safeDrawingPadding()
                .padding(24.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title },
            ) {
                Column(modifier = Modifier.padding(24.dp), content = content)
            }
        }
    }
}

@Composable
fun BottomSheetOverlay(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val motion = MaterialTheme.motionScheme
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reduceMotion) EnterTransition.None else fadeIn(motion.defaultEffectsSpec<Float>()),
            exit = if (reduceMotion) ExitTransition.None else fadeOut(motion.fastEffectsSpec<Float>()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = if (reduceMotion) EnterTransition.None else slideInVertically(motion.defaultSpatialSpec()) { it },
            exit = if (reduceMotion) ExitTransition.None else slideOutVertically(motion.fastSpatialSpec()) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomSheet(title = title, reduceMotion = reduceMotion, onDismiss = onDismiss, content = content)
        }
    }
}

@Composable
private fun BottomSheet(
    title: String,
    reduceMotion: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val closeLabel = stringResource(R.string.composer_close)
    Surface(
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .widthIn(max = SheetMaxWidth)
            .fillMaxWidth()
            .onSizeChanged { height = it.height }
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = title },
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState { dragOffset = (dragOffset + it).coerceAtLeast(0f) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            when {
                                velocity > SheetDismissVelocity || dragOffset > height * SheetDismissFraction -> onDismiss()
                                reduceMotion -> dragOffset = 0f
                                else -> animate(dragOffset, 0f, animationSpec = motion.fastSpatialSpec()) { value, _ -> dragOffset = value }
                            }
                        },
                    )
                    .clickable(onClickLabel = closeLabel, onClick = onDismiss),
            ) {
                BottomSheetDefaults.DragHandle()
            }
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
        }
    }
}

private val SheetMaxWidth = 640.dp
private const val SheetDismissVelocity = 1200f
private const val SheetDismissFraction = 0.4f

@Composable
fun DiscardPrompt(
    visible: Boolean,
    draftable: Boolean,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(if (draftable) R.string.composer_discard_title else R.string.composer_discard_edit_title)
    ModalOverlay(visible = visible, title = title, onDismiss = onDismiss) {
        val confirmFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { confirmFocus.requestFocus() }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (draftable) {
            Text(
                stringResource(R.string.composer_discard_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.composer_discard)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onConfirm, modifier = Modifier.focusRequester(confirmFocus)) {
                Text(stringResource(if (draftable) R.string.composer_keep_draft else R.string.composer_keep_editing))
            }
        }
    }
}

@Composable
fun AttachmentDetailsPrompt(
    attachment: Attachment?,
    maxAltLength: Int,
    onSave: (altText: String, isSensitive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.attachment_details_title)
    ModalOverlay(visible = attachment != null, title = title, onDismiss = onDismiss) {
        val current = attachment ?: return@ModalOverlay
        val altState = rememberTextFieldState(current.altText)
        var sensitive by rememberSaveable(current.localId) { mutableStateOf(current.isSensitive) }
        val altFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { altFocus.requestFocus() }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.attachment_details_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        TextField(
            state = altState,
            label = { Text(stringResource(R.string.attachment_details_label)) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6),
            supportingText = { Text("${codePointLength(altState.text)} / $maxAltLength") },
            modifier = Modifier.fillMaxWidth().focusRequester(altFocus),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.attachment_details_sensitive), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.attachment_details_sensitive_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = sensitive, onCheckedChange = { sensitive = it })
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.attachment_details_cancel)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onSave(altState.text.toString(), sensitive) }) {
                Text(stringResource(R.string.attachment_details_save))
            }
        }
    }
}

package com.fortind.zenith.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fortind.zenith.R
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.media.NetworkImage
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@Composable
fun ImageViewer(
    files: List<NoteFile>,
    initialIndex: Int,
    animate: Boolean,
    onDismiss: () -> Unit,
    onOpenOriginal: (String) -> Unit,
) {
    if (files.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, files.lastIndex)) { files.size }
        val scope = rememberCoroutineScope()
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        val current = files[pagerState.currentPage.coerceIn(0, files.lastIndex)]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        Key.DirectionLeft -> {
                            scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                            true
                        }
                        Key.DirectionRight -> {
                            scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(files.lastIndex)) }
                            true
                        }
                        else -> false
                    }
                },
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { files[it].id }) { page ->
                ZoomableImage(files[page], animate)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ViewerButton(stringResource(R.string.viewer_close), Icons.Default.Close, onDismiss)
                if (files.size > 1) {
                    Text(
                        stringResource(R.string.viewer_counter, pagerState.currentPage + 1, files.size),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                ViewerButton(stringResource(R.string.viewer_open_original), Icons.AutoMirrored.Filled.OpenInNew) {
                    onOpenOriginal(current.url)
                }
            }
            current.altText?.let { alt ->
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(16.dp)
                        .widthIn(max = 640.dp),
                ) {
                    Text(alt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ViewerButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.5f), contentColor = Color.White) {
        TooltipIconButton(label = label, icon = icon, onClick = onClick, tint = Color.White)
    }
}

@Composable
private fun ZoomableImage(file: NoteFile, animate: Boolean) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = (size.width * (zoom - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (size.height * (zoom - 1f) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        val applied = next / scale
        val fromCenter = centroid - Offset(size.width / 2f, size.height / 2f)
        offset = clamp((offset - fromCenter) * applied + fromCenter + panChange, next)
        scale = next
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = DOUBLE_TAP_ZOOM
                        offset = clamp((Offset(size.width / 2f, size.height / 2f) - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                    }
                })
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                            val delta = event.changes.first().scrollDelta.y
                            val next = (scale * if (delta < 0) 1.1f else 0.9f).coerceIn(1f, MAX_ZOOM)
                            scale = next
                            offset = clamp(offset, next)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .transformable(state = transformState, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        NetworkImage(
            url = file.url,
            contentDescription = file.altText,
            animate = animate,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

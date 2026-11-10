package com.fortind.zenith.ui.feed

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.media.NetworkImage
import com.fortind.zenith.ui.motion.MediaKey
import com.fortind.zenith.ui.motion.SharedSpec
import com.fortind.zenith.ui.motion.mediaTarget
import com.fortind.zenith.ui.motion.rememberSharedSpec
import androidx.core.view.WindowCompat
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@Composable
fun ImageViewer(
    files: List<NoteFile>,
    initialIndex: Int,
    animate: Boolean,
    heroSource: Any?,
    returnFocus: FocusRequester?,
    visibility: AnimatedVisibilityScope,
    onDismiss: () -> Unit,
    onOpenOriginal: (String) -> Unit,
) {
    if (files.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, files.lastIndex)) { files.size }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val showing = visibility.transition.targetState == EnterExitState.Visible
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    var leaving by remember { mutableStateOf(false) }
    val dismiss = {
        leaving = true
        onDismiss()
        val keyboard = inputMode.inputMode == InputMode.Keyboard
        if (!keyboard || returnFocus?.requestFocus() != true) focusManager.clearFocus(force = true)
    }
    val keyboardMode = inputMode.inputMode == InputMode.Keyboard
    LaunchedEffect(keyboardMode, showing) { if (keyboardMode && showing) focusRequester.requestFocus() }
    DarkSystemBars()
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = showing,
        onBackCompleted = dismiss,
    )
    val current = files[pagerState.currentPage.coerceIn(0, files.lastIndex)]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {}
            .focusProperties { onExit = { if (!leaving) cancelFocusChange() } }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp && showing) dismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        dismiss()
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
            val file = files[page]
            val heroKey = heroSource?.takeIf { page == pagerState.currentPage }?.let { MediaKey(it, file.id) }
            ZoomableImage(file, animate, rememberSharedSpec(heroKey, visibility))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ViewerButton(stringResource(R.string.viewer_close), Icons.Default.Close, dismiss)
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

@Composable
private fun DarkSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
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
private fun ZoomableImage(file: NoteFile, animate: Boolean, hero: SharedSpec?) {
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
        val ratio = file.aspectRatio
        NetworkImage(
            url = file.url,
            contentDescription = file.altText,
            animate = animate,
            contentScale = if (ratio != null) ContentScale.Crop else ContentScale.Fit,
            placeholderUrl = file.thumbnailUrl ?: file.url,
            modifier = Modifier
                .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
                .mediaTarget(hero)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

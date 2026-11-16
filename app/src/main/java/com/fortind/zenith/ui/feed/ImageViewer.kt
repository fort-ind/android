package com.fortind.zenith.ui.feed

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.Cursors
import com.fortind.zenith.ui.hasHardwareKeyboard
import com.fortind.zenith.ui.media.CreateMediaDocument
import com.fortind.zenith.ui.media.NetworkImage
import com.fortind.zenith.ui.media.canPrint
import com.fortind.zenith.ui.media.downloadToDocument
import com.fortind.zenith.ui.media.dragShadowSource
import com.fortind.zenith.ui.media.loadBitmap
import com.fortind.zenith.ui.media.mediaDragSource
import com.fortind.zenith.ui.media.mediaPayload
import com.fortind.zenith.ui.media.printBitmap
import com.fortind.zenith.ui.media.rememberTouchDragStarter
import com.fortind.zenith.ui.media.rememberWindowFullscreenState
import com.fortind.zenith.ui.media.saveTargetFor
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
private const val KEY_ZOOM_STEP = 1.25f
private const val WHEEL_ZOOM_STEP = 1.1f

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
    val hardwareKeyboard = hasHardwareKeyboard(LocalConfiguration.current)
    LaunchedEffect(keyboardMode, hardwareKeyboard, showing) { if ((keyboardMode || hardwareKeyboard) && showing) focusRequester.requestFocus() }
    DarkSystemBars()
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = showing,
        onBackCompleted = dismiss,
    )
    val current = files[pagerState.currentPage.coerceIn(0, files.lastIndex)]
    val zooms = remember(files) { files.associate { it.id to ZoomState() } }
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }
    val fullscreen = rememberWindowFullscreenState()
    var pendingSaveId by rememberSaveable { mutableStateOf<String?>(null) }
    val savedMessage = stringResource(R.string.viewer_saved)
    val saveFailedMessage = stringResource(R.string.viewer_save_failed)
    val printFailedMessage = stringResource(R.string.viewer_print_failed)
    val saveLauncher = rememberLauncherForActivityResult(CreateMediaDocument()) { target ->
        val file = files.firstOrNull { it.id == pendingSaveId }
        pendingSaveId = null
        if (target != null && file != null) {
            scope.launch {
                val client = (context.applicationContext as ZenithApplication).container.okHttpClient
                val saved = downloadToDocument(context, client, file.url, target)
                snackbar.showSnackbar(if (saved) savedMessage else saveFailedMessage)
            }
        }
    }
    val save = { file: NoteFile ->
        pendingSaveId = file.id
        saveLauncher.launch(saveTargetFor(file))
    }
    val printable = remember { canPrint() }
    val print = { file: NoteFile ->
        scope.launch {
            val bitmap = loadBitmap(context, file.url)
            if (bitmap == null || activity == null) {
                snackbar.showSnackbar(printFailedMessage)
            } else {
                printBitmap(activity, saveTargetFor(file).fileName, bitmap)
            }
        }
        Unit
    }
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
                val zoom = zooms[current.id]
                if (event.isCtrlPressed) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.S -> {
                            save(current)
                            true
                        }
                        Key.P -> {
                            if (printable) print(current)
                            printable
                        }
                        Key.Equals, Key.Plus, Key.NumPadAdd -> {
                            zoom?.zoomBy(KEY_ZOOM_STEP)
                            true
                        }
                        Key.Minus, Key.NumPadSubtract -> {
                            zoom?.zoomBy(1f / KEY_ZOOM_STEP)
                            true
                        }
                        Key.Zero, Key.NumPad0 -> {
                            zoom?.reset()
                            true
                        }
                        else -> false
                    }
                }
                when (event.key) {
                    Key.F -> {
                        if (fullscreen.canToggle) fullscreen.toggle()
                        fullscreen.canToggle
                    }
                    Key.Escape -> {
                        if (fullscreen.active) {
                            fullscreen.exit()
                            return@onPreviewKeyEvent true
                        }
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
            ZoomableImage(file, zooms.getValue(file.id), animate, rememberSharedSpec(heroKey, visibility))
        }
        if (files.size > 1) {
            Text(
                stringResource(R.string.viewer_counter, pagerState.currentPage + 1, files.size),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 20.dp),
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewerButton(stringResource(R.string.viewer_save), Icons.Default.Download) { save(current) }
                if (printable) ViewerButton(stringResource(R.string.viewer_print), Icons.Default.Print) { print(current) }
                if (fullscreen.canToggle) {
                    ViewerButton(
                        stringResource(if (fullscreen.active) R.string.viewer_exit_fullscreen else R.string.viewer_fullscreen),
                        if (fullscreen.active) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        fullscreen::toggle,
                    )
                }
                ViewerButton(stringResource(R.string.viewer_open_original), Icons.AutoMirrored.Filled.OpenInNew) {
                    onOpenOriginal(current.url)
                }
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
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 72.dp))
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

@Stable
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)

    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = (size.width * (zoom - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (size.height * (zoom - 1f) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    fun zoomBy(factor: Float) {
        val next = (scale * factor).coerceIn(1f, MAX_ZOOM)
        offset = clamp(offset * (next / scale), next)
        scale = next
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

@Composable
private fun ZoomableImage(file: NoteFile, zoom: ZoomState, animate: Boolean, hero: SharedSpec?) {
    val context = LocalContext.current
    val touchDrag = rememberTouchDragStarter()
    var pressed by remember { mutableStateOf(false) }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val next = (zoom.scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        val applied = next / zoom.scale
        val fromCenter = centroid - Offset(zoom.size.width / 2f, zoom.size.height / 2f)
        zoom.offset = zoom.clamp((zoom.offset - fromCenter) * applied + fromCenter + panChange, next)
        zoom.scale = next
    }
    val zoomed = zoom.scale > 1f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { zoom.size = it }
            .pointerHoverIcon(
                when {
                    zoomed && pressed -> Cursors.Grabbing
                    zoomed -> Cursors.Grab
                    else -> Cursors.ZoomIn
                },
            )
            .mediaDragSource(context, file, enabled = !zoomed)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (zoom.scale > 1f) {
                            zoom.reset()
                        } else {
                            zoom.scale = DOUBLE_TAP_ZOOM
                            zoom.offset = zoom.clamp((Offset(zoom.size.width / 2f, zoom.size.height / 2f) - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                        }
                    },
                    onLongPress = { if (zoom.scale <= 1f) touchDrag.start(mediaPayload(context, file)) },
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> pressed = true
                            PointerEventType.Release -> pressed = false
                        }
                        if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                            val delta = event.changes.first().scrollDelta.y
                            zoom.zoomBy(if (delta < 0) WHEEL_ZOOM_STEP else 1f / WHEEL_ZOOM_STEP)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .transformable(state = transformState, canPan = { zoom.scale > 1f }),
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
                .dragShadowSource(touchDrag)
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        )
    }
}

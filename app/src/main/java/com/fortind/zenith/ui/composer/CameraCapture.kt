package com.fortind.zenith.ui.composer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.NightModeIndicator
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.TorchState
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fortind.zenith.R
import com.fortind.zenith.media.CaptureMode
import com.fortind.zenith.media.CaptureTimer
import com.fortind.zenith.media.FlashChoice
import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.media.VideoOptions
import com.fortind.zenith.media.VideoSupport
import com.fortind.zenith.media.effectiveCaptureMode
import com.fortind.zenith.media.effectiveFlash
import com.fortind.zenith.media.exposureValue
import com.fortind.zenith.media.flashChoices
import com.fortind.zenith.media.keyZoom
import com.fortind.zenith.media.nextFlash
import com.fortind.zenith.media.offeredCaptureModes
import com.fortind.zenith.media.wheelZoom
import com.fortind.zenith.media.zoomStops
import com.fortind.zenith.ui.StandaloneMenuGroup
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.handCursor
import com.fortind.zenith.ui.openAppDetailsSettings
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.shouldExplain
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraCaptureOverlay(
    videoSizeLimitBytes: Long,
    captureMode: CaptureMode,
    onCaptureModeChange: (CaptureMode) -> Unit,
    videoOptions: VideoOptions,
    onVideoOptionsChange: (VideoOptions) -> Unit,
    settingsOpen: Boolean,
    onSettingsOpenChange: (Boolean) -> Unit,
    onCaptured: (LocalMedia) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val inputModes = LocalInputModeManager.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val useCases = remember { CameraUseCases { request -> surfaceRequest = request } }
    var cameraIndex by rememberSaveable { mutableIntStateOf(0) }
    var cameraMenu by remember { mutableStateOf(false) }
    var videoMode by rememberSaveable { mutableStateOf(false) }
    var flashChoice by rememberSaveable { mutableStateOf(FlashChoice.Off) }
    var timer by rememberSaveable { mutableStateOf(CaptureTimer.Off) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var extensionsManager by remember { mutableStateOf<ExtensionsManager?>(null) }
    var cameras by remember { mutableStateOf(emptyList<CameraOption>()) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMillis by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var captureModes by remember { mutableStateOf(emptyList<CaptureMode>()) }
    var activeMode by remember { mutableStateOf(CaptureMode.Off) }
    var brokenModes by remember { mutableStateOf(emptySet<Pair<Int, CaptureMode>>()) }
    var featureSession by remember { mutableStateOf(false) }
    var brokenVideo by remember { mutableStateOf(emptySet<Int>()) }
    val videoSupports = remember { mutableMapOf<Int, VideoSupport>() }
    var videoSupport by remember { mutableStateOf(VideoSupport()) }
    var capturing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableIntStateOf(NO_PROGRESS) }
    var postview by remember { mutableStateOf<ImageBitmap?>(null) }
    var screenFlash by remember { mutableStateOf<ImageCapture.ScreenFlash?>(null) }
    var focusSpot by remember { mutableStateOf<FocusSpot?>(null) }
    var focusSerial by remember { mutableIntStateOf(0) }
    var exposureOpen by remember { mutableStateOf(false) }
    var exposurePinned by remember { mutableStateOf(false) }
    var exposureTouches by remember { mutableIntStateOf(0) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    val exposureInteraction = remember { MutableInteractionSource() }
    var nightHint by remember { mutableStateOf(false) }
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    var rootTop by remember { mutableFloatStateOf(0f) }
    var rootHeight by remember { mutableIntStateOf(0) }
    var panelWasOpen by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    var audioGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var audioBlocked by rememberSaveable { mutableStateOf(false) }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioGranted = granted
        audioBlocked = !granted && !activity.shouldExplain(Manifest.permission.RECORD_AUDIO)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        audioGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        if (audioGranted) audioBlocked = false
    }
    val requestMicrophone = {
        if (audioBlocked) context.openAppDetailsSettings() else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val closed = remember { AtomicBoolean(false) }
    val focus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val transformer = remember { MutableCoordinateTransformer() }
    val landscape = LocalWindowInfo.current.containerSize.let { it.width > it.height }
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    val hinge = posture.hingeList.firstOrNull { !it.isVertical && it.isSeparating }?.takeIf { posture.isTabletop }
    val split = hinge?.bounds?.let { bounds ->
        val top = bounds.top - rootTop
        val bottom = bounds.bottom - rootTop
        (top to bottom).takeIf { top > 0f && bottom < rootHeight }
    }

    val sessionVideo = videoOptions.copy(lowLight = false)
    LaunchedEffect(cameraIndex, videoMode, captureMode, brokenModes, sessionVideo, brokenVideo) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        cameras = cameraOptions(provider.availableCameraInfos)
        when {
            cameras.isEmpty() -> message = R.string.camera_unavailable
            cameraIndex !in cameras.indices -> cameraIndex = 0
            else -> {
                val index = cameraIndex
                val selector = cameras[index].selector
                val extensions = loadExtensions(context, provider)
                extensionsManager = extensions
                val offered = offeredCaptureModes { mode ->
                    extensions != null &&
                        (index to mode) !in brokenModes &&
                        extensions.isExtensionAvailable(selector, mode.extensionMode())
                }
                captureModes = offered
                val mode = if (videoMode) CaptureMode.Off else effectiveCaptureMode(captureMode, offered)
                provider.unbindAll()
                activeMode = CaptureMode.Off
                featureSession = false
                val result = if (videoMode) {
                    val support = videoSupports.getOrPut(index) { queryVideoSupport(provider, selector, useCases) }
                    videoSupport = support
                    val wanted = if (index in brokenVideo) VideoOptions() else videoOptions.effectiveFor(support)
                    bindVideo(provider, lifecycleOwner, selector, useCases, wanted, support)
                } else {
                    bindPhoto(provider, lifecycleOwner, selector, useCases.preview, mode, extensions)
                }
                when (result) {
                    is BindResult.Bound -> {
                        camera = result.camera
                        imageCapture = result.imageCapture
                        videoCapture = result.videoCapture
                        activeMode = result.mode
                        featureSession = result.videoCapture === useCases.featureVideo
                    }
                    BindResult.ModeFailed -> {
                        camera = null
                        brokenModes = brokenModes + (index to mode)
                        notice = R.string.camera_mode_failed
                    }
                    BindResult.VideoOptionsFailed -> {
                        camera = null
                        brokenVideo = brokenVideo + index
                        notice = R.string.camera_video_options_failed
                    }
                    BindResult.Failed -> {
                        camera = null
                        message = R.string.camera_failed
                    }
                }
            }
        }
    }
    DisposableEffect(camera, activeMode, featureSession, cameraIndex) {
        val bound = camera
        val mode = activeMode
        val feature = featureSession
        val index = cameraIndex
        if (bound == null || (mode == CaptureMode.Off && !feature)) return@DisposableEffect onDispose { }
        val observer = Observer<CameraState> { state ->
            val code = state.error?.code
            if (code == CameraState.ERROR_STREAM_CONFIG || code == CameraState.ERROR_CAMERA_FATAL_ERROR) {
                if (mode != CaptureMode.Off) {
                    brokenModes = brokenModes + (index to mode)
                    notice = R.string.camera_mode_failed
                } else {
                    brokenVideo = brokenVideo + index
                    notice = R.string.camera_video_options_failed
                }
            }
        }
        bound.cameraInfo.cameraState.observe(lifecycleOwner, observer)
        onDispose { bound.cameraInfo.cameraState.removeObserver(observer) }
    }

    val info = camera?.cameraInfo
    val zoom = liveValue(remember(info) { info?.zoomState })
    val zoomRatio = zoom?.zoomRatio ?: 1f
    val minZoom = zoom?.minZoomRatio ?: 1f
    val maxZoom = zoom?.maxZoomRatio ?: 1f
    val lensRatios = remember(info) { info?.let(::lensZoomRatios).orEmpty() }
    val stops = remember(minZoom, maxZoom, lensRatios) { zoomStops(minZoom, maxZoom, lensRatios) }
    val torchOn = liveValue(remember(info) { info?.torchState }) == TorchState.ON
    val torchAdjustable = remember(info) { info != null && runCatching { info.isTorchStrengthSupported }.getOrDefault(false) }
    val torchLevel = liveValue(remember(info, torchAdjustable) { info?.takeIf { torchAdjustable }?.torchStrengthLevel })
    val exposureState = info?.exposureState
    val exposureSupported = exposureState?.isExposureCompensationSupported == true
    val nightIndicatorSupported = remember(info) { info != null && runCatching { info.isNightModeIndicatorSupported }.getOrDefault(false) }
    val nightIndicator = liveValue(remember(info, nightIndicatorSupported) { if (nightIndicatorSupported) info?.nightModeIndicator else null })
    val extensionInfo = remember(info, activeMode, extensionsManager) {
        if (activeMode == CaptureMode.Off || info == null) null else runCatching { extensionsManager?.getCameraExtensionsInfo(info) }.getOrNull()
    }
    val strengthAvailable = remember(extensionInfo) { runCatching { extensionInfo?.isExtensionStrengthAvailable }.getOrNull() == true }
    val extensionStrength = liveValue(remember(extensionInfo, strengthAvailable) { if (strengthAvailable) extensionInfo?.extensionStrength else null })
    val effectiveVideo = videoOptions.effectiveFor(videoSupport)
    val lowLightWanted = videoMode && effectiveVideo.lowLight && !torchOn
    val front = cameras.getOrNull(cameraIndex)?.facing == CameraFacing.Front
    val hasFlashUnit = info?.hasFlashUnit() == true
    val flashOptions = flashChoices(hasFlashUnit, front && screenFlash != null)
    val flash = effectiveFlash(flashChoice, flashOptions)
    val busy = capturing || countdown != null

    LaunchedEffect(camera) {
        focusSpot = null
        exposureOpen = false
        exposureIndex = camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0
    }
    LaunchedEffect(camera, lowLightWanted) {
        val bound = camera ?: return@LaunchedEffect
        if (lowLightSupported(bound.cameraInfo)) bound.cameraControl.enableLowLightBoostAsync(lowLightWanted)
    }
    LaunchedEffect(imageCapture, flash, hasFlashUnit, screenFlash) {
        val capture = imageCapture ?: return@LaunchedEffect
        capture.screenFlash = screenFlash
        val mode = when {
            flash == FlashChoice.Off -> ImageCapture.FLASH_MODE_OFF
            !hasFlashUnit -> ImageCapture.FLASH_MODE_SCREEN
            flash == FlashChoice.Auto -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_ON
        }
        try {
            capture.flashMode = mode
        } catch (_: IllegalArgumentException) {
            capture.flashMode = ImageCapture.FLASH_MODE_OFF
        }
    }
    LaunchedEffect(nightIndicator) {
        if (nightIndicator == NightModeIndicator.RECOMMENDED) {
            delay(NIGHT_HINT_SHOW_MILLIS)
            nightHint = true
        } else if (nightHint) {
            delay(NIGHT_HINT_HIDE_MILLIS)
            nightHint = false
        }
    }
    val exposureDragged by exposureInteraction.collectIsDraggedAsState()
    val exposurePressed by exposureInteraction.collectIsPressedAsState()
    val exposureFocused by exposureInteraction.collectIsFocusedAsState()
    LaunchedEffect(exposureOpen, exposurePinned, exposureTouches, exposureDragged, exposurePressed, exposureFocused) {
        if (exposureOpen && !exposurePinned && !exposureDragged && !exposurePressed && !exposureFocused) {
            delay(EXPOSURE_HIDE_MILLIS)
            exposureOpen = false
        }
    }
    LaunchedEffect(focusSpot?.id, focusSpot?.phase) {
        val spot = focusSpot ?: return@LaunchedEffect
        if (!spot.locked && spot.phase != FocusPhase.Focusing) {
            delay(FOCUS_RING_MILLIS)
            if (focusSpot?.id == spot.id) focusSpot = null
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MILLIS)
            notice = null
        }
    }
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) {
            panelWasOpen = true
            if (inputModes.inputMode == InputMode.Keyboard) {
                withFrameNanos { }
                requestFocusSafely(panelFocus)
            }
        } else if (panelWasOpen) {
            panelWasOpen = false
            requestFocusSafely(focus)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) {
        onDispose {
            closed.set(true)
            recording?.stop()
            unbindIfReady(context)
        }
    }

    val takePhoto: () -> Unit = takePhoto@{
        val capture = imageCapture ?: return@takePhoto
        if (capturing) return@takePhoto
        capturing = true
        captureProgress = NO_PROGRESS
        postview = null
        val file = captureFile(context, "IMG", "jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onCaptureProcessProgressed(progress: Int) {
                    captureProgress = progress
                }

                override fun onPostviewBitmapAvailable(bitmap: Bitmap) {
                    if (!closed.get()) postview = bitmap.asImageBitmap()
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturing = false
                    postview = null
                    if (closed.get()) {
                        file.delete()
                    } else {
                        onCaptured(LocalMedia(Uri.fromFile(file).toString(), file.name, "image/jpeg", file.length()))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    postview = null
                    file.delete()
                    notice = R.string.camera_failed
                }
            },
        )
    }
    val toggleRecording: () -> Unit = toggleRecording@{
        val active = recording
        if (active != null) {
            active.stop()
            return@toggleRecording
        }
        val output = videoCapture?.output ?: return@toggleRecording
        val file = captureFile(context, "VID", "mp4")
        val options = FileOutputOptions.Builder(file).setFileSizeLimit(videoSizeLimitBytes).build()
        var pending = output.prepareRecording(context, options)
        if (audioGranted) {
            pending = try {
                pending.withAudioEnabled(muted)
            } catch (_: SecurityException) {
                pending
            }
        }
        recordedMillis = 0L
        paused = false
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Status -> recordedMillis = event.recordingStats.recordedDurationNanos / NANOS_PER_MILLI
                is VideoRecordEvent.Pause -> paused = true
                is VideoRecordEvent.Resume -> paused = false
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    paused = false
                    val limitReached = event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                    if (closed.get()) {
                        file.delete()
                    } else if (!event.hasError() || limitReached) {
                        onCaptured(LocalMedia(Uri.fromFile(file).toString(), file.name, "video/mp4", file.length()))
                    } else {
                        file.delete()
                        message = R.string.camera_failed
                    }
                }
                else -> Unit
            }
        }
    }
    val fire: () -> Unit = { if (videoMode) toggleRecording() else takePhoto() }
    val shutter: () -> Unit = {
        when {
            countdown != null -> countdown = null
            recording != null -> toggleRecording()
            capturing -> Unit
            timer != CaptureTimer.Off -> countdown = timer.seconds
            else -> fire()
        }
    }
    LaunchedEffect(countdown) {
        val left = countdown ?: return@LaunchedEffect
        if (left <= 0) {
            countdown = null
            fire()
        } else {
            delay(COUNTDOWN_TICK_MILLIS)
            countdown = left - 1
        }
    }
    val toggleMute: () -> Unit = {
        muted = !muted
        try {
            recording?.mute(muted)
        } catch (_: IllegalStateException) {
        }
    }
    val togglePause: () -> Unit = {
        val active = recording
        if (paused) active?.resume() else active?.pause()
    }
    val currentZoom: () -> Float = { camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: zoomRatio }
    val stepZoom: (Float) -> Unit = { target ->
        zoomJob?.cancel()
        camera?.cameraControl?.setZoomRatio(target.coerceIn(minZoom, maxZoom))
    }
    val animateZoom: (Float) -> Unit = animateZoom@{ target ->
        val control = camera?.cameraControl ?: return@animateZoom
        zoomJob?.cancel()
        zoomJob = scope.launch {
            if (reduceMotion) {
                control.setZoomRatio(target.coerceIn(minZoom, maxZoom))
            } else {
                Animatable(currentZoom()).animateTo(target, motion.defaultEffectsSpec()) {
                    control.setZoomRatio(value.coerceIn(minZoom, maxZoom))
                }
            }
        }
    }
    val meter: (Offset, Boolean) -> Unit = meter@{ tap, lock ->
        val bound = camera ?: return@meter
        val request = surfaceRequest ?: return@meter
        val point = with(transformer) { tap.transform() }
        val factory = SurfaceOrientedMeteringPointFactory(request.resolution.width.toFloat(), request.resolution.height.toFloat())
        val builder = FocusMeteringAction.Builder(factory.createPoint(point.x, point.y))
        if (lock) {
            builder.setLockingMode(FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB)
            builder.disableAutoCancel()
        }
        if (focusSpot?.locked == true) bound.cameraControl.cancelFocusAndMetering()
        val id = focusSerial + 1
        focusSerial = id
        focusSpot = FocusSpot(id, tap, FocusPhase.Focusing, lock)
        val future = bound.cameraControl.startFocusAndMetering(builder.build())
        future.addListener(
            {
                val focused = try {
                    future.get().isFocusSuccessful
                } catch (_: ExecutionException) {
                    false
                } catch (_: CancellationException) {
                    false
                }
                val spot = focusSpot
                if (spot != null && spot.id == id) focusSpot = spot.copy(phase = if (focused) FocusPhase.Focused else FocusPhase.Failed)
            },
            mainExecutor,
        )
        if (lock) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (exposureSupported) {
            exposureOpen = true
            exposurePinned = false
            exposureTouches++
        }
    }
    val latestMeter by rememberUpdatedState(meter)
    val latestWheel by rememberUpdatedState<(Float) -> Unit> { delta -> stepZoom(wheelZoom(currentZoom(), delta, minZoom, maxZoom)) }
    val onCameraKey: (KeyEvent) -> Boolean = onCameraKey@{ event ->
        val down = event.type == KeyEventType.KeyDown
        val first = down && event.nativeKeyEvent.repeatCount == 0
        when (event.key) {
            Key.VolumeUp, Key.VolumeDown, Key.Camera -> {
                if (first) shutter()
                true
            }
            Key.Enter, Key.Spacebar -> {
                if (first) shutter()
                down
            }
            Key.Plus, Key.Equals, Key.NumPadAdd -> {
                if (down) stepZoom(keyZoom(currentZoom(), zoomIn = true, minRatio = minZoom, maxRatio = maxZoom))
                down
            }
            Key.Minus, Key.NumPadSubtract -> {
                if (down) stepZoom(keyZoom(currentZoom(), zoomIn = false, minRatio = minZoom, maxRatio = maxZoom))
                down
            }
            Key.Zero, Key.NumPad0 -> {
                if (first) animateZoom(1f)
                down
            }
            else -> false
        }
    }

    val viewfinder: @Composable (Modifier, ContentScale) -> Unit = { modifier, scale ->
        Box(modifier = modifier) {
            surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    coordinateTransformer = transformer,
                    contentScale = scale,
                    isPinchToZoomEnabled = true,
                    onScreenFlashReady = { screenFlash = it },
                    onRelease = { screenFlash = null },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(request) {
                            detectFocusGestures(onTap = { latestMeter(it, false) }, onLongPress = { latestMeter(it, true) })
                        }
                        .pointerInput(Unit) { detectWheel { latestWheel(it) } },
                )
            }
            postview?.let { Image(it, contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize()) }
            focusSpot?.let { FocusRing(it, reduceMotion) }
        }
    }
    val centerOverlays: @Composable BoxScope.() -> Unit = {
        (notice ?: message)?.let { res ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(stringResource(res), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }
        countdown?.takeIf { it > 0 }?.let { CountdownNumber(it, modifier = Modifier.align(Alignment.Center)) }
    }
    val topBar: @Composable (Modifier) -> Unit = { modifier ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            TooltipIconButton(
                label = stringResource(R.string.camera_close),
                icon = Icons.Default.Close,
                onClick = onClose,
                tint = Color.White,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val active = recording
                when {
                    active != null -> RecordingBadge(recordedMillis, paused)
                    timer != CaptureTimer.Off -> TimerBadge(timer)
                }
            }
            if (exposureSupported) {
                CameraToggleIcon(
                    label = stringResource(R.string.camera_exposure),
                    icon = Icons.Default.Exposure,
                    checked = exposureOpen,
                    onCheckedChange = { open ->
                        exposureOpen = open
                        exposurePinned = open
                    },
                )
            }
            if (videoMode && hasFlashUnit) {
                CameraToggleIcon(
                    label = stringResource(if (torchOn) R.string.camera_torch_on else R.string.camera_torch_off),
                    icon = if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    checked = torchOn,
                    onCheckedChange = { on -> camera?.cameraControl?.enableTorch(on) },
                )
            } else if (!videoMode && flashOptions.isNotEmpty()) {
                val (flashLabel, flashIcon) = when (flash) {
                    FlashChoice.On -> R.string.camera_flash_on to Icons.Default.FlashOn
                    FlashChoice.Auto -> R.string.camera_flash_auto to Icons.Default.FlashAuto
                    FlashChoice.Off -> R.string.camera_flash_off to Icons.Default.FlashOff
                }
                TooltipIconButton(
                    label = stringResource(flashLabel),
                    icon = flashIcon,
                    onClick = { flashChoice = nextFlash(flash, flashOptions) },
                    tint = Color.White,
                )
            }
            if (cameras.size > 1 && recording == null && !busy) {
                Box {
                    TooltipIconButton(
                        label = stringResource(R.string.camera_flip),
                        icon = Icons.Default.FlipCameraAndroid,
                        onClick = { if (cameras.size > 2) cameraMenu = true else cameraIndex = (cameraIndex + 1) % cameras.size },
                        tint = Color.White,
                    )
                    DropdownMenuPopup(expanded = cameraMenu && cameras.size > 2, onDismissRequest = { cameraMenu = false }) {
                        StandaloneMenuGroup {
                            cameras.forEachIndexed { index, option ->
                                SelectableDropdownMenuItem(
                                    selected = index == cameraIndex,
                                    text = { Text(cameraLabel(cameras, index)) },
                                    shapes = MenuDefaults.itemShape(index, cameras.size),
                                    leadingIcon = { Icon(option.icon(), contentDescription = null) },
                                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = {
                                        cameraMenu = false
                                        cameraIndex = index
                                    },
                                    modifier = Modifier.handCursor(),
                                )
                            }
                        }
                    }
                }
            }
            CameraToggleIcon(
                label = stringResource(R.string.camera_settings),
                icon = Icons.Default.Settings,
                checked = settingsOpen,
                onCheckedChange = onSettingsOpenChange,
            )
        }
    }
    val showModes = !videoMode && captureModes.size > 1
    val selectedMode = effectiveCaptureMode(captureMode, captureModes)
    val showNightHint = nightHint && !videoMode && !busy && CaptureMode.Night in captureModes &&
        activeMode != CaptureMode.Night && activeMode != CaptureMode.Auto
    val framingControls: @Composable () -> Unit = {
        if (showNightHint) NightHint(onClick = { onCaptureModeChange(CaptureMode.Night) })
        if (exposureOpen && exposureSupported) {
            val range = exposureState.exposureCompensationRange
            val step = exposureState.exposureCompensationStep
            ExposureSlider(
                index = exposureIndex,
                range = range.lower..range.upper,
                value = exposureValue(exposureIndex, step.numerator, step.denominator),
                onIndexChange = { value ->
                    exposureIndex = value
                    exposureTouches++
                    camera?.cameraControl?.setExposureCompensationIndex(value)
                },
                interactionSource = exposureInteraction,
            )
        }
        if (stops.isNotEmpty()) ZoomStops(stops = stops, ratio = zoomRatio, onSelect = animateZoom)
    }
    val modeToggle: @Composable () -> Unit = {
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            listOf(false to R.string.camera_photo, true to R.string.camera_video).forEachIndexed { index, (video, label) ->
                val enabled = recording == null && !busy
                ToggleButton(
                    checked = videoMode == video,
                    onCheckedChange = {
                        videoMode = video
                        val firstAsk = !audioBlocked && !activity.shouldExplain(Manifest.permission.RECORD_AUDIO)
                        if (video && !audioGranted && firstAsk) audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = enabled,
                    shapes = if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                    modifier = Modifier.handCursor(enabled).semantics { role = Role.RadioButton },
                ) {
                    Text(stringResource(label))
                }
            }
        }
    }
    val shutterCluster: @Composable (Boolean) -> Unit = { vertical ->
        val shutterButton: @Composable () -> Unit = {
            ShutterButton(
                video = videoMode,
                recording = recording != null,
                counting = countdown != null,
                busy = capturing,
                progress = captureProgress,
                onClick = shutter,
            )
        }
        if (videoMode) {
            ShutterCluster(
                vertical = vertical,
                start = if (audioGranted) {
                    {
                        CameraRoundButton(
                            label = stringResource(if (muted) R.string.camera_unmute else R.string.camera_mute),
                            icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                            onClick = toggleMute,
                            checked = muted,
                        )
                    }
                } else {
                    null
                },
                end = if (recording != null) {
                    {
                        CameraRoundButton(
                            label = stringResource(if (paused) R.string.camera_resume_recording else R.string.camera_pause_recording),
                            icon = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            onClick = togglePause,
                        )
                    }
                } else {
                    null
                },
                shutter = shutterButton,
            )
        } else {
            shutterButton()
        }
    }
    val statusNotices: @Composable () -> Unit = {
        if (videoMode && !audioGranted && recording == null) {
            MicrophoneNotice(blocked = audioBlocked, onClick = requestMicrophone)
        }
        if (capturing && activeMode != CaptureMode.Off) {
            CameraNotice(stringResource(if (postview != null) R.string.camera_processing_postview else R.string.camera_processing))
        }
    }
    val modeChips: @Composable (Boolean, Modifier) -> Unit = { vertical, modifier ->
        CaptureModeChips(
            modes = captureModes,
            selected = selectedMode,
            vertical = vertical,
            enabled = !busy,
            onSelect = onCaptureModeChange,
            modifier = modifier,
        )
    }
    val settingsLayer: @Composable BoxScope.(Modifier) -> Unit = { panelModifier ->
        if (settingsOpen) {
            Box(modifier = Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { onSettingsOpenChange(false) } })
        }
        AnimatedVisibility(
            visible = settingsOpen,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f))
            },
            exit = if (reduceMotion) ExitTransition.None else fadeOut(motion.fastEffectsSpec()),
            modifier = panelModifier,
        ) {
            CameraSettingsPanel(
                timer = timer,
                onTimerChange = { timer = it },
                timerEnabled = countdown == null,
                effectStrength = if (!videoMode && strengthAvailable && extensionStrength != null) {
                    SliderSetting(R.string.camera_effect_strength, extensionStrength, 0..MAX_EXTENSION_STRENGTH) { value ->
                        val bound = camera ?: return@SliderSetting
                        runCatching { extensionsManager?.getCameraExtensionsControl(bound.cameraControl)?.setExtensionStrength(value) }
                    }
                } else {
                    null
                },
                video = if (videoMode) {
                    VideoSettings(
                        options = videoOptions,
                        support = videoSupport,
                        recording = recording != null,
                        torchOn = torchOn,
                        onChange = onVideoOptionsChange,
                    )
                } else {
                    null
                },
                torchBrightness = if (videoMode && torchOn && torchAdjustable && info != null) {
                    SliderSetting(R.string.camera_torch_brightness, torchLevel ?: info.maxTorchStrengthLevel, 1..info.maxTorchStrengthLevel) { level ->
                        camera?.cameraControl?.setTorchStrengthLevel(level)
                    }
                } else {
                    null
                },
                focusRequester = panelFocus,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                rootTop = coordinates.positionInWindow().y
                rootHeight = coordinates.size.height
            }
            .focusRequester(focus)
            .focusable()
            .onKeyEvent(onCameraKey),
    ) {
        if (split != null) {
            val (hingeTop, hingeBottom) = split
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(with(density) { hingeTop.toDp() })) {
                    viewfinder(Modifier.fillMaxSize(), ContentScale.Fit)
                    centerOverlays()
                }
                Spacer(modifier = Modifier.height(with(density) { (hingeBottom - hingeTop).toDp() }))
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                ) {
                    val minHeight = maxHeight
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = minHeight)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        topBar(Modifier.fillMaxWidth())
                        framingControls()
                        statusNotices()
                        if (showModes) modeChips(false, Modifier)
                        modeToggle()
                        shutterCluster(false)
                    }
                    settingsLayer(Modifier.align(Alignment.TopEnd).padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 8.dp))
                }
            }
        } else {
            viewfinder(Modifier.fillMaxSize(), ContentScale.Crop)
            topBar(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(8.dp))
            centerOverlays()
            if (landscape) {
                if (showModes) {
                    modeChips(true, Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(24.dp))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                ) { framingControls() }
                BoxWithConstraints(modifier = Modifier.align(Alignment.CenterEnd).safeDrawingPadding()) {
                    val minHeight = maxHeight
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = minHeight)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        statusNotices()
                        modeToggle()
                        shutterCluster(true)
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    framingControls()
                    statusNotices()
                    if (showModes) modeChips(false, Modifier)
                    modeToggle()
                    shutterCluster(false)
                }
            }
            settingsLayer(
                Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun MicrophoneNotice(blocked: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Black.copy(alpha = 0.75f),
        contentColor = Color.White,
        modifier = Modifier.widthIn(max = 360.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Default.MicOff, contentDescription = null)
            Text(
                text = stringResource(if (blocked) R.string.camera_microphone_blocked else R.string.camera_microphone_needed),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun CaptureModeChips(
    modes: List<CaptureMode>,
    selected: CaptureMode,
    vertical: Boolean,
    enabled: Boolean,
    onSelect: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupLabel = stringResource(R.string.camera_modes)
    val selectedContainer = MaterialTheme.colorScheme.secondaryContainer
    val selectedContent = MaterialTheme.colorScheme.onSecondaryContainer
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Black.copy(alpha = SCRIM_ALPHA),
        labelColor = Color.White,
        disabledContainerColor = Color.Black.copy(alpha = DISABLED_SCRIM_ALPHA),
        disabledLabelColor = Color.White.copy(alpha = DISABLED_CONTENT_ALPHA),
        selectedContainerColor = selectedContainer,
        selectedLabelColor = selectedContent,
        disabledSelectedContainerColor = selectedContainer.copy(alpha = DISABLED_CONTENT_ALPHA),
    )
    val chips: @Composable () -> Unit = {
        modes.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                enabled = enabled,
                label = { Text(stringResource(mode.labelRes())) },
                colors = colors,
                border = null,
                modifier = Modifier.handCursor(enabled).semantics { role = Role.RadioButton },
            )
        }
    }
    val group = Modifier.selectableGroup().semantics { contentDescription = groupLabel }
    if (vertical) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.then(group).verticalScroll(rememberScrollState()),
        ) { chips() }
    } else {
        Box(modifier = modifier.widthIn(max = 480.dp), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = group.horizontalScroll(rememberScrollState()),
            ) { chips() }
        }
    }
}

@Composable
private fun ShutterButton(video: Boolean, recording: Boolean, counting: Boolean, busy: Boolean, progress: Int, onClick: () -> Unit) {
    val label = stringResource(
        when {
            counting -> R.string.camera_countdown_cancel
            !video -> R.string.camera_take_photo
            recording -> R.string.camera_stop_recording
            else -> R.string.camera_start_recording
        },
    )
    val processing = stringResource(R.string.camera_processing)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = !busy, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(8.dp)
            .semantics {
                contentDescription = label
                if (busy) stateDescription = processing
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(if (recording) RoundedCornerShape(12.dp) else CircleShape)
                .background((if (video) Color.Red else Color.White).copy(alpha = if (busy || counting) DISABLED_CONTENT_ALPHA else 1f)),
        )
        if (counting) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Black)
        }
        if (busy) {
            val track = Color.White.copy(alpha = 0.3f)
            if (progress in 0..MAX_PROGRESS) {
                CircularProgressIndicator(
                    progress = { progress / MAX_PROGRESS.toFloat() },
                    color = Color.White,
                    trackColor = track,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(color = Color.White, trackColor = track, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun RecordingBadge(millis: Long, paused: Boolean) {
    val time = formatElapsed(millis)
    val description = stringResource(if (paused) R.string.camera_recording_paused else R.string.camera_recording_time, time)
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (paused) {
                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
            } else {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
            }
            Text(time, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun requestFocusSafely(requester: FocusRequester) {
    try {
        requester.requestFocus()
    } catch (_: IllegalStateException) {
    }
}

private fun lensZoomRatios(info: CameraInfo): List<Float> = runCatching {
    info.physicalCameraInfos.map { it.intrinsicZoomRatio }
}.getOrDefault(emptyList())

private fun formatElapsed(millis: Long): String {
    val seconds = millis / MILLIS_PER_SECOND
    return "%d:%02d".format(Locale.US, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun captureFile(context: Context, prefix: String, extension: String): File {
    val directory = File(context.cacheDir, "captures").also { it.mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(directory, "${prefix}_$stamp.$extension")
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val NOTICE_MILLIS = 4000L
private const val NO_PROGRESS = -1
private const val MAX_PROGRESS = 100
private const val MAX_EXTENSION_STRENGTH = 100
private const val SCRIM_ALPHA = 0.55f
private const val DISABLED_SCRIM_ALPHA = 0.35f
private const val DISABLED_CONTENT_ALPHA = 0.5f
private const val EXPOSURE_HIDE_MILLIS = 4000L
private const val FOCUS_RING_MILLIS = 1500L
private const val NIGHT_HINT_SHOW_MILLIS = 1000L
private const val NIGHT_HINT_HIDE_MILLIS = 2000L
private const val COUNTDOWN_TICK_MILLIS = 1000L

private fun CaptureMode.labelRes(): Int = when (this) {
    CaptureMode.Off -> R.string.camera_mode_off
    CaptureMode.Auto -> R.string.camera_mode_auto
    CaptureMode.Hdr -> R.string.camera_mode_hdr
    CaptureMode.Night -> R.string.camera_mode_night
    CaptureMode.Portrait -> R.string.camera_mode_portrait
    CaptureMode.FaceRetouch -> R.string.camera_mode_face_retouch
}

private fun unbindIfReady(context: Context) {
    val future = ProcessCameraProvider.getInstance(context)
    if (!future.isDone) return
    try {
        future.get().unbindAll()
    } catch (_: ExecutionException) {
    } catch (_: CancellationException) {
    }
}

private suspend fun loadExtensions(context: Context, provider: ProcessCameraProvider): ExtensionsManager? =
    try {
        ExtensionsManager.getInstance(context.applicationContext, provider)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

class CameraOption(val selector: CameraSelector, val facing: CameraFacing)

enum class CameraFacing { Back, Front, External }

@androidx.annotation.OptIn(markerClass = [ExperimentalLensFacing::class])
private fun facingOf(info: CameraInfo): CameraFacing? = when (info.lensFacing) {
    CameraSelector.LENS_FACING_BACK -> CameraFacing.Back
    CameraSelector.LENS_FACING_FRONT -> CameraFacing.Front
    CameraSelector.LENS_FACING_EXTERNAL -> CameraFacing.External
    else -> null
}

private fun cameraOptions(infos: List<CameraInfo>): List<CameraOption> =
    infos.mapNotNull { info -> facingOf(info)?.let { CameraOption(info.cameraSelector, it) } }.sortedBy { it.facing.ordinal }

private fun CameraOption.icon(): ImageVector = when (facing) {
    CameraFacing.Back -> Icons.Default.CameraRear
    CameraFacing.Front -> Icons.Default.CameraFront
    CameraFacing.External -> Icons.Default.Videocam
}

@Composable
private fun cameraLabel(cameras: List<CameraOption>, index: Int): String {
    val option = cameras[index]
    val sameKind = cameras.filter { it.facing == option.facing }
    val number = sameKind.indexOf(option) + 1
    val base = when (option.facing) {
        CameraFacing.Back -> R.string.camera_back
        CameraFacing.Front -> R.string.camera_front
        CameraFacing.External -> R.string.camera_external
    }
    return if (sameKind.size > 1) stringResource(R.string.camera_numbered, stringResource(base), number) else stringResource(base)
}

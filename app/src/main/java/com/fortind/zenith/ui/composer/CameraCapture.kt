package com.fortind.zenith.ui.composer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionSessionConfig
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.media.effectiveCaptureMode
import com.fortind.zenith.media.offeredCaptureModes
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.StandaloneMenuGroup
import com.fortind.zenith.ui.handCursor
import com.fortind.zenith.ui.openAppDetailsSettings
import com.fortind.zenith.ui.shouldExplain
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun CameraCaptureOverlay(
    videoSizeLimitBytes: Long,
    captureMode: CaptureMode,
    onCaptureModeChange: (CaptureMode) -> Unit,
    onCaptured: (LocalMedia) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var cameraIndex by rememberSaveable { mutableIntStateOf(0) }
    var cameraMenu by remember { mutableStateOf(false) }
    var videoMode by rememberSaveable { mutableStateOf(false) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameras by remember { mutableStateOf(emptyList<CameraOption>()) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMillis by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var captureModes by remember { mutableStateOf(emptyList<CaptureMode>()) }
    var activeMode by remember { mutableStateOf(CaptureMode.Off) }
    var brokenModes by remember { mutableStateOf(emptySet<Pair<Int, CaptureMode>>()) }
    var capturing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableIntStateOf(NO_PROGRESS) }
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
    val transformer = remember { MutableCoordinateTransformer() }
    val landscape = LocalWindowInfo.current.containerSize.let { it.width > it.height }

    val preview = remember { Preview.Builder().build().also { it.setSurfaceProvider { request -> surfaceRequest = request } } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val videoCapture = remember {
        val quality = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
        )
        VideoCapture.withOutput(Recorder.Builder().setQualitySelector(quality).build())
    }

    LaunchedEffect(cameraIndex, videoMode, captureMode, brokenModes) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        cameras = cameraOptions(provider.availableCameraInfos)
        when {
            cameras.isEmpty() -> message = R.string.camera_unavailable
            cameraIndex !in cameras.indices -> cameraIndex = 0
            else -> {
                val selector = cameras[cameraIndex].selector
                val extensions = loadExtensions(context, provider)
                val offered = offeredCaptureModes { mode ->
                    extensions != null &&
                        (cameraIndex to mode) !in brokenModes &&
                        extensions.isExtensionAvailable(selector, mode.extensionMode())
                }
                captureModes = offered
                val mode = if (videoMode) CaptureMode.Off else effectiveCaptureMode(captureMode, offered)
                provider.unbindAll()
                activeMode = CaptureMode.Off
                camera = try {
                    if (mode != CaptureMode.Off && extensions != null) {
                        val session = ExtensionSessionConfig(mode.extensionMode(), extensions, preview, imageCapture)
                        provider.bindToLifecycle(lifecycleOwner, selector, session).also { activeMode = mode }
                    } else {
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, if (videoMode) videoCapture else imageCapture)
                    }
                } catch (_: IllegalArgumentException) {
                    if (mode == CaptureMode.Off) {
                        message = R.string.camera_failed
                    } else {
                        brokenModes = brokenModes + (cameraIndex to mode)
                        notice = R.string.camera_mode_failed
                    }
                    null
                }
            }
        }
    }
    DisposableEffect(camera, activeMode, cameraIndex) {
        val bound = camera
        val mode = activeMode
        if (bound == null || mode == CaptureMode.Off) return@DisposableEffect onDispose { }
        val observer = Observer<CameraState> { state ->
            val code = state.error?.code
            if (code == CameraState.ERROR_STREAM_CONFIG || code == CameraState.ERROR_CAMERA_FATAL_ERROR) {
                brokenModes = brokenModes + (cameraIndex to mode)
                notice = R.string.camera_mode_failed
            }
        }
        bound.cameraInfo.cameraState.observe(lifecycleOwner, observer)
        onDispose { bound.cameraInfo.cameraState.removeObserver(observer) }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MILLIS)
            notice = null
        }
    }
    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) {
        onDispose {
            closed.set(true)
            recording?.stop()
            unbindIfReady(context)
        }
    }

    val takePhoto: () -> Unit = takePhoto@{
        if (capturing) return@takePhoto
        capturing = true
        captureProgress = NO_PROGRESS
        val file = captureFile(context, "IMG", "jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onCaptureProcessProgressed(progress: Int) {
                    captureProgress = progress
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturing = false
                    if (closed.get()) {
                        file.delete()
                    } else {
                        onCaptured(LocalMedia(Uri.fromFile(file).toString(), file.name, "image/jpeg", file.length()))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    file.delete()
                    notice = R.string.camera_failed
                }
            },
        )
    }
    val toggleRecording = {
        val active = recording
        if (active != null) {
            active.stop()
        } else {
            val file = captureFile(context, "VID", "mp4")
            val output = FileOutputOptions.Builder(file).setFileSizeLimit(videoSizeLimitBytes).build()
            var pending = videoCapture.output.prepareRecording(context, output)
            if (audioGranted) {
                pending = try {
                    pending.withAudioEnabled()
                } catch (_: SecurityException) {
                    pending
                }
            }
            recordedMillis = 0L
            recording = pending.start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Status -> recordedMillis = event.recordingStats.recordedDurationNanos / NANOS_PER_MILLI
                    is VideoRecordEvent.Finalize -> {
                        recording = null
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
    }
    val shutter = { if (videoMode) toggleRecording() else takePhoto() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.Spacebar)) {
                    shutter()
                    true
                } else {
                    false
                }
            },
    ) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                coordinateTransformer = transformer,
                modifier = Modifier.fillMaxSize().pointerInput(camera, request) {
                    detectTapGestures { tap ->
                        val active = camera ?: return@detectTapGestures
                        val point = with(transformer) { tap.transform() }
                        val factory = SurfaceOrientedMeteringPointFactory(request.resolution.width.toFloat(), request.resolution.height.toFloat())
                        active.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(factory.createPoint(point.x, point.y)).build())
                    }
                },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(8.dp),
        ) {
            TooltipIconButton(
                label = stringResource(R.string.camera_close),
                icon = Icons.Default.Close,
                onClick = onClose,
                tint = Color.White,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                recording?.let { RecordingBadge(recordedMillis) }
            }
            if (!videoMode) {
                val (flashLabel, flashIcon) = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> R.string.camera_flash_on to Icons.Default.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> R.string.camera_flash_auto to Icons.Default.FlashAuto
                    else -> R.string.camera_flash_off to Icons.Default.FlashOff
                }
                TooltipIconButton(
                    label = stringResource(flashLabel),
                    icon = flashIcon,
                    onClick = { flashMode = nextFlashMode(flashMode) },
                    tint = Color.White,
                )
            }
            if (cameras.size > 1 && recording == null && !capturing) {
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
        }
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
        val showModes = !videoMode && captureModes.size > 1
        val selectedMode = effectiveCaptureMode(captureMode, captureModes)
        val controls: @Composable () -> Unit = {
            if (videoMode && !audioGranted && recording == null) {
                MicrophoneNotice(blocked = audioBlocked, onClick = requestMicrophone)
            }
            if (capturing && activeMode != CaptureMode.Off) {
                ProcessingNotice()
            }
            if (showModes && !landscape) {
                CaptureModeChips(
                    modes = captureModes,
                    selected = selectedMode,
                    vertical = false,
                    enabled = !capturing,
                    onSelect = onCaptureModeChange,
                )
            }
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                listOf(false to R.string.camera_photo, true to R.string.camera_video).forEachIndexed { index, (video, label) ->
                    ToggleButton(
                        checked = videoMode == video,
                        onCheckedChange = {
                            videoMode = video
                            val firstAsk = !audioBlocked && !activity.shouldExplain(Manifest.permission.RECORD_AUDIO)
                            if (video && !audioGranted && firstAsk) audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        enabled = recording == null && !capturing,
                        shapes = if (index == 0) {
                            ButtonGroupDefaults.connectedLeadingButtonShapes()
                        } else {
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        },
                        modifier = Modifier.handCursor(recording == null && !capturing).semantics { role = Role.RadioButton },
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
            ShutterButton(
                video = videoMode,
                recording = recording != null,
                busy = capturing,
                progress = captureProgress,
                onClick = shutter,
            )
        }
        if (showModes && landscape) {
            CaptureModeChips(
                modes = captureModes,
                selected = selectedMode,
                vertical = true,
                enabled = !capturing,
                onSelect = onCaptureModeChange,
                modifier = Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(24.dp),
            )
        }
        if (landscape) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(24.dp),
            ) { controls() }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
            ) { controls() }
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
private fun ProcessingNotice() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Black.copy(alpha = 0.75f),
        contentColor = Color.White,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(R.string.camera_processing),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ShutterButton(video: Boolean, recording: Boolean, busy: Boolean, progress: Int, onClick: () -> Unit) {
    val label = stringResource(
        when {
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
                .background((if (video) Color.Red else Color.White).copy(alpha = if (busy) DISABLED_CONTENT_ALPHA else 1f)),
        )
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
private fun RecordingBadge(millis: Long) {
    val time = formatElapsed(millis)
    val description = stringResource(R.string.camera_recording_time, time)
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
            Text(time, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun nextFlashMode(current: Int): Int = when (current) {
    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
    else -> ImageCapture.FLASH_MODE_OFF
}

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
private const val SCRIM_ALPHA = 0.55f
private const val DISABLED_SCRIM_ALPHA = 0.35f
private const val DISABLED_CONTENT_ALPHA = 0.5f

private fun CaptureMode.labelRes(): Int = when (this) {
    CaptureMode.Off -> R.string.camera_mode_off
    CaptureMode.Auto -> R.string.camera_mode_auto
    CaptureMode.Hdr -> R.string.camera_mode_hdr
    CaptureMode.Night -> R.string.camera_mode_night
    CaptureMode.Portrait -> R.string.camera_mode_portrait
    CaptureMode.FaceRetouch -> R.string.camera_mode_face_retouch
}

private fun CaptureMode.extensionMode(): Int = when (this) {
    CaptureMode.Off -> ExtensionMode.NONE
    CaptureMode.Auto -> ExtensionMode.AUTO
    CaptureMode.Hdr -> ExtensionMode.HDR
    CaptureMode.Night -> ExtensionMode.NIGHT
    CaptureMode.Portrait -> ExtensionMode.BOKEH
    CaptureMode.FaceRetouch -> ExtensionMode.FACE_RETOUCH
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

package com.fortind.zenith.ui.composer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fortind.zenith.R
import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.ui.feed.TooltipIconButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureOverlay(
    videoSizeLimitBytes: Long,
    onCaptured: (LocalMedia) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var videoMode by rememberSaveable { mutableStateOf(false) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lenses by remember { mutableStateOf(emptyList<Int>()) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMillis by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<Int?>(null) }
    var audioGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { audioGranted = it }
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

    LaunchedEffect(lensFacing, videoMode) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        lenses = listOf(CameraSelector.LENS_FACING_BACK, CameraSelector.LENS_FACING_FRONT)
            .filter { provider.hasCamera(CameraSelector.Builder().requireLensFacing(it).build()) }
        when {
            lenses.isEmpty() -> message = R.string.camera_unavailable
            lensFacing !in lenses -> lensFacing = lenses.first()
            else -> {
                provider.unbindAll()
                camera = try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                        preview,
                        if (videoMode) videoCapture else imageCapture,
                    )
                } catch (_: IllegalArgumentException) {
                    message = R.string.camera_failed
                    null
                }
            }
        }
    }
    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) {
        onDispose {
            closed.set(true)
            recording?.stop()
            ProcessCameraProvider.getInstance(context).let { future -> if (future.isDone) future.get().unbindAll() }
        }
    }

    val takePhoto = {
        val file = captureFile(context, "IMG", "jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (closed.get()) {
                        file.delete()
                    } else {
                        onCaptured(LocalMedia(Uri.fromFile(file).toString(), file.name, "image/jpeg", file.length()))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    message = R.string.camera_failed
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
            .onPreviewKeyEvent { event ->
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
            if (lenses.size > 1 && recording == null) {
                TooltipIconButton(
                    label = stringResource(R.string.camera_flip),
                    icon = Icons.Default.FlipCameraAndroid,
                    onClick = { lensFacing = lenses.first { it != lensFacing } },
                    tint = Color.White,
                )
            }
        }
        message?.let { res ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(stringResource(res), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }
        val controls: @Composable () -> Unit = {
            SingleChoiceSegmentedButtonRow {
                listOf(false to R.string.camera_photo, true to R.string.camera_video).forEachIndexed { index, (video, label) ->
                    SegmentedButton(
                        selected = videoMode == video,
                        onClick = {
                            videoMode = video
                            if (video && !audioGranted) audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        enabled = recording == null,
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            ShutterButton(video = videoMode, recording = recording != null, onClick = shutter)
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
private fun ShutterButton(video: Boolean, recording: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when {
            !video -> R.string.camera_take_photo
            recording -> R.string.camera_stop_recording
            else -> R.string.camera_start_recording
        },
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(8.dp)
            .semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(if (recording) RoundedCornerShape(12.dp) else CircleShape)
                .background(if (video) Color.Red else Color.White),
        )
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

package com.fortind.zenith.ui.composer

import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionSessionConfig
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.GroupableFeatures
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fortind.zenith.media.CaptureMode
import com.fortind.zenith.media.Stabilization
import com.fortind.zenith.media.VideoOptions
import com.fortind.zenith.media.VideoSupport

internal class CameraUseCases(onSurfaceRequest: (SurfaceRequest) -> Unit) {
    val preview: Preview = Preview.Builder().build().also { it.setSurfaceProvider(onSurfaceRequest) }
    val plainVideo: VideoCapture<Recorder> = VideoCapture.withOutput(
        Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build(),
    )
    val featureVideo: VideoCapture<Recorder> = VideoCapture.withOutput(Recorder.Builder().build())
}

internal sealed interface BindResult {
    class Bound(
        val camera: Camera,
        val imageCapture: ImageCapture?,
        val videoCapture: VideoCapture<Recorder>?,
        val mode: CaptureMode,
    ) : BindResult

    data object ModeFailed : BindResult
    data object VideoOptionsFailed : BindResult
    data object Failed : BindResult
}

internal fun bindPhoto(
    provider: ProcessCameraProvider,
    owner: LifecycleOwner,
    selector: CameraSelector,
    preview: Preview,
    mode: CaptureMode,
    extensions: ExtensionsManager?,
): BindResult {
    val extension = extensions?.takeIf { mode != CaptureMode.Off }
    val postview = extension != null && runCatching {
        val info = provider.getCameraInfo(selector, ExtensionSessionConfig(mode.extensionMode(), extension, preview))
        ImageCapture.getImageCaptureCapabilities(info).isPostviewSupported
    }.getOrDefault(false)
    val capture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setPostviewEnabled(postview)
        .build()
    return try {
        val camera = if (extension != null) {
            provider.bindToLifecycle(owner, selector, ExtensionSessionConfig(mode.extensionMode(), extension, preview, capture))
        } else {
            provider.bindToLifecycle(owner, selector, preview, capture)
        }
        BindResult.Bound(camera, capture, null, if (extension != null) mode else CaptureMode.Off)
    } catch (_: IllegalArgumentException) {
        if (extension != null) BindResult.ModeFailed else BindResult.Failed
    }
}

internal fun queryVideoSupport(provider: ProcessCameraProvider, selector: CameraSelector, useCases: CameraUseCases): VideoSupport {
    val info = runCatching { provider.getCameraInfo(selector) }.getOrNull() ?: return VideoSupport()
    val featureUseCases = listOf(useCases.preview, useCases.featureVideo)
    fun supports(vararg features: GroupableFeature): Boolean = runCatching {
        info.isSessionConfigSupported(SessionConfig(featureUseCases, requiredFeatureGroup = features.toSet()))
    }.getOrDefault(false)
    val stabilization = when {
        supports(GroupableFeature.PREVIEW_STABILIZATION) -> Stabilization.Preview
        supports(GroupableFeatures.VIDEO_STABILIZATION) -> Stabilization.Recording
        else -> null
    }
    val fps60 = supports(GroupableFeature.FPS_60)
    return VideoSupport(
        fps60 = fps60,
        stabilization = stabilization,
        fps60WithStabilization = fps60 && stabilization != null && supports(GroupableFeature.FPS_60, stabilization.feature()),
        lowLight = lowLightSupported(info),
    )
}

internal fun bindVideo(
    provider: ProcessCameraProvider,
    owner: LifecycleOwner,
    selector: CameraSelector,
    useCases: CameraUseCases,
    options: VideoOptions,
    support: VideoSupport,
): BindResult {
    val required = buildSet {
        if (options.fps60) add(GroupableFeature.FPS_60)
        if (options.stabilize) support.stabilization?.let { add(it.feature()) }
    }
    return try {
        if (required.isEmpty()) {
            val camera = provider.bindToLifecycle(owner, selector, useCases.preview, useCases.plainVideo)
            BindResult.Bound(camera, null, useCases.plainVideo, CaptureMode.Off)
        } else {
            val config = SessionConfig(
                useCases = listOf<UseCase>(useCases.preview, useCases.featureVideo),
                requiredFeatureGroup = required,
                preferredFeatureGroup = listOf(
                    GroupableFeatures.FHD_RECORDING,
                    GroupableFeatures.HD_RECORDING,
                    GroupableFeatures.SD_RECORDING,
                ),
            )
            val camera = provider.bindToLifecycle(owner, selector, config)
            BindResult.Bound(camera, null, useCases.featureVideo, CaptureMode.Off)
        }
    } catch (_: IllegalArgumentException) {
        if (required.isEmpty()) BindResult.Failed else BindResult.VideoOptionsFailed
    }
}

internal fun lowLightSupported(info: CameraInfo): Boolean = runCatching { info.isLowLightBoostSupported }.getOrDefault(false)

private fun Stabilization.feature(): GroupableFeature = when (this) {
    Stabilization.Preview -> GroupableFeature.PREVIEW_STABILIZATION
    Stabilization.Recording -> GroupableFeatures.VIDEO_STABILIZATION
}

internal fun CaptureMode.extensionMode(): Int = when (this) {
    CaptureMode.Off -> ExtensionMode.NONE
    CaptureMode.Auto -> ExtensionMode.AUTO
    CaptureMode.Hdr -> ExtensionMode.HDR
    CaptureMode.Night -> ExtensionMode.NIGHT
    CaptureMode.Portrait -> ExtensionMode.BOKEH
    CaptureMode.FaceRetouch -> ExtensionMode.FACE_RETOUCH
}

@Composable
internal fun <T : Any> liveValue(data: LiveData<T>?): T? {
    val owner = LocalLifecycleOwner.current
    var value by remember(data) { mutableStateOf(data?.value) }
    DisposableEffect(data, owner) {
        if (data == null) return@DisposableEffect onDispose { }
        val observer = Observer<T> { value = it }
        data.observe(owner, observer)
        onDispose { data.removeObserver(observer) }
    }
    return value
}

package com.fortind.zenith.ui.media

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.material3.Player

private const val MIN_ASPECT = 0.42f
private const val MAX_ASPECT = 2.39f
private const val ASPECT_SCALE = 1000

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
fun PictureInPictureVideo(playback: FeedPlayback) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val supported = remember(activity) { activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    if (!supported) return
    val inPictureInPicture = rememberInPictureInPicture(activity)
    SideEffect { playback.onPictureInPictureChanged(inPictureInPicture) }
    val eligible = playback.videoPlaying
    val aspect = pictureInPictureAspect(playback.videoAspect)
    val sourceRect = playback.videoBounds?.let { Rect(it.left, it.top, it.right, it.bottom) }
    SideEffect { activity.updatePictureInPictureParams(eligible, aspect, sourceRect) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && eligible) {
        DisposableEffect(activity, aspect, sourceRect) {
            val enter = Runnable { activity.enterPictureInPicture(aspect, sourceRect) }
            activity.addOnUserLeaveHintListener(enter)
            onDispose { activity.removeOnUserLeaveHintListener(enter) }
        }
    }
    if (inPictureInPicture && playback.current != null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Player(player = playback.player, modifier = Modifier.fillMaxSize(), showControls = false)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun rememberInPictureInPicture(activity: ComponentActivity): Boolean {
    var inPictureInPicture by remember(activity) { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val observer = Consumer<PictureInPictureModeChangedInfo> { inPictureInPicture = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(observer)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(observer) }
    }
    return inPictureInPicture
}

internal fun pictureInPictureAspect(aspect: Float): Rational {
    val clamped = if (aspect.isFinite() && aspect > 0f) aspect.coerceIn(MIN_ASPECT, MAX_ASPECT) else 16f / 9f
    return Rational((clamped * ASPECT_SCALE).toInt(), ASPECT_SCALE)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun pictureInPictureParams(eligible: Boolean, aspect: Rational, sourceRect: Rect?): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(aspect).setSourceRectHint(sourceRect)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(eligible)
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.O)
private fun ComponentActivity.updatePictureInPictureParams(eligible: Boolean, aspect: Rational, sourceRect: Rect?) {
    try {
        setPictureInPictureParams(pictureInPictureParams(eligible, aspect, sourceRect))
    } catch (_: IllegalStateException) {
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun ComponentActivity.enterPictureInPicture(aspect: Rational, sourceRect: Rect?) {
    try {
        enterPictureInPictureMode(pictureInPictureParams(eligible = true, aspect, sourceRect))
    } catch (_: IllegalStateException) {
    }
}

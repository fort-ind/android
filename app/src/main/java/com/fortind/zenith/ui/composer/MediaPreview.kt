package com.fortind.zenith.ui.composer

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player
import com.fortind.zenith.R
import com.fortind.zenith.composer.Attachment
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.media.NetworkImage
import com.fortind.zenith.ui.rememberReduceMotion

@Composable
fun MediaPreviewOverlay(attachment: Attachment?, onClose: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = attachment != null,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(motion.defaultEffectsSpec<Float>()),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(motion.fastEffectsSpec<Float>()),
    ) {
        val current = attachment ?: return@AnimatedVisibility
        val title = stringResource(R.string.attachment_preview_close)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).semantics { paneTitle = title }) {
            when (current.media.kind) {
                MediaKind.Video, MediaKind.Audio -> VideoPlayer(current.media.uri)
                else -> NetworkImage(
                    url = current.media.uri,
                    contentDescription = current.altText.ifBlank { null },
                    animate = !reduceMotion,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                )
            }
            TooltipIconButton(
                label = title,
                icon = Icons.Default.Close,
                onClick = onClose,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
            )
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
private fun VideoPlayer(uri: String) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleResumeEffect(player) { onPauseOrDispose { player.pause() } }
    var controlsVisible by remember { mutableStateOf(true) }
    Player(
        player = player,
        showControls = controlsVisible,
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null) { controlsVisible = !controlsVisible },
    )
}

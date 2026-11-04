package com.fortind.zenith.ui.feed

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.material3.Player
import com.fortind.zenith.R
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.media.FeedPlayback

private val VideoFocusRing = 2.dp

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
fun InlineVideo(playback: FeedPlayback, file: NoteFile, modifier: Modifier = Modifier) {
    var controls by rememberSaveable(file.id) { mutableStateOf(true) }
    var focused by remember { mutableStateOf(false) }
    val fullscreenLabel = stringResource(R.string.note_video_fullscreen)
    val controlsLabel = stringResource(R.string.note_video_controls)
    val focusRing = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .background(Color.Black)
            .onFocusChanged { focused = it.isFocused }
            .playbackKeys(playback, file) { controls = !controls }
            .semantics {
                onClick(label = controlsLabel) {
                    controls = !controls
                    true
                }
            }
            .focusable()
            .then(if (focused) Modifier.border(VideoFocusRing, focusRing) else Modifier),
    ) {
        Player(
            player = playback.player,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { controls = !controls } },
            showControls = controls,
            topControls = { _, visible ->
                AnimatedVisibility(visible = visible, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TooltipIconButton(
                            label = fullscreenLabel,
                            icon = Icons.Default.Fullscreen,
                            onClick = { playback.enterFullscreen(file) },
                            tint = Color.White,
                        )
                    }
                }
            },
        )
    }
}

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
fun FullscreenVideo(playback: FeedPlayback, onDismiss: () -> Unit) {
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    val hinge = posture.hingeList.firstOrNull { !it.isVertical && it.isSeparating }?.takeIf { posture.isTabletop }
    val file = playback.current ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var controls by remember { mutableStateOf(true) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        val closeLabel = stringResource(R.string.viewer_close)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .playbackKeys(playback, file) { controls = !controls }
                .focusRequester(focusRequester)
                .focusable(),
        ) {
            if (hinge != null) {
                val density = LocalDensity.current
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().height(with(density) { hinge.bounds.top.toDp() })) {
                        Player(
                            player = playback.player,
                            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                            showControls = false,
                        )
                        TooltipIconButton(
                            label = closeLabel,
                            icon = Icons.Default.Close,
                            onClick = onDismiss,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(with(density) { hinge.bounds.height.toDp() }))
                    TabletopControls(playback, file, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Player(
                    player = playback.player,
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .pointerInput(Unit) { detectTapGestures { controls = !controls } },
                    showControls = controls,
                )
                AnimatedVisibility(visible = controls, modifier = Modifier.align(Alignment.TopStart)) {
                    TooltipIconButton(
                        label = closeLabel,
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        tint = Color.White,
                        modifier = Modifier.safeDrawingPadding().padding(8.dp),
                    )
                }
            }
        }
    }
}

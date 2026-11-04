package com.fortind.zenith.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.media.formatPlaybackTime

private val TabletopMaxWidth = 640.dp
private val SideButtonSize = 72.dp
private val PlayButtonSize = 96.dp
private val PlayIconSize = 40.dp
private const val TABLETOP_SEEK_MS = 5_000L

@Composable
internal fun TabletopControls(playback: FeedPlayback, file: NoteFile, modifier: Modifier = Modifier) {
    val player = playback.player
    val progress = rememberPlayerProgress(player)
    val position = formatPlaybackTime(progress.positionMs)
    val duration = formatPlaybackTime(if (progress.known) progress.durationMs else 0L)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.navigationBarsPadding().padding(16.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = TabletopMaxWidth).fillMaxWidth(),
            ) {
                file.name?.let { name ->
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(position, style = MaterialTheme.typography.labelMedium)
                    val positionDescription = stringResource(R.string.note_audio_position, position, duration)
                    val sliderState = remember { SliderState() }
                    LaunchedEffect(progress.fraction) {
                        if (!sliderState.isDragging) sliderState.value = progress.fraction
                    }
                    Slider(
                        state = sliderState,
                        onValueChange = { fraction ->
                            val target = (fraction * progress.durationMs).toLong()
                            progress.positionMs = target
                            player?.seekTo(target)
                        },
                        enabled = progress.known,
                        modifier = Modifier.weight(1f).semantics { stateDescription = positionDescription },
                    )
                    Text(duration, style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalIconButton(onClick = { playback.seekBy(-TABLETOP_SEEK_MS) }, modifier = Modifier.size(SideButtonSize)) {
                        Icon(Icons.Default.Replay5, contentDescription = stringResource(R.string.note_video_back))
                    }
                    FilledIconButton(onClick = { playback.toggle(file) }, modifier = Modifier.size(PlayButtonSize)) {
                        Icon(
                            imageVector = if (progress.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (progress.isPlaying) R.string.note_video_pause else R.string.note_video_play),
                            modifier = Modifier.size(PlayIconSize),
                        )
                    }
                    FilledTonalIconButton(onClick = { playback.seekBy(TABLETOP_SEEK_MS) }, modifier = Modifier.size(SideButtonSize)) {
                        Icon(Icons.Default.Forward5, contentDescription = stringResource(R.string.note_video_forward))
                    }
                }
            }
        }
    }
}

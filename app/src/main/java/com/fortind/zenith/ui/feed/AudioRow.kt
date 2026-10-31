package com.fortind.zenith.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.fortind.zenith.R
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.media.formatPlaybackTime
import kotlinx.coroutines.delay

private const val POSITION_TICK_MS = 250L

@Stable
private class PlayerProgress {
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(C.TIME_UNSET)

    val known: Boolean get() = durationMs > 0

    val fraction: Float get() = if (known) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Composable
private fun rememberPlayerProgress(player: Player?): PlayerProgress {
    val progress = remember(player) { PlayerProgress() }
    if (player != null) {
        DisposableEffect(player) {
            fun sync() {
                progress.isPlaying = player.isPlaying
                progress.positionMs = player.currentPosition
                progress.durationMs = player.duration
            }
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync()
            }
            sync()
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player, progress.isPlaying) {
            while (progress.isPlaying) {
                progress.positionMs = player.currentPosition
                delay(POSITION_TICK_MS)
            }
        }
    }
    return progress
}

@Composable
fun AudioRow(file: NoteFile, playback: FeedPlayback, modifier: Modifier = Modifier) {
    val active = playback.current?.id == file.id
    val player = playback.player?.takeIf { active }
    val progress = rememberPlayerProgress(player)
    val playing = active && progress.isPlaying
    val colors = MaterialTheme.colorScheme
    val title = file.name ?: stringResource(R.string.note_audio)
    val position = formatPlaybackTime(progress.positionMs)
    val duration = formatPlaybackTime(if (progress.known) progress.durationMs else 0L)
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHighest, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            FilledTonalIconButton(
                onClick = { if (playing) playback.pause() else playback.play(file) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.note_audio_pause else R.string.note_audio_play),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        enabled = active && progress.known,
                        modifier = Modifier.weight(1f).semantics { stateDescription = positionDescription },
                    )
                    Text(
                        text = if (active) position else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}

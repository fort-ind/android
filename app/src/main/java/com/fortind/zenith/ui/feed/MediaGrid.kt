package com.fortind.zenith.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.media.BlurhashImage
import com.fortind.zenith.ui.media.NetworkImage
import com.fortind.zenith.ui.pointerHover

private const val MIN_ASPECT = 0.6f
private const val MAX_ASPECT = 1.9f
private const val GRID_LIMIT = 4
private const val DEFAULT_VIDEO_ASPECT = 16f / 9f
private const val VISIBLE_FRACTION_TO_KEEP_PLAYING = 0.25f

private val NoteFile.hasPreview: Boolean
    get() = kind == MediaKind.Image || kind == MediaKind.Video

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaGrid(note: Note, environment: NoteEnvironment, callbacks: NoteCallbacks) {
    var revealed by rememberSaveable(note.id) { mutableStateOf(false) }
    val files = note.files
    val playback = environment.playback
    val images = remember(files) { files.filter { it.kind == MediaKind.Image } }
    val audio = remember(files, revealed) { files.filter { it.kind == MediaKind.Audio && (!it.isSensitive || revealed) } }
    val gridFiles = remember(files, audio) { files.filter { it !in audio } }
    val shown = gridFiles.take(GRID_LIMIT)
    val extra = gridFiles.size - shown.size
    val gap = 4.dp
    val tile = @Composable { index: Int, modifier: Modifier ->
        val file = shown[index]
        MediaTile(
            file = file,
            position = index + 1,
            total = gridFiles.size,
            hidden = file.isSensitive && !revealed,
            extra = if (index == shown.lastIndex) extra else 0,
            animate = environment.animate,
            onClick = {
                when {
                    file.isSensitive && !revealed -> revealed = true
                    file.kind == MediaKind.Image -> callbacks.openMedia(note, images.indexOf(file).coerceAtLeast(0))
                    file.kind == MediaKind.Video && gridFiles.size == 1 -> playback.play(file)
                    file.kind == MediaKind.Video -> playback.enterFullscreen(file)
                    else -> callbacks.openUrl(file.url)
                }
            },
            modifier = modifier,
        )
    }
    val shape = MaterialTheme.shapes.large
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (shown.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().clip(shape)) {
                when (shown.size) {
                    1 -> {
                        val file = shown[0]
                        if (file.hasPreview) {
                            val fallback = if (file.kind == MediaKind.Video) DEFAULT_VIDEO_ASPECT else 1.5f
                            val aspect = (file.aspectRatio ?: fallback).coerceIn(MIN_ASPECT, MAX_ASPECT)
                            val frame = Modifier.fillMaxWidth().heightIn(max = 420.dp).aspectRatio(aspect)
                            val inline = file.kind == MediaKind.Video && playback.current?.id == file.id && !playback.fullscreen && !(file.isSensitive && !revealed)
                            if (inline) {
                                InlineVideo(
                                    playback = playback,
                                    file = file,
                                    modifier = frame.onVisibilityChanged(minFractionVisible = VISIBLE_FRACTION_TO_KEEP_PLAYING) { visible ->
                                        if (!visible && !playback.fullscreen) playback.stopIf(file.id)
                                    },
                                )
                            } else {
                                tile(0, frame)
                            }
                        } else {
                            tile(0, Modifier.fillMaxWidth().height(72.dp))
                        }
                    }
                    2 -> Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth().aspectRatio(2f)) {
                        tile(0, Modifier.weight(1f).fillMaxHeight())
                        tile(1, Modifier.weight(1f).fillMaxHeight())
                    }
                    3 -> Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                        tile(0, Modifier.weight(1f).fillMaxHeight())
                        Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.weight(1f).fillMaxHeight()) {
                            tile(1, Modifier.weight(1f).fillMaxWidth())
                            tile(2, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.weight(1f)) {
                            tile(0, Modifier.weight(1f).fillMaxHeight())
                            tile(1, Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.weight(1f)) {
                            tile(2, Modifier.weight(1f).fillMaxHeight())
                            tile(3, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
        audio.forEach { AudioRow(it, playback) }
    }
}

@Composable
private fun MediaTile(
    file: NoteFile,
    position: Int,
    total: Int,
    hidden: Boolean,
    extra: Int,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    var showAlt by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val description = when {
        hidden -> stringResource(R.string.note_sensitive)
        file.kind == MediaKind.Video -> stringResource(R.string.note_video)
        file.kind == MediaKind.Audio -> stringResource(R.string.note_audio)
        file.kind == MediaKind.Other -> stringResource(R.string.note_file)
        else -> file.altText ?: stringResource(R.string.note_media_position, position, total)
    }
    val colors = MaterialTheme.colorScheme
    val posterUrl = if (file.kind == MediaKind.Image) file.thumbnailUrl ?: file.url else file.thumbnailUrl
    Box(
        modifier = modifier
            .background(colors.surfaceContainerHighest)
            .pointerHover { hovered = it }
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = if (file.kind == MediaKind.Image) Role.Image else Role.Button,
                onClick = onClick,
            )
            .then(if (focused) Modifier.border(3.dp, colors.primary, MaterialTheme.shapes.small) else Modifier)
            .semantics { contentDescription = description },
    ) {
        when {
            hidden -> SensitiveCover(file)
            posterUrl != null -> NetworkImage(
                url = posterUrl,
                contentDescription = null,
                animate = animate,
                blurhash = file.blurhash,
                modifier = Modifier.fillMaxSize(),
            )
            else -> BlurhashImage(file.blurhash, Modifier.fillMaxSize())
        }
        if (!hidden && file.kind != MediaKind.Image) {
            val icon = when (file.kind) {
                MediaKind.Video -> Icons.Default.PlayArrow
                MediaKind.Audio -> Icons.Default.AudioFile
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            }
            Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center)) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(12.dp).size(28.dp))
            }
        }
        if (hovered && !hidden) {
            Box(modifier = Modifier.fillMaxSize().background(colors.onSurface.copy(alpha = 0.08f)))
        }
        if (extra > 0) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.note_more_media, extra), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }
        if (!hidden && file.altText != null) {
            Surface(
                onClick = { showAlt = true },
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    stringResource(R.string.note_alt),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
    if (showAlt && file.altText != null) {
        AlertDialog(
            onDismissRequest = { showAlt = false },
            confirmButton = { TextButton(onClick = { showAlt = false }) { Text(stringResource(R.string.viewer_close)) } },
            title = { Text(stringResource(R.string.note_alt_title)) },
            text = { Text(file.altText) },
        )
    }
}

@Composable
private fun SensitiveCover(file: NoteFile) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BlurhashImage(file.blurhash, Modifier.fillMaxSize())
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.note_sensitive), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

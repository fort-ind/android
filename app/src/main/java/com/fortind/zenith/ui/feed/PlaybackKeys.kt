package com.fortind.zenith.ui.feed

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.media.FeedPlayback

private const val KEY_SEEK_MS = 5_000L

internal fun Modifier.playbackKeys(playback: FeedPlayback, file: NoteFile, onActivate: (() -> Unit)? = null): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onKeyEvent false
    val active = playback.current?.id == file.id
    when (event.key) {
        Key.Spacebar -> {
            playback.toggle(file)
            true
        }
        Key.DirectionLeft -> active.also { if (it) playback.seekBy(-KEY_SEEK_MS) }
        Key.DirectionRight -> active.also { if (it) playback.seekBy(KEY_SEEK_MS) }
        Key.Enter, Key.NumPadEnter -> onActivate?.let {
            it()
            true
        } ?: false
        else -> false
    }
}

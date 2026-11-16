package com.fortind.zenith.ui.feed

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.DpOffset
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.canRenote

class NoteReveal(
    val cwShown: Boolean,
    val toggleCw: () -> Unit,
    val longExpanded: Boolean,
    val toggleLong: () -> Unit,
)

enum class NoteKey { Reply, React, Like, Renote, Menu, OpenMedia, ToggleContent }

fun noteKeyFor(key: Key, shift: Boolean): NoteKey? = when {
    key == Key.Plus || key == Key.NumPadAdd || (shift && key == Key.Equals) -> NoteKey.React
    shift -> null
    key == Key.R -> NoteKey.Reply
    key == Key.E || key == Key.A -> NoteKey.React
    key == Key.L -> NoteKey.Like
    key == Key.Q -> NoteKey.Renote
    key == Key.M -> NoteKey.Menu
    key == Key.O -> NoteKey.OpenMedia
    key == Key.V -> NoteKey.ToggleContent
    else -> null
}

fun firstViewableMedia(note: Note): Int? =
    note.files.indexOfFirst { (it.kind == MediaKind.Image || it.kind == MediaKind.Video) && !it.isSensitive }.takeIf { it >= 0 }

internal fun runNoteKey(
    key: NoteKey,
    note: Note,
    environment: NoteEnvironment,
    callbacks: NoteCallbacks,
    reveal: NoteReveal,
    collapsible: Boolean,
    renoted: Boolean,
    openMenu: (DpOffset) -> Unit,
): Boolean = when (key) {
    NoteKey.Reply -> {
        callbacks.reply(note)
        true
    }
    NoteKey.React -> {
        callbacks.openReactionPicker(note)
        true
    }
    NoteKey.Like -> {
        callbacks.reactDefault(note)
        true
    }
    NoteKey.Renote -> if (!renoted && note.canRenote(environment.myUserId)) {
        callbacks.renote(note)
        true
    } else {
        false
    }
    NoteKey.Menu -> {
        openMenu(DpOffset.Zero)
        true
    }
    NoteKey.OpenMedia -> firstViewableMedia(note)?.let { index ->
        callbacks.openMedia(note, index)
        true
    } ?: false
    NoteKey.ToggleContent -> when {
        note.cwMfm != null -> {
            reveal.toggleCw()
            true
        }
        collapsible && note.isLong() -> {
            reveal.toggleLong()
            true
        }
        else -> false
    }
}

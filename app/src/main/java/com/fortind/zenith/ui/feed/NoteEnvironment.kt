package com.fortind.zenith.ui.feed

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.mfm.MfmEnvironment

class NoteCallbacks(
    val openThread: (Note) -> Unit,
    val reply: (Note) -> Unit,
    val quote: (Note) -> Unit,
    val openProfile: (NoteUser) -> Unit,
    val toggleReaction: (Note, String) -> Unit,
    val openReactionPicker: (Note) -> Unit,
    val reactDefault: (Note) -> Unit,
    val renote: (Note) -> Unit,
    val unrenote: (Note) -> Unit,
    val checkRenoted: (Note) -> Unit,
    val vote: (Note, List<Int>) -> Unit,
    val edit: (Note) -> Unit,
    val delete: (Note) -> Unit,
    val redraft: (Note) -> Unit,
    val toggleBookmark: (Note) -> Unit,
    val checkBookmarked: (Note) -> Unit,
    val openMedia: (Note, Int) -> Unit,
    val openUrl: (String) -> Unit,
    val copyLink: (Note) -> Unit,
    val copyText: (Note) -> Unit,
    val share: (Note) -> Unit,
)

class NoteEnvironment(
    val now: Long,
    val myUserId: String?,
    val signedIn: Boolean,
    val renoted: Set<String>,
    val renoteChecks: Set<String>,
    val bookmarked: Set<String>,
    val bookmarkChecks: Set<String>,
    val localEmojiNames: Set<String>?,
    val emojiUrl: (String) -> String,
    val mfm: MfmEnvironment,
    val selectedNoteId: String?,
    val playback: FeedPlayback,
) {
    val animate: Boolean get() = mfm.animate

    fun noteEmoji(note: Note): (String) -> String = { name ->
        note.emojis[name] ?: note.user.host?.let { host -> "https://${FortInstance.HOST}/emoji/$name@$host.webp" } ?: emojiUrl(name)
    }

    fun userEmoji(user: NoteUser): (String) -> String = { name ->
        user.emojis[name] ?: user.host?.let { host -> "https://${FortInstance.HOST}/emoji/$name@$host.webp" } ?: emojiUrl(name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            if (tint == Color.Unspecified) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            } else {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

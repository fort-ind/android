package com.fortind.zenith.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.Reactions
import com.fortind.zenith.ui.mfm.CustomEmojiImage
import com.fortind.zenith.ui.mfm.EmojiAspects
import com.fortind.zenith.ui.pointerHover

private const val VISIBLE_REACTIONS = 16

class NoteActionLabels(
    val react: String,
    val like: String,
    val renote: String,
    val unrenote: String,
    val quote: String,
    val reply: String,
    val more: String,
    val copyLink: String,
    val openBrowser: String,
)

@Composable
fun rememberNoteActionLabels(): NoteActionLabels {
    val react = stringResource(R.string.note_action_react)
    val like = stringResource(R.string.note_action_like)
    val renote = stringResource(R.string.note_action_renote)
    val unrenote = stringResource(R.string.note_action_unrenote)
    val quote = stringResource(R.string.note_action_quote)
    val reply = stringResource(R.string.note_action_reply)
    val more = stringResource(R.string.note_action_more)
    val copyLink = stringResource(R.string.note_menu_copy_link)
    val openBrowser = stringResource(R.string.note_menu_open_browser)
    return remember(react, like, renote, unrenote, quote, reply, more, copyLink, openBrowser) {
        NoteActionLabels(react, like, renote, unrenote, quote, reply, more, copyLink, openBrowser)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionChips(note: Note, environment: NoteEnvironment, callbacks: NoteCallbacks) {
    var expanded by remember(note.id) { mutableStateOf(false) }
    val sorted = remember(note.reactions) { note.reactions.entries.sortedByDescending { it.value } }
    val visible = if (expanded) sorted else sorted.take(VISIBLE_REACTIONS)
    val remoteOnly = stringResource(R.string.note_reaction_remote_only)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        visible.forEach { (key, count) ->
            val usable = environment.signedIn && Reactions.toApi(key, environment.localEmojiNames) != null
            val selected = Reactions.sameReaction(note.myReaction, key)
            val chip = @Composable {
                ReactionChip(
                    reactionKey = key,
                    count = count,
                    selected = selected,
                    enabled = usable || !environment.signedIn,
                    note = note,
                    environment = environment,
                    onClick = { callbacks.toggleReaction(note, key) },
                )
            }
            if (environment.signedIn && !usable) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(remoteOnly) } },
                    state = rememberTooltipState(),
                ) { chip() }
            } else {
                chip()
            }
        }
        if (!expanded && sorted.size > VISIBLE_REACTIONS) {
            Surface(
                onClick = { expanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    pluralStringResource(R.plurals.note_reactions_more, sorted.size - VISIBLE_REACTIONS, sorted.size - VISIBLE_REACTIONS),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.height(32.dp).padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
internal fun ReactionChip(
    reactionKey: String,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    note: Note,
    environment: NoteEnvironment,
    onClick: () -> Unit,
) {
    val name = Reactions.customName(reactionKey)
    val description = stringResource(R.string.note_reaction_description, name?.let { ":$it:" } ?: reactionKey, count)
    var hovered by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val container = when {
        selected -> colors.primaryContainer
        hovered -> colors.surfaceContainerHighest
        else -> colors.surfaceContainerHigh
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = if (selected) BorderStroke(1.dp, colors.primary) else null,
        modifier = Modifier
            .pointerHover { hovered = it }
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(32.dp).padding(horizontal = 10.dp),
        ) {
            ReactionGlyph(reactionKey, note, environment, sizeDp = 18)
            Text(count.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ReactionGlyph(reactionKey: String, note: Note?, environment: NoteEnvironment, sizeDp: Int) {
    val name = Reactions.customName(reactionKey)
    if (name == null) {
        Text(reactionKey, style = MaterialTheme.typography.titleMedium)
        return
    }
    val url = Reactions.emojiUrl(reactionKey, note?.reactionEmojis.orEmpty(), environment.emojiUrl)
    if (url == null) {
        Text(":$name:", style = MaterialTheme.typography.labelMedium)
        return
    }
    EmojiAspects.version
    val ratio = EmojiAspects.ratio(url).coerceIn(0.5f, 6f)
    Box(modifier = Modifier.height(sizeDp.dp).width((sizeDp * ratio).dp)) {
        CustomEmojiImage(name, url, environment.animate)
    }
}

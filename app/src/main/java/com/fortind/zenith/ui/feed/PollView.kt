package com.fortind.zenith.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.timeline.Poll
import com.fortind.zenith.timeline.PollChoice
import com.fortind.zenith.timeline.canVote
import com.fortind.zenith.ui.relativeTime
import kotlin.math.roundToInt

@Composable
fun PollView(
    poll: Poll,
    noteId: String,
    now: Long,
    signedIn: Boolean,
    onVote: (List<Int>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val votable = signedIn && poll.canVote(now)
    var picked by rememberSaveable(noteId) { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (votable) {
            poll.choices.forEachIndexed { index, choice ->
                val selected = picked and (1 shl index) != 0
                ChoiceOption(
                    text = choice.text,
                    multiple = poll.multiple,
                    selected = selected,
                    onClick = { if (poll.multiple) picked = picked xor (1 shl index) else onVote(listOf(index)) },
                )
            }
            if (poll.multiple) {
                Button(
                    onClick = { onVote(poll.choices.indices.filter { picked and (1 shl it) != 0 }) },
                    enabled = picked != 0,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.note_poll_vote))
                }
            }
        } else {
            val total = poll.totalVotes
            poll.choices.forEach { choice -> ChoiceResult(choice, total) }
        }
        val expires = poll.expiresAtMillis
        val status = when {
            expires == null -> null
            expires <= now -> stringResource(R.string.note_poll_ended)
            else -> stringResource(R.string.note_poll_ends, relativeTime(expires, now))
        }
        val hint = when {
            !votable -> null
            poll.multiple -> stringResource(R.string.note_poll_choose_many)
            else -> stringResource(R.string.note_poll_choose_one)
        }
        Text(
            text = listOfNotNull(hint, pluralStringResource(R.plurals.note_poll_votes, poll.totalVotes, poll.totalVotes), status).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceOption(text: String, multiple: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(shape)
        .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
        .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
    val interactive = if (multiple) {
        base.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        base.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = interactive.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (multiple) {
            Icon(
                if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (selected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChoiceResult(choice: PollChoice, total: Int) {
    val colors = MaterialTheme.colorScheme
    val fraction = if (total == 0) 0f else choice.votes.toFloat() / total
    val percent = (fraction * 100).roundToInt()
    val fill = if (choice.isVoted) colors.primaryContainer else colors.secondaryContainer
    val description = stringResource(
        if (choice.isVoted) R.string.note_poll_choice_voted else R.string.note_poll_choice_result,
        choice.text,
        percent,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHighest)
            .drawBehind {
                val width = size.width * fraction
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                drawRect(color = fill, topLeft = Offset(left, 0f), size = Size(width, size.height))
            }
            .semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (choice.isVoted) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
            Text(
                choice.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("$percent%", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
    }
}

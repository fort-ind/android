package com.fortind.zenith.ui.feed

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.mfm.MfmText
import com.fortind.zenith.timeline.Note
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.LaunchedEffect
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.contextMenuTrigger
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.mfm.MfmEnvironment
import com.fortind.zenith.ui.pointerHover
import com.fortind.zenith.ui.relativeTime

private const val LONG_TEXT_CHARS = 500
private const val LONG_TEXT_LINES = 15

private fun Note.isLong(): Boolean =
    (text?.length ?: 0) > LONG_TEXT_CHARS || (text?.count { it == '\n' } ?: 0) >= LONG_TEXT_LINES

@Composable
fun NoteCard(
    note: Note,
    environment: NoteEnvironment,
    callbacks: NoteCallbacks,
    modifier: Modifier = Modifier,
    focal: Boolean = false,
) {
    val target = note.target
    var hovered by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val selected = environment.selectedNoteId == target.id
    val colors = MaterialTheme.colorScheme
    val container = when {
        focal -> Color.Transparent
        selected -> colors.secondaryContainer
        hovered -> colors.surfaceContainerLow
        else -> Color.Transparent
    }
    val labels = rememberNoteActionLabels()
    val renoted = target.id in environment.renoted
    val openMenu = { offset: DpOffset ->
        menuOffset = offset
        menuOpen = true
    }

    Box(modifier = modifier) {
        Surface(
            onClick = { callbacks.openThread(target) },
            enabled = !focal && !target.deleted,
            shape = RectangleShape,
            color = container,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("note")
                .pointerHover { hovered = it }
                .contextMenuTrigger(enabled = !target.deleted, onOpen = openMenu)
                .onPreviewKeyEvent { event ->
                    val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
                    if (event.type != KeyEventType.KeyDown || !plain || target.deleted) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.R -> {
                            callbacks.openReactionPicker(target)
                            true
                        }
                        Key.L -> {
                            callbacks.reactDefault(target)
                            true
                        }
                        else -> false
                    }
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(labels.reply) { callbacks.reply(target); true },
                        CustomAccessibilityAction(labels.quote) { callbacks.quote(target); true },
                        CustomAccessibilityAction(labels.react) { callbacks.openReactionPicker(target); true },
                        CustomAccessibilityAction(if (renoted) labels.unrenote else labels.renote) {
                            if (renoted) callbacks.unrenote(target) else callbacks.renote(target)
                            true
                        },
                        CustomAccessibilityAction(labels.copyLink) { callbacks.copyLink(target); true },
                        CustomAccessibilityAction(labels.openBrowser) { callbacks.openUrl(target.webUrl); true },
                    )
                },
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 2.dp)) {
                if (note.isPureRenote) RenoteHeader(note, environment)
                if (target.deleted) {
                    DeletedStub(R.string.note_deleted)
                } else if (target.isHidden) {
                    DeletedStub(R.string.note_hidden)
                } else {
                    NoteMain(target, environment, callbacks, focal)
                }
            }
        }
        NoteMenu(
            note = target,
            expanded = menuOpen,
            offset = menuOffset,
            onDismiss = { menuOpen = false },
            environment = environment,
            callbacks = callbacks,
        )
    }
}

@Composable
private fun NoteMain(note: Note, environment: NoteEnvironment, callbacks: NoteCallbacks, focal: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(
            name = note.user.displayName,
            url = note.user.avatarUrl,
            size = if (focal) 48.dp else 40.dp,
            modifier = Modifier
                .clickable(onClickLabel = note.user.handle) { callbacks.openProfile(note.user) }
                .semantics { contentDescription = note.user.handle },
        )
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NoteHeader(note, environment)
            note.reply?.let { ReplyContext(it) }
            NoteContent(note, environment, callbacks, focal)
            if (note.reactions.isNotEmpty()) ReactionChips(note, environment, callbacks)
            NoteActionRow(note, environment, callbacks)
        }
    }
}

@Composable
private fun RenoteHeader(note: Note, environment: NoteEnvironment) {
    val nameEnvironment = remember(note.user, environment.mfm) { environment.mfm.withEmoji(environment.userEmoji(note.user)) }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 22.dp, bottom = 6.dp),
    ) {
        Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        MfmContent(
            nodes = note.user.nameMfm.ifEmpty { listOf(MfmText(note.user.username)) },
            environment = nameEnvironment,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(stringResource(R.string.note_renoted), style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
        Text("·", style = MaterialTheme.typography.labelMedium, color = tint)
        Text(relativeTime(note.createdAtMillis, environment.now), style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

@Composable
private fun ReplyContext(parent: Note) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.note_replying_to, parent.user.handle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NoteHeader(note: Note, environment: NoteEnvironment) {
    val nameEnvironment = remember(note.user, environment.mfm) { environment.mfm.withEmoji(environment.userEmoji(note.user)) }
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MfmContent(
            nodes = note.user.nameMfm.ifEmpty { listOf(MfmText(note.user.username)) },
            environment = nameEnvironment,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = note.user.handle,
            style = MaterialTheme.typography.bodyMedium,
            color = subtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (note.user.isBot) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = stringResource(R.string.note_bot),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        VisibilityIcon(note)
        if (note.editedAtMillis != null) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.note_edited),
                tint = subtle,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = relativeTime(note.createdAtMillis, environment.now),
            style = MaterialTheme.typography.bodyMedium,
            color = subtle,
            maxLines = 1,
        )
    }
}

@Composable
private fun VisibilityIcon(note: Note) {
    val icon: ImageVector? = when (note.visibility) {
        Visibility.Public -> null
        Visibility.Home -> Icons.Default.Home
        Visibility.Followers -> Icons.Default.Lock
        Visibility.Specified -> Icons.Default.Mail
    }
    val label = when (note.visibility) {
        Visibility.Public -> null
        Visibility.Home -> stringResource(R.string.note_visibility_home)
        Visibility.Followers -> stringResource(R.string.note_visibility_followers)
        Visibility.Specified -> stringResource(R.string.note_visibility_specified)
    }
    if (icon != null) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
    if (note.localOnly) {
        Icon(
            Icons.Default.PublicOff,
            contentDescription = stringResource(R.string.note_local_only),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun NoteContent(note: Note, environment: NoteEnvironment, callbacks: NoteCallbacks, focal: Boolean) {
    val cw = note.cwMfm
    if (cw == null) {
        NoteBody(note, environment, callbacks, focal)
        return
    }
    var shown by rememberSaveable(note.id) { mutableStateOf(false) }
    val textEnvironment = remember(note.id, environment.mfm) { environment.mfm.withEmoji(environment.noteEmoji(note)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (cw.isEmpty()) {
            Text(stringResource(R.string.note_cw_default), style = MaterialTheme.typography.bodyLarge)
        } else {
            MfmContent(cw, textEnvironment, style = MaterialTheme.typography.bodyLarge)
        }
        CwToggle(shown) { shown = !shown }
        AnimatedVisibility(visible = shown) {
            NoteBody(note, environment, callbacks, focal)
        }
    }
}

@Composable
private fun CwToggle(shown: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        Text(
            text = stringResource(if (shown) R.string.note_cw_hide else R.string.note_cw_show),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NoteBody(note: Note, environment: NoteEnvironment, callbacks: NoteCallbacks, focal: Boolean) {
    val textEnvironment = remember(note.id, environment.mfm) { environment.mfm.withEmoji(environment.noteEmoji(note)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (note.textMfm.isNotEmpty()) {
            if (!focal && note.isLong()) {
                CollapsibleText(note, textEnvironment)
            } else {
                MfmContent(note.textMfm, textEnvironment, style = bodyStyle(focal))
            }
        }
        if (note.files.isNotEmpty()) MediaGrid(note, environment, callbacks)
        note.poll?.let { poll ->
            PollView(
                poll = poll,
                noteId = note.id,
                now = environment.now,
                signedIn = environment.signedIn,
                onVote = { choices -> callbacks.vote(note, choices) },
            )
        }
        val quoted = note.renote
        if (quoted != null && !note.isPureRenote) QuoteCard(quoted, environment, callbacks)
    }
}

@Composable
private fun bodyStyle(focal: Boolean) =
    if (focal) MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize) else MaterialTheme.typography.bodyLarge

@Composable
private fun CollapsibleText(note: Note, environment: MfmEnvironment) {
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.surface
    Column {
        Box(modifier = if (expanded) Modifier else Modifier.heightIn(max = 240.dp).clipToBounds()) {
            MfmContent(note.textMfm, environment, style = MaterialTheme.typography.bodyLarge)
            if (!expanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(0.6f to surface.copy(alpha = 0f), 1f to surface)),
                )
            }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.note_show_less else R.string.note_show_more))
        }
    }
}

@Composable
private fun QuoteCard(quoted: Note, environment: NoteEnvironment, callbacks: NoteCallbacks) {
    var hovered by remember { mutableStateOf(false) }
    val textEnvironment = remember(quoted.id, environment.mfm) { environment.mfm.withEmoji(environment.noteEmoji(quoted)) }
    Surface(
        onClick = { callbacks.openThread(quoted) },
        enabled = !quoted.deleted,
        shape = MaterialTheme.shapes.medium,
        color = if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().pointerHover { hovered = it },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (quoted.deleted) {
                DeletedStub(R.string.note_deleted)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(name = quoted.user.displayName, url = quoted.user.avatarUrl, size = 24.dp)
                Box(modifier = Modifier.weight(1f)) { NoteHeader(quoted, environment) }
            }
            val summary = quoted.cwMfm ?: quoted.textMfm
            if (summary.isNotEmpty()) {
                MfmContent(summary, textEnvironment, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
            }
            if (quoted.files.isNotEmpty() && quoted.cw == null) MediaGrid(quoted, environment, callbacks)
        }
    }
}

@Composable
private fun DeletedStub(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun NoteMenu(
    note: Note,
    expanded: Boolean,
    offset: DpOffset,
    onDismiss: () -> Unit,
    environment: NoteEnvironment,
    callbacks: NoteCallbacks,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = offset) {
        NoteMenuItems(note, environment, callbacks, onDismiss)
    }
}

@Composable
fun NoteMenuItems(note: Note, environment: NoteEnvironment, callbacks: NoteCallbacks, onDismiss: () -> Unit) {
    val signedIn = environment.signedIn
    val mine = signedIn && environment.myUserId == note.user.id && !note.deleted
    LaunchedEffect(note.id, signedIn) { if (signedIn) callbacks.checkBookmarked(note) }
    MenuItem(R.string.note_menu_view_thread) {
        onDismiss()
        callbacks.openThread(note)
    }
    if (signedIn) {
        val bookmarked = note.id in environment.bookmarked
        MenuItem(
            labelRes = if (bookmarked) R.string.note_menu_unbookmark else R.string.note_menu_bookmark,
            enabled = note.id !in environment.bookmarkChecks,
        ) {
            onDismiss()
            callbacks.toggleBookmark(note)
        }
    }
    if (mine) {
        MenuItem(R.string.note_menu_edit) {
            onDismiss()
            callbacks.edit(note)
        }
    }
    MenuItem(R.string.note_menu_copy_link) {
        onDismiss()
        callbacks.copyLink(note)
    }
    if (!note.text.isNullOrEmpty()) {
        MenuItem(R.string.note_menu_copy_text) {
            onDismiss()
            callbacks.copyText(note)
        }
    }
    MenuItem(R.string.note_menu_share) {
        onDismiss()
        callbacks.share(note)
    }
    MenuItem(R.string.note_menu_open_browser) {
        onDismiss()
        callbacks.openUrl(note.webUrl)
    }
    note.remoteUrl?.let { url ->
        MenuItem(R.string.note_menu_open_original) {
            onDismiss()
            callbacks.openUrl(url)
        }
    }
    if (mine) {
        if (note.visibility != Visibility.Specified) {
            MenuItem(R.string.note_menu_redraft, destructive = true) {
                onDismiss()
                callbacks.redraft(note)
            }
        }
        MenuItem(R.string.note_menu_delete, destructive = true) {
            onDismiss()
            callbacks.delete(note)
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = if (destructive) MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error) else MenuDefaults.itemColors()
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick, enabled = enabled, colors = colors)
}

fun absoluteTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
    )

@Composable
fun rememberAbsoluteTime(millis: Long): String {
    val context = LocalContext.current
    return remember(millis, context) { absoluteTime(context, millis) }
}

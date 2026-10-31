package com.fortind.zenith.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fortind.zenith.R
import com.fortind.zenith.notifications.FortNotification
import com.fortind.zenith.notifications.NotificationType
import com.fortind.zenith.notifications.titleText
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.contextMenuTrigger
import com.fortind.zenith.ui.pointerHover
import com.fortind.zenith.ui.relativeTime

class NotificationActions(
    val open: (FortNotification) -> Unit,
    val copyLink: (FortNotification) -> Unit,
    val accept: (FortNotification) -> Unit,
    val reject: (FortNotification) -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NotificationItem(
    notification: FortNotification,
    followProgress: FollowRequestProgress?,
    shapes: ListItemShapes,
    now: Long,
    actions: NotificationActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var hovered by remember { mutableStateOf(false) }
    val isFollowRequest = notification.type == NotificationType.FollowRequest
    val hasMenu = notification.webUrl != null || isFollowRequest
    val showMenu = { offset: DpOffset ->
        if (hasMenu) {
            menuOffset = offset
            menuOpen = true
        }
    }
    val title = notification.titleText(LocalResources.current)
    val colors = ListItemDefaults.segmentedColors(
        containerColor = if (hovered) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    )
    val leading = @Composable { NotificationAvatar(notification) }
    val supporting = @Composable {
        NotificationDetails(
            notification = notification,
            followProgress = followProgress,
            actions = actions,
        )
    }
    val trailing = @Composable {
        Text(
            text = remember(notification.createdAtMillis, now) { relativeTime(notification.createdAtMillis, now) },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val headline = @Composable {
        Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val interactions = Modifier
        .fillMaxWidth()
        .pointerHover { hovered = it }
        .contextMenuTrigger(enabled = hasMenu, onOpen = showMenu)

    Box(modifier = modifier) {
        if (notification.webUrl != null) {
            SegmentedListItem(
                onClick = { actions.open(notification) },
                shapes = shapes,
                onLongClick = { showMenu(DpOffset.Zero) },
                onLongClickLabel = stringResource(R.string.notifications_item_menu_label),
                leadingContent = leading,
                supportingContent = supporting,
                trailingContent = trailing,
                colors = colors,
                modifier = interactions,
                content = headline,
            )
        } else {
            SegmentedListItem(
                shapes = shapes,
                leadingContent = leading,
                supportingContent = supporting,
                trailingContent = trailing,
                colors = colors,
                modifier = interactions,
                content = headline,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, offset = menuOffset) {
            if (notification.webUrl != null) {
                MenuItem(R.string.notifications_menu_open) {
                    menuOpen = false
                    actions.open(notification)
                }
                MenuItem(R.string.notifications_menu_copy_link) {
                    menuOpen = false
                    actions.copyLink(notification)
                }
            }
            if (isFollowRequest && followProgress == null) {
                MenuItem(R.string.notifications_accept) {
                    menuOpen = false
                    actions.accept(notification)
                }
                MenuItem(R.string.notifications_reject) {
                    menuOpen = false
                    actions.reject(notification)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

@Composable
private fun NotificationDetails(
    notification: FortNotification,
    followProgress: FollowRequestProgress?,
    actions: NotificationActions,
) {
    val showsReaction = notification.type == NotificationType.Reaction && notification.reaction != null
    val showsActions = notification.type == NotificationType.FollowRequest
    val body = notification.notePreview ?: notification.body
    if (!showsReaction && body == null && !showsActions) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showsReaction) {
            ReactionView(reaction = notification.reaction.orEmpty(), emojiUrl = notification.reactionEmojiUrl)
        }
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showsActions) {
            FollowRequestActions(
                progress = followProgress,
                onAccept = { actions.accept(notification) },
                onReject = { actions.reject(notification) },
            )
        }
    }
}

@Composable
private fun ReactionView(reaction: String, emojiUrl: String?) {
    if (emojiUrl != null) {
        AsyncImage(
            model = emojiUrl,
            contentDescription = reaction,
            modifier = Modifier.height(28.dp),
        )
    } else {
        Text(text = reaction, style = MaterialTheme.typography.titleLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FollowRequestActions(
    progress: FollowRequestProgress?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val size = ButtonDefaults.MinHeight
    val padding = ButtonDefaults.contentPaddingFor(size)
    when (progress) {
        null, FollowRequestProgress.Accepting, FollowRequestProgress.Rejecting -> {
            val busy = progress != null
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAccept,
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = padding,
                    modifier = Modifier.height(size),
                ) {
                    Text(stringResource(R.string.notifications_accept), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = padding,
                    modifier = Modifier.height(size),
                ) {
                    Text(stringResource(R.string.notifications_reject), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        FollowRequestProgress.Accepted, FollowRequestProgress.Rejected -> {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        if (progress == FollowRequestProgress.Accepted) {
                            R.string.notifications_accepted
                        } else {
                            R.string.notifications_rejected
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationAvatar(notification: FortNotification) {
    val actor = notification.actor
    val badge = notification.type.badgeColors()
    Box(modifier = Modifier.size(48.dp).clearAndSetSemantics {}) {
        if (actor != null) {
            Avatar(name = actor.displayName, url = actor.avatarUrl, modifier = Modifier.align(Alignment.TopStart))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(badge.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notification.type.icon(),
                    contentDescription = null,
                    tint = badge.content,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(badge.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notification.type.icon(),
                    contentDescription = null,
                    tint = badge.content,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private class BadgeColors(val container: Color, val content: Color)

@Composable
private fun NotificationType.badgeColors(): BadgeColors {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        NotificationType.Reaction, NotificationType.Achievement ->
            BadgeColors(colors.tertiaryContainer, colors.onTertiaryContainer)
        NotificationType.Follow, NotificationType.FollowRequest, NotificationType.FollowRequestAccepted ->
            BadgeColors(colors.secondaryContainer, colors.onSecondaryContainer)
        else -> BadgeColors(colors.primaryContainer, colors.onPrimaryContainer)
    }
}

private fun NotificationType.icon(): ImageVector = when (this) {
    NotificationType.Mention -> Icons.Default.AlternateEmail
    NotificationType.Reply -> Icons.AutoMirrored.Filled.Reply
    NotificationType.Quote -> Icons.Default.FormatQuote
    NotificationType.Renote -> Icons.Default.Repeat
    NotificationType.Reaction -> Icons.Default.Favorite
    NotificationType.Follow, NotificationType.FollowRequest -> Icons.Default.PersonAdd
    NotificationType.FollowRequestAccepted -> Icons.Default.HowToReg
    NotificationType.Note, NotificationType.Edited -> Icons.Default.Edit
    NotificationType.PollEnded -> Icons.Default.Poll
    NotificationType.RoleAssigned -> Icons.Default.AdminPanelSettings
    NotificationType.Achievement -> Icons.Default.EmojiEvents
    NotificationType.Login -> Icons.AutoMirrored.Filled.Login
    NotificationType.CreateToken -> Icons.Default.Key
    NotificationType.ExportCompleted -> Icons.Default.Download
    NotificationType.ScheduledNoteFailed -> Icons.Default.Warning
    NotificationType.Test, NotificationType.App, NotificationType.Other -> Icons.Default.Notifications
}

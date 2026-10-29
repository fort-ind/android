package com.fortind.zenith.notifications

import androidx.annotation.StringRes
import com.fortind.zenith.R

enum class NotificationCategory(@param:StringRes val labelRes: Int) {
    Mentions(R.string.settings_notifications_type_mentions),
    Reactions(R.string.settings_notifications_type_reactions),
    Renotes(R.string.settings_notifications_type_renotes),
    Follows(R.string.settings_notifications_type_follows),
    Other(R.string.settings_notifications_type_other);

    val channelId: String get() = "fort_${name.lowercase()}"
}

val DefaultNotificationCategories: Set<NotificationCategory> = setOf(
    NotificationCategory.Mentions,
    NotificationCategory.Reactions,
    NotificationCategory.Renotes,
    NotificationCategory.Follows,
)

fun NotificationType.category(): NotificationCategory = when (this) {
    NotificationType.Mention, NotificationType.Reply, NotificationType.Quote -> NotificationCategory.Mentions
    NotificationType.Reaction -> NotificationCategory.Reactions
    NotificationType.Renote -> NotificationCategory.Renotes
    NotificationType.Follow, NotificationType.FollowRequest, NotificationType.FollowRequestAccepted ->
        NotificationCategory.Follows
    NotificationType.Note,
    NotificationType.Edited,
    NotificationType.PollEnded,
    NotificationType.RoleAssigned,
    NotificationType.Achievement,
    NotificationType.App,
    NotificationType.Login,
    NotificationType.CreateToken,
    NotificationType.ExportCompleted,
    NotificationType.ScheduledNoteFailed,
    NotificationType.Test,
    NotificationType.Other -> NotificationCategory.Other
}

package com.fortind.zenith.notifications

import androidx.annotation.StringRes
import com.fortind.zenith.R

enum class NotificationCategory(@param:StringRes val labelRes: Int, @param:StringRes val descriptionRes: Int) {
    Mentions(R.string.settings_notifications_type_mentions, R.string.notification_channel_mentions_description),
    Reactions(R.string.settings_notifications_type_reactions, R.string.notification_channel_reactions_description),
    Renotes(R.string.settings_notifications_type_renotes, R.string.notification_channel_renotes_description),
    Follows(R.string.settings_notifications_type_follows, R.string.notification_channel_follows_description),
    Other(R.string.settings_notifications_type_other, R.string.notification_channel_other_description);

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

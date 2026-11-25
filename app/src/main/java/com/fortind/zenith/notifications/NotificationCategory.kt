package com.fortind.zenith.notifications

import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fortind.zenith.R

enum class NotificationAlert(val importance: Int, val legacyPriority: Int, val legacySound: Boolean) {
    Urgent(NotificationManagerCompat.IMPORTANCE_HIGH, NotificationCompat.PRIORITY_HIGH, legacySound = true),
    Audible(NotificationManagerCompat.IMPORTANCE_DEFAULT, NotificationCompat.PRIORITY_DEFAULT, legacySound = true),
    Silent(NotificationManagerCompat.IMPORTANCE_LOW, NotificationCompat.PRIORITY_LOW, legacySound = false),
}

enum class NotificationCategory(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val alert: NotificationAlert,
) {
    Mentions(R.string.settings_notifications_type_mentions, R.string.notification_channel_mentions_description, NotificationAlert.Urgent),
    Reactions(R.string.settings_notifications_type_reactions, R.string.notification_channel_reactions_description, NotificationAlert.Silent),
    Renotes(R.string.settings_notifications_type_renotes, R.string.notification_channel_renotes_description, NotificationAlert.Silent),
    Follows(R.string.settings_notifications_type_follows, R.string.notification_channel_follows_description, NotificationAlert.Audible),
    Other(R.string.settings_notifications_type_other, R.string.notification_channel_other_description, NotificationAlert.Silent);

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
    NotificationType.ScheduledNotePosted,
    NotificationType.ScheduledNoteFailed,
    NotificationType.Test,
    NotificationType.Other -> NotificationCategory.Other
}

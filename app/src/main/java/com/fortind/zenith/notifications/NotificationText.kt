package com.fortind.zenith.notifications

import android.content.res.Resources
import com.fortind.zenith.R

fun FortNotification.titleText(resources: Resources): String {
    val name = actor?.displayName ?: resources.getString(R.string.notifications_someone)
    return when (type) {
        NotificationType.Mention -> resources.getString(R.string.notifications_mention, name)
        NotificationType.Reply -> resources.getString(R.string.notifications_reply, name)
        NotificationType.Quote -> resources.getString(R.string.notifications_quote, name)
        NotificationType.Renote -> resources.getString(R.string.notifications_renote, name)
        NotificationType.Reaction -> resources.getString(R.string.notifications_reaction, name)
        NotificationType.Follow -> resources.getString(R.string.notifications_follow, name)
        NotificationType.FollowRequest -> resources.getString(R.string.notifications_follow_request, name)
        NotificationType.FollowRequestAccepted ->
            resources.getString(R.string.notifications_follow_request_accepted, name)
        NotificationType.Note -> resources.getString(R.string.notifications_note, name)
        NotificationType.Edited -> resources.getString(R.string.notifications_edited, name)
        NotificationType.PollEnded -> resources.getString(R.string.notifications_poll_ended)
        NotificationType.RoleAssigned -> resources.getString(R.string.notifications_role_assigned)
        NotificationType.Achievement -> resources.getString(R.string.notifications_achievement)
        NotificationType.Login -> resources.getString(R.string.notifications_login)
        NotificationType.CreateToken -> resources.getString(R.string.notifications_create_token)
        NotificationType.ExportCompleted -> resources.getString(R.string.notifications_export_completed)
        NotificationType.ScheduledNoteFailed -> resources.getString(R.string.notifications_scheduled_failed)
        NotificationType.Test -> resources.getString(R.string.notifications_test)
        NotificationType.App, NotificationType.Other -> title ?: resources.getString(R.string.notifications_generic)
    }
}

fun FortNotification.previewText(): String? = notePreview ?: body

fun FortNotification.bodyText(): String? {
    val preview = previewText()
    val emoji = reaction?.takeIf { type == NotificationType.Reaction }
    return when {
        emoji != null && preview != null -> "$emoji $preview"
        emoji != null -> emoji
        else -> preview
    }
}

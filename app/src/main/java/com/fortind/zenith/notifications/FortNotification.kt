package com.fortind.zenith.notifications

import com.fortind.zenith.auth.FortInstance
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONObject

enum class NotificationType(val apiName: String) {
    Mention("mention"),
    Reply("reply"),
    Quote("quote"),
    Renote("renote"),
    Reaction("reaction"),
    Follow("follow"),
    FollowRequest("receiveFollowRequest"),
    FollowRequestAccepted("followRequestAccepted"),
    Note("note"),
    Edited("edited"),
    PollEnded("pollEnded"),
    RoleAssigned("roleAssigned"),
    Achievement("achievementEarned"),
    App("app"),
    Login("login"),
    CreateToken("createToken"),
    ExportCompleted("exportCompleted"),
    ScheduledNoteFailed("scheduledNoteFailed"),
    Test("test"),
    Other("");

    companion object {
        fun fromApiName(name: String): NotificationType =
            entries.firstOrNull { it != Other && it.apiName == name } ?: Other
    }
}

data class NotificationActor(
    val id: String,
    val username: String,
    val host: String?,
    val name: String?,
    val avatarUrl: String?,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username

    val handle: String get() = if (host == null) "@$username" else "@$username@$host"

    val profileUrl: String get() = "https://${FortInstance.HOST}/$handle"
}

data class FortNotification(
    val id: String,
    val type: NotificationType,
    val createdAtMillis: Long,
    val actor: NotificationActor?,
    val noteId: String?,
    val notePreview: String?,
    val reaction: String?,
    val reactionEmojiUrl: String?,
    val title: String?,
    val body: String?,
) {
    val webUrl: String?
        get() = when {
            noteId != null -> "https://${FortInstance.HOST}/notes/$noteId"
            actor != null -> actor.profileUrl
            else -> null
        }
}

fun parseNotifications(array: JSONArray): List<FortNotification> =
    (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::parseNotification) }

fun parseNotification(json: JSONObject): FortNotification? {
    val id = json.optStringOrNull("id") ?: return null
    val type = NotificationType.fromApiName(json.optString("type"))
    val note = json.optJSONObject("note")
    val reaction = json.optStringOrNull("reaction")
    return FortNotification(
        id = id,
        type = type,
        createdAtMillis = parseTimestamp(json.optString("createdAt")),
        actor = json.optJSONObject("user")?.let(::parseActor),
        noteId = note?.optStringOrNull("id"),
        notePreview = note?.let { it.optStringOrNull("cw") ?: it.optStringOrNull("text") },
        reaction = reaction,
        reactionEmojiUrl = if (reaction != null && note != null) customEmojiUrl(reaction, note) else null,
        title = json.optStringOrNull("header"),
        body = json.optStringOrNull("body") ?: json.optStringOrNull("reason") ?: json.optStringOrNull("message"),
    )
}

private fun parseActor(json: JSONObject): NotificationActor? {
    val id = json.optStringOrNull("id") ?: return null
    val username = json.optStringOrNull("username") ?: return null
    return NotificationActor(
        id = id,
        username = username,
        host = json.optStringOrNull("host"),
        name = json.optStringOrNull("name"),
        avatarUrl = json.optStringOrNull("avatarUrl"),
    )
}

private fun customEmojiUrl(reaction: String, note: JSONObject): String? {
    if (!reaction.startsWith(':') || !reaction.endsWith(':') || reaction.length < 3) return null
    val key = reaction.substring(1, reaction.length - 1).removeSuffix("@.")
    return note.optJSONObject("reactionEmojis")?.optStringOrNull(key)
}

private fun parseTimestamp(text: String): Long = try {
    Instant.parse(text).toEpochMilli()
} catch (_: DateTimeParseException) {
    0L
}

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

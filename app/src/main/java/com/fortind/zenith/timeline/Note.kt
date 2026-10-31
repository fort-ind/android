package com.fortind.zenith.timeline

import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.mfm.MfmNode
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.net.intMap
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optIntOrNull
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.parseTimestamp
import com.fortind.zenith.net.stringMap
import com.fortind.zenith.net.strings
import org.json.JSONArray
import org.json.JSONObject

enum class Visibility(val apiName: String) {
    Public("public"),
    Home("home"),
    Followers("followers"),
    Specified("specified");

    companion object {
        fun fromApiName(name: String?): Visibility = entries.firstOrNull { it.apiName == name } ?: Public
    }
}

enum class ReactionAcceptance(val apiName: String?) {
    All(null),
    LikeOnly("likeOnly"),
    LikeOnlyForRemote("likeOnlyForRemote"),
    NonSensitiveOnly("nonSensitiveOnly"),
    NonSensitiveOnlyForLocalLikeOnlyForRemote("nonSensitiveOnlyForLocalLikeOnlyForRemote");

    companion object {
        fun fromApiName(name: String?): ReactionAcceptance = entries.firstOrNull { it.apiName == name } ?: All
    }
}

enum class MediaKind { Image, Video, Audio, Other }

data class InstanceTicker(val name: String?, val iconUrl: String?, val themeColor: String?)

data class NoteUser(
    val id: String,
    val username: String,
    val host: String?,
    val name: String?,
    val nameMfm: List<MfmNode>,
    val avatarUrl: String?,
    val avatarBlurhash: String?,
    val emojis: Map<String, String>,
    val isBot: Boolean,
    val isCat: Boolean,
    val instance: InstanceTicker?,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username

    val handle: String get() = if (host == null) "@$username" else "@$username@$host"

    val profileUrl: String get() = "https://${FortInstance.HOST}/$handle"
}

data class NoteFile(
    val id: String,
    val name: String?,
    val mimeType: String,
    val url: String,
    val thumbnailUrl: String?,
    val blurhash: String?,
    val isSensitive: Boolean,
    val altText: String?,
    val width: Int?,
    val height: Int?,
) {
    val kind: MediaKind
        get() = when {
            mimeType.startsWith("image/") -> MediaKind.Image
            mimeType.startsWith("video/") -> MediaKind.Video
            mimeType.startsWith("audio/") -> MediaKind.Audio
            else -> MediaKind.Other
        }

    val aspectRatio: Float?
        get() = if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null
}

data class PollChoice(val text: String, val votes: Int, val isVoted: Boolean)

data class Poll(val choices: List<PollChoice>, val multiple: Boolean, val expiresAtMillis: Long?) {
    val totalVotes: Int get() = choices.sumOf { it.votes }
}

data class Note(
    val id: String,
    val createdAtMillis: Long,
    val editedAtMillis: Long?,
    val user: NoteUser,
    val text: String?,
    val textMfm: List<MfmNode>,
    val cw: String?,
    val cwMfm: List<MfmNode>?,
    val visibility: Visibility,
    val localOnly: Boolean,
    val renote: Note?,
    val reply: Note?,
    val files: List<NoteFile>,
    val poll: Poll?,
    val emojis: Map<String, String>,
    val reactions: Map<String, Int>,
    val reactionEmojis: Map<String, String>,
    val renoteCount: Int,
    val repliesCount: Int,
    val myReaction: String?,
    val reactionAcceptance: ReactionAcceptance,
    val remoteUrl: String?,
    val isHidden: Boolean,
    val deleted: Boolean = false,
    val visibleUserIds: List<String> = emptyList(),
) {
    val isPureRenote: Boolean
        get() = renote != null && text == null && cw == null && files.isEmpty() && poll == null

    val target: Note get() = if (isPureRenote) renote!! else this

    val webUrl: String get() = "https://${FortInstance.HOST}/notes/$id"

    val reactionCount: Int get() = reactions.values.sum()
}

private const val MAX_EMBED_DEPTH = 2

fun parseNotes(array: JSONArray): List<Note> = array.objects().mapNotNull { parseNote(it) }

fun parseNote(json: JSONObject, depth: Int = 0): Note? {
    val id = json.optStringOrNull("id") ?: return null
    val user = json.optJSONObject("user")?.let(::parseNoteUser) ?: return null
    val text = json.optStringOrNull("text")
    val cw = if (json.isNull("cw")) null else json.optString("cw")
    val nested = depth < MAX_EMBED_DEPTH
    return Note(
        id = id,
        createdAtMillis = parseTimestamp(json.optStringOrNull("createdAt")),
        editedAtMillis = json.optStringOrNull("updatedAt")?.let(::parseTimestamp)?.takeIf { it > 0 },
        user = user,
        text = text,
        textMfm = text?.let(MfmParser::parse).orEmpty(),
        cw = cw,
        cwMfm = cw?.let(MfmParser::parse),
        visibility = Visibility.fromApiName(json.optStringOrNull("visibility")),
        localOnly = json.optBoolean("localOnly"),
        renote = if (nested) json.optJSONObject("renote")?.let { parseNote(it, depth + 1) } else null,
        reply = if (nested) json.optJSONObject("reply")?.let { parseNote(it, depth + 1) } else null,
        files = json.optJSONArray("files")?.objects()?.mapNotNull(::parseNoteFile).orEmpty(),
        poll = json.optJSONObject("poll")?.let(::parsePoll),
        emojis = json.optJSONObject("emojis")?.stringMap().orEmpty(),
        reactions = json.optJSONObject("reactions")?.intMap()?.filterValues { it > 0 }.orEmpty(),
        reactionEmojis = json.optJSONObject("reactionEmojis")?.stringMap().orEmpty(),
        renoteCount = json.optInt("renoteCount"),
        repliesCount = json.optInt("repliesCount"),
        myReaction = json.optStringOrNull("myReaction"),
        reactionAcceptance = ReactionAcceptance.fromApiName(json.optStringOrNull("reactionAcceptance")),
        remoteUrl = json.optStringOrNull("url") ?: json.optStringOrNull("uri"),
        isHidden = json.optBoolean("isHidden"),
        visibleUserIds = json.optJSONArray("visibleUserIds")?.strings().orEmpty(),
    )
}

fun parseNoteUser(json: JSONObject): NoteUser? {
    val id = json.optStringOrNull("id") ?: return null
    val username = json.optStringOrNull("username") ?: return null
    val name = json.optStringOrNull("name")
    return NoteUser(
        id = id,
        username = username,
        host = json.optStringOrNull("host"),
        name = name,
        nameMfm = name?.let(MfmParser::parseSimple).orEmpty(),
        avatarUrl = json.optStringOrNull("avatarUrl"),
        avatarBlurhash = json.optStringOrNull("avatarBlurhash"),
        emojis = json.optJSONObject("emojis")?.stringMap().orEmpty(),
        isBot = json.optBoolean("isBot"),
        isCat = json.optBoolean("isCat"),
        instance = json.optJSONObject("instance")?.let {
            InstanceTicker(
                name = it.optStringOrNull("name"),
                iconUrl = it.optStringOrNull("faviconUrl") ?: it.optStringOrNull("iconUrl"),
                themeColor = it.optStringOrNull("themeColor"),
            )
        },
    )
}

fun parseNoteFile(json: JSONObject): NoteFile? {
    val id = json.optStringOrNull("id") ?: return null
    val url = json.optStringOrNull("url") ?: return null
    val properties = json.optJSONObject("properties")
    return NoteFile(
        id = id,
        name = json.optStringOrNull("name"),
        mimeType = json.optStringOrNull("type") ?: "application/octet-stream",
        url = url,
        thumbnailUrl = json.optStringOrNull("thumbnailUrl"),
        blurhash = json.optStringOrNull("blurhash"),
        isSensitive = json.optBoolean("isSensitive"),
        altText = json.optStringOrNull("comment")?.takeIf { it.isNotBlank() },
        width = properties?.optIntOrNull("width")?.takeIf { it > 0 },
        height = properties?.optIntOrNull("height")?.takeIf { it > 0 },
    )
}

private fun parsePoll(json: JSONObject): Poll? {
    val choices = json.optJSONArray("choices")?.objects()?.map {
        PollChoice(text = it.optString("text"), votes = it.optInt("votes"), isVoted = it.optBoolean("isVoted"))
    }.orEmpty()
    if (choices.isEmpty()) return null
    return Poll(
        choices = choices,
        multiple = json.optBoolean("multiple"),
        expiresAtMillis = json.optStringOrNull("expiresAt")?.let(::parseTimestamp)?.takeIf { it > 0 },
    )
}

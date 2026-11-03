package com.fortind.zenith.people

import com.fortind.zenith.mfm.MfmNode
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.parseTimestamp
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.parseNoteUser
import com.fortind.zenith.timeline.parseNotes
import org.json.JSONObject

enum class ListVisibility(val apiName: String?) {
    Public("public"),
    Followers("followers"),
    Private("private"),
    Unspecified(null);

    companion object {
        fun fromApiName(name: String?): ListVisibility = entries.firstOrNull { it.apiName == name } ?: Unspecified
    }
}

data class UserRole(
    val name: String,
    val color: String?,
    val iconUrl: String?,
    val description: String?,
)

data class UserField(
    val name: String,
    val nameMfm: List<MfmNode>,
    val valueMfm: List<MfmNode>,
)

data class UserDetail(
    val user: NoteUser,
    val bannerUrl: String? = null,
    val bioMfm: List<MfmNode> = emptyList(),
    val location: String? = null,
    val birthday: String? = null,
    val createdAtMillis: Long = 0L,
    val fields: List<UserField> = emptyList(),
    val roles: List<UserRole> = emptyList(),
    val pinnedNotes: List<Note> = emptyList(),
    val notesCount: Int = 0,
    val followingCount: Int = 0,
    val followersCount: Int = 0,
    val followingVisibility: ListVisibility = ListVisibility.Unspecified,
    val followersVisibility: ListVisibility = ListVisibility.Unspecified,
    val isFollowing: Boolean = false,
    val isFollowed: Boolean = false,
    val hasPendingRequestFromYou: Boolean = false,
    val isLocked: Boolean = false,
    val isSuspended: Boolean = false,
    val hasMoved: Boolean = false,
    val isMuted: Boolean = false,
    val isRenoteMuted: Boolean = false,
    val isBlocking: Boolean = false,
    val isBlocked: Boolean = false,
) {
    val id: String get() = user.id

    fun showsFollowingCount(myUserId: String?): Boolean = id == myUserId || followingVisibility.admits(isFollowing)

    fun showsFollowersCount(myUserId: String?): Boolean = id == myUserId || followersVisibility.admits(isFollowing)

    private fun ListVisibility.admits(following: Boolean): Boolean = when (this) {
        ListVisibility.Public, ListVisibility.Unspecified -> true
        ListVisibility.Followers -> following
        ListVisibility.Private -> false
    }
}

fun parseUserDetail(json: JSONObject): UserDetail? {
    val user = parseNoteUser(json) ?: return null
    return UserDetail(
        user = user,
        bannerUrl = json.optStringOrNull("bannerUrl"),
        bioMfm = json.optStringOrNull("description")?.let { MfmParser.parse(it) }.orEmpty(),
        location = json.optStringOrNull("location"),
        birthday = json.optStringOrNull("birthday"),
        createdAtMillis = json.optStringOrNull("createdAt")?.let(::parseTimestamp) ?: 0L,
        fields = json.optJSONArray("fields")?.objects().orEmpty().mapNotNull(::parseField),
        roles = json.optJSONArray("roles")?.objects().orEmpty().mapNotNull(::parseRole),
        pinnedNotes = json.optJSONArray("pinnedNotes")?.let(::parseNotes).orEmpty(),
        notesCount = json.optInt("notesCount"),
        followingCount = json.optInt("followingCount"),
        followersCount = json.optInt("followersCount"),
        followingVisibility = ListVisibility.fromApiName(json.optStringOrNull("followingVisibility")),
        followersVisibility = ListVisibility.fromApiName(json.optStringOrNull("followersVisibility")),
        isFollowing = json.optBoolean("isFollowing"),
        isFollowed = json.optBoolean("isFollowed"),
        hasPendingRequestFromYou = json.optBoolean("hasPendingFollowRequestFromYou"),
        isLocked = json.optBoolean("isLocked"),
        isSuspended = json.optBoolean("isSuspended"),
        hasMoved = json.optStringOrNull("movedTo") != null,
        isMuted = json.optBoolean("isMuted"),
        isRenoteMuted = json.optBoolean("isRenoteMuted"),
        isBlocking = json.optBoolean("isBlocking"),
        isBlocked = json.optBoolean("isBlocked"),
    )
}

private fun parseField(json: JSONObject): UserField? {
    val name = json.optStringOrNull("name").orEmpty()
    val value = json.optStringOrNull("value").orEmpty()
    if (name.isEmpty() && value.isEmpty()) return null
    return UserField(name, MfmParser.parseSimple(name), MfmParser.parse(value))
}

private fun parseRole(json: JSONObject): UserRole? {
    val name = json.optStringOrNull("name")?.takeIf { it.isNotBlank() } ?: return null
    return UserRole(
        name = name,
        color = json.optStringOrNull("color"),
        iconUrl = json.optStringOrNull("iconUrl"),
        description = json.optStringOrNull("description"),
    )
}

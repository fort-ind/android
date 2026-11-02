package com.fortind.zenith.account

import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.parseTimestamp
import com.fortind.zenith.net.translateAuthError
import org.json.JSONObject

enum class ModerationKind(val listPath: String, val userKey: String) {
    Mute("/api/mute/list", "mutee"),
    RenoteMute("/api/renote-mute/list", "mutee"),
    Block("/api/blocking/list", "blockee"),
}

data class ModeratedUser(
    val id: String,
    val username: String,
    val host: String?,
    val name: String?,
    val avatarUrl: String?,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username

    val handle: String get() = if (host == null) "@$username@${FortInstance.HOST}" else "@$username@$host"
}

data class ModerationEntry(
    val id: String,
    val kind: ModerationKind,
    val user: ModeratedUser,
    val expiresAtMillis: Long?,
)

fun parseModerationEntry(kind: ModerationKind, json: JSONObject): ModerationEntry? {
    val id = json.optStringOrNull("id") ?: return null
    val user = json.optJSONObject(kind.userKey) ?: return null
    val userId = user.optStringOrNull("id") ?: return null
    val username = user.optStringOrNull("username") ?: return null
    return ModerationEntry(
        id = id,
        kind = kind,
        user = ModeratedUser(
            id = userId,
            username = username,
            host = user.optStringOrNull("host"),
            name = user.optStringOrNull("name"),
            avatarUrl = user.optStringOrNull("avatarUrl"),
        ),
        expiresAtMillis = json.optStringOrNull("expiresAt")?.let(::parseTimestamp)?.takeIf { it > 0L },
    )
}

interface ModerationApi {
    suspend fun list(token: String, kind: ModerationKind, untilId: String?, limit: Int): List<ModerationEntry>

    suspend fun mute(token: String, userId: String, expiresAtMillis: Long?)

    suspend fun unmute(token: String, userId: String)

    suspend fun hideRenotes(token: String, userId: String)

    suspend fun showRenotes(token: String, userId: String)

    suspend fun block(token: String, userId: String)

    suspend fun unblock(token: String, userId: String)
}

class HttpModerationApi(private val http: FortHttp) : ModerationApi {

    override suspend fun list(token: String, kind: ModerationKind, untilId: String?, limit: Int): List<ModerationEntry> {
        val body = JSONObject().put("i", token).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        val array = try {
            http.postJsonArray(kind.listPath, body)
        } catch (e: HttpStatusException) {
            throw translateAuthError(e)
        }
        return array.objects().mapNotNull { parseModerationEntry(kind, it) }
    }

    override suspend fun mute(token: String, userId: String, expiresAtMillis: Long?) {
        val body = request(token, userId).put("expiresAt", expiresAtMillis ?: JSONObject.NULL)
        send("/api/mute/create", body, ApiErrorCodes.ALREADY_MUTING)
    }

    override suspend fun unmute(token: String, userId: String) {
        send("/api/mute/delete", request(token, userId), ApiErrorCodes.NOT_MUTING)
    }

    override suspend fun hideRenotes(token: String, userId: String) {
        send("/api/renote-mute/create", request(token, userId), ApiErrorCodes.ALREADY_MUTING)
    }

    override suspend fun showRenotes(token: String, userId: String) {
        send("/api/renote-mute/delete", request(token, userId), ApiErrorCodes.NOT_MUTING)
    }

    override suspend fun block(token: String, userId: String) {
        send("/api/blocking/create", request(token, userId), ApiErrorCodes.ALREADY_BLOCKING)
    }

    override suspend fun unblock(token: String, userId: String) {
        send("/api/blocking/delete", request(token, userId), ApiErrorCodes.NOT_BLOCKING)
    }

    private fun request(token: String, userId: String): JSONObject = JSONObject().put("i", token).put("userId", userId)

    private suspend fun send(path: String, body: JSONObject, alreadyDone: String) {
        try {
            http.postDiscardingResponse(path, body)
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode != alreadyDone) throw translateAuthError(e)
        }
    }
}

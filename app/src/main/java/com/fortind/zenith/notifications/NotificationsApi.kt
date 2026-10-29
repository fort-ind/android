package com.fortind.zenith.notifications

import com.fortind.zenith.auth.FortHttp
import com.fortind.zenith.auth.HttpStatusException
import com.fortind.zenith.auth.TokenRejectedException
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

class MissingPermissionException : IOException("The access token lacks a required permission")

interface NotificationsApi {
    suspend fun fetch(token: String, types: List<NotificationType>?, untilId: String?, limit: Int): List<FortNotification>

    suspend fun unreadCount(token: String): Int

    suspend fun markAllAsRead(token: String)

    suspend fun respondToFollowRequest(token: String, userId: String, accept: Boolean)
}

fun parseUnreadCount(json: JSONObject): Int {
    if (json.has("unreadNotificationsCount") && !json.isNull("unreadNotificationsCount")) {
        return json.optInt("unreadNotificationsCount").coerceAtLeast(0)
    }
    return if (json.optBoolean("hasUnreadNotification")) 1 else 0
}

class HttpNotificationsApi(private val http: FortHttp) : NotificationsApi {

    override suspend fun fetch(
        token: String,
        types: List<NotificationType>?,
        untilId: String?,
        limit: Int,
    ): List<FortNotification> {
        val body = JSONObject()
            .put("i", token)
            .put("limit", limit)
            .put("markAsRead", false)
        untilId?.let { body.put("untilId", it) }
        types?.let { body.put("includeTypes", JSONArray(it.map(NotificationType::apiName))) }
        return parseNotifications(translatingAuthErrors { http.postJsonArray("/api/i/notifications", body) })
    }

    override suspend fun unreadCount(token: String): Int =
        parseUnreadCount(translatingAuthErrors { http.postJson("/api/i", JSONObject().put("i", token)) })

    override suspend fun markAllAsRead(token: String) {
        translatingAuthErrors { http.postDiscardingResponse("/api/notifications/mark-all-as-read", JSONObject().put("i", token)) }
    }

    override suspend fun respondToFollowRequest(token: String, userId: String, accept: Boolean) {
        val path = if (accept) "/api/following/requests/accept" else "/api/following/requests/reject"
        translatingAuthErrors { http.postDiscardingResponse(path, JSONObject().put("i", token).put("userId", userId)) }
    }

    private suspend fun <T> translatingAuthErrors(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpStatusException) {
        when (e.code) {
            401 -> throw TokenRejectedException(e.code)
            403 -> throw MissingPermissionException()
            else -> throw e
        }
    }
}

package com.fortind.zenith.auth

import java.io.IOException
import org.json.JSONObject

data class CurrentUser(
    val id: String,
    val username: String,
    val name: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username

    val handle: String get() = "@$username@${FortInstance.HOST}"
}

class TokenRejectedException(val code: Int) : IOException("Token rejected (HTTP $code)")

interface UserApi {
    suspend fun fetchCurrentUser(token: String): CurrentUser
}

fun parseCurrentUser(json: JSONObject): CurrentUser {
    val id = json.optString("id")
    val username = json.optString("username")
    if (id.isEmpty() || username.isEmpty()) throw IOException("Malformed user response")
    return CurrentUser(
        id = id,
        username = username,
        name = json.optString("name").takeIf { it.isNotEmpty() },
        avatarUrl = json.optString("avatarUrl").takeIf { it.isNotEmpty() },
        bannerUrl = json.optString("bannerUrl").takeIf { it.isNotEmpty() },
    )
}

class HttpUserApi(private val http: FortHttp) : UserApi {
    override suspend fun fetchCurrentUser(token: String): CurrentUser = try {
        parseCurrentUser(http.postJson("/api/i", JSONObject().put("i", token)))
    } catch (e: HttpStatusException) {
        if (e.code == 401 || e.code == 403) throw TokenRejectedException(e.code) else throw e
    }
}

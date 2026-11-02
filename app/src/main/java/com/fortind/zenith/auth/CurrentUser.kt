package com.fortind.zenith.auth

import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optStringOrNull
import java.io.IOException
import org.json.JSONObject

data class ProfileField(val name: String, val value: String)

data class CurrentUser(
    val id: String,
    val username: String,
    val name: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val description: String? = null,
    val location: String? = null,
    val birthday: String? = null,
    val fields: List<ProfileField> = emptyList(),
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
        name = json.optStringOrNull("name"),
        avatarUrl = json.optStringOrNull("avatarUrl"),
        bannerUrl = json.optStringOrNull("bannerUrl"),
        description = json.optStringOrNull("description"),
        location = json.optStringOrNull("location"),
        birthday = json.optStringOrNull("birthday"),
        fields = json.optJSONArray("fields")?.objects().orEmpty().mapNotNull { entry ->
            val name = entry.optStringOrNull("name") ?: return@mapNotNull null
            ProfileField(name, entry.optStringOrNull("value").orEmpty())
        },
    )
}

class HttpUserApi(private val http: FortHttp) : UserApi {
    override suspend fun fetchCurrentUser(token: String): CurrentUser = try {
        parseCurrentUser(http.postJson("/api/i", JSONObject().put("i", token)))
    } catch (e: HttpStatusException) {
        if (e.code == 401 || e.code == 403) throw TokenRejectedException(e.code) else throw e
    }
}

package com.fortind.zenith.autocomplete

import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.strings
import com.fortind.zenith.net.translateAuthError
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.parseNoteUser
import org.json.JSONObject

interface SuggestionsApi {
    suspend fun users(token: String?, query: String, limit: Int): List<NoteUser>

    suspend fun frequentUsers(token: String, userId: String, limit: Int): List<NoteUser>

    suspend fun hashtags(token: String?, query: String, limit: Int): List<String>
}

class HttpSuggestionsApi(private val http: FortHttp) : SuggestionsApi {

    override suspend fun users(token: String?, query: String, limit: Int): List<NoteUser> {
        val (username, host) = splitHandleQuery(query)
        val body = request(token).put("limit", limit).put("detail", false)
        if (username.isNotEmpty()) body.put("username", username)
        host?.let { body.put("host", it) }
        return call { http.postJsonArray("/api/users/search-by-username-and-host", body) }.objects().mapNotNull(::parseNoteUser)
    }

    override suspend fun frequentUsers(token: String, userId: String, limit: Int): List<NoteUser> {
        val body = request(token).put("userId", userId).put("limit", limit)
        return call { http.postJsonArray("/api/users/get-frequently-replied-users", body) }
            .objects()
            .mapNotNull { entry -> entry.optJSONObject("user")?.let(::parseNoteUser) }
    }

    override suspend fun hashtags(token: String?, query: String, limit: Int): List<String> {
        val body = request(token).put("query", query).put("limit", limit).put("offset", 0)
        return call { http.postJsonArray("/api/hashtags/search", body) }.strings()
    }

    private fun request(token: String?): JSONObject = JSONObject().apply { token?.let { put("i", it) } }

    private suspend fun <T> call(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpStatusException) {
        throw translateAuthError(e)
    }
}

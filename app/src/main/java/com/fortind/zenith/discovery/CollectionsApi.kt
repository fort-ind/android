package com.fortind.zenith.discovery

import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.strings
import com.fortind.zenith.net.translatingAuthErrors
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.parseNoteUser
import java.io.IOException
import org.json.JSONObject

const val LIST_NAME_MAX_LENGTH = 100

data class UserListInfo(val id: String, val name: String, val userIds: List<String>)

data class AntennaInfo(val id: String, val name: String, val hasUnread: Boolean)

data class ListMember(val membershipId: String, val user: NoteUser)

fun parseUserList(json: JSONObject): UserListInfo? {
    val id = json.optStringOrNull("id") ?: return null
    return UserListInfo(
        id = id,
        name = json.optStringOrNull("name").orEmpty(),
        userIds = json.optJSONArray("userIds")?.strings().orEmpty(),
    )
}

fun parseAntenna(json: JSONObject): AntennaInfo? {
    val id = json.optStringOrNull("id") ?: return null
    return AntennaInfo(id, json.optStringOrNull("name").orEmpty(), json.optBoolean("hasUnreadNote"))
}

fun parseListMember(json: JSONObject): ListMember? {
    val id = json.optStringOrNull("id") ?: return null
    val user = json.optJSONObject("user")?.let(::parseNoteUser) ?: return null
    return ListMember(id, user)
}

interface CollectionsApi {
    suspend fun lists(token: String): List<UserListInfo>

    suspend fun antennas(token: String): List<AntennaInfo>

    suspend fun createList(token: String, name: String): UserListInfo

    suspend fun renameList(token: String, listId: String, name: String)

    suspend fun deleteList(token: String, listId: String)

    suspend fun addToList(token: String, listId: String, userId: String)

    suspend fun removeFromList(token: String, listId: String, userId: String)

    suspend fun members(token: String, listId: String, untilId: String?, limit: Int): List<ListMember>
}

class HttpCollectionsApi(private val http: FortHttp) : CollectionsApi {
    override suspend fun lists(token: String): List<UserListInfo> =
        translatingAuthErrors { http.postJsonArray("/api/users/lists/list", request(token)) }.objects().mapNotNull(::parseUserList)

    override suspend fun antennas(token: String): List<AntennaInfo> =
        translatingAuthErrors { http.postJsonArray("/api/antennas/list", request(token)) }.objects().mapNotNull(::parseAntenna)

    override suspend fun createList(token: String, name: String): UserListInfo =
        parseUserList(translatingAuthErrors { http.postJson("/api/users/lists/create", request(token).put("name", name)) })
            ?: throw IOException("Malformed list")

    override suspend fun renameList(token: String, listId: String, name: String) {
        translatingAuthErrors { http.postDiscardingResponse("/api/users/lists/update", request(token).put("listId", listId).put("name", name)) }
    }

    override suspend fun deleteList(token: String, listId: String) {
        translatingAuthErrors { http.postDiscardingResponse("/api/users/lists/delete", request(token).put("listId", listId)) }
    }

    override suspend fun addToList(token: String, listId: String, userId: String) {
        try {
            translatingAuthErrors {
                http.postDiscardingResponse("/api/users/lists/push", request(token).put("listId", listId).put("userId", userId))
            }
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode != ApiErrorCodes.ALREADY_ADDED) throw e
        }
    }

    override suspend fun removeFromList(token: String, listId: String, userId: String) {
        translatingAuthErrors {
            http.postDiscardingResponse("/api/users/lists/pull", request(token).put("listId", listId).put("userId", userId))
        }
    }

    override suspend fun members(token: String, listId: String, untilId: String?, limit: Int): List<ListMember> {
        val body = request(token).put("listId", listId).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        return translatingAuthErrors { http.postJsonArray("/api/users/lists/get-memberships", body) }.objects().mapNotNull(::parseListMember)
    }

    private fun request(token: String): JSONObject = JSONObject().put("i", token)
}

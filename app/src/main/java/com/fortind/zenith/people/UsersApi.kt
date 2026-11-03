package com.fortind.zenith.people

import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.translateAuthError
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class FollowListKind(val path: String?, val userKey: String) {
    Following("/api/users/following", "followee"),
    Followers("/api/users/followers", "follower"),
    Requests(null, "follower"),
}

data class UserListRow(val pagingId: String, val detail: UserDetail)

interface UsersApi {
    suspend fun show(token: String?, userId: String): UserDetail

    suspend fun showByHandle(token: String?, username: String, host: String?): UserDetail

    suspend fun follows(token: String?, userId: String, kind: FollowListKind, untilId: String?, limit: Int): List<UserListRow>

    suspend fun followRequests(token: String, untilId: String?, limit: Int): List<UserListRow>
}

class HttpUsersApi(private val http: FortHttp) : UsersApi {

    override suspend fun show(token: String?, userId: String): UserDetail =
        showRequest(request(token).put("userId", userId))

    override suspend fun showByHandle(token: String?, username: String, host: String?): UserDetail =
        showRequest(request(token).put("username", username).put("host", host ?: JSONObject.NULL))

    override suspend fun follows(token: String?, userId: String, kind: FollowListKind, untilId: String?, limit: Int): List<UserListRow> {
        val path = requireNotNull(kind.path)
        val body = request(token).put("userId", userId).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        return rows(call { http.postJsonArray(path, body) }, kind.userKey)
    }

    override suspend fun followRequests(token: String, untilId: String?, limit: Int): List<UserListRow> {
        val body = request(token).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        return rows(call { http.postJsonArray("/api/following/requests/list", body) }, FollowListKind.Requests.userKey)
    }

    private suspend fun showRequest(body: JSONObject): UserDetail {
        val json = call { http.postJson("/api/users/show", body) }
        return withContext(Dispatchers.Default) { parseUserDetail(json) } ?: throw IOException("Malformed user")
    }

    private suspend fun rows(array: JSONArray, userKey: String): List<UserListRow> = withContext(Dispatchers.Default) {
        array.objects().mapNotNull { entry ->
            val id = entry.optStringOrNull("id") ?: return@mapNotNull null
            val detail = entry.optJSONObject(userKey)?.let(::parseUserDetail) ?: return@mapNotNull null
            UserListRow(id, detail)
        }
    }

    private fun request(token: String?): JSONObject = JSONObject().apply { token?.let { put("i", it) } }

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: HttpStatusException) {
        throw translateAuthError(e)
    }
}

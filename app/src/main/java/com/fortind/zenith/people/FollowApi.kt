package com.fortind.zenith.people

import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.translateAuthError
import org.json.JSONObject

enum class RequestAnswerResult { Done, Gone }

interface FollowApi {
    suspend fun follow(token: String, userId: String)

    suspend fun unfollow(token: String, userId: String)

    suspend fun cancelRequest(token: String, userId: String)

    suspend fun answerRequest(token: String, userId: String, accept: Boolean): RequestAnswerResult
}

class HttpFollowApi(private val http: FortHttp) : FollowApi {

    override suspend fun follow(token: String, userId: String) =
        send("/api/following/create", token, userId, ApiErrorCodes.ALREADY_FOLLOWING)

    override suspend fun unfollow(token: String, userId: String) =
        send("/api/following/delete", token, userId, ApiErrorCodes.NOT_FOLLOWING)

    override suspend fun cancelRequest(token: String, userId: String) =
        send("/api/following/requests/cancel", token, userId, ApiErrorCodes.FOLLOW_REQUEST_NOT_FOUND)

    override suspend fun answerRequest(token: String, userId: String, accept: Boolean): RequestAnswerResult {
        val path = if (accept) "/api/following/requests/accept" else "/api/following/requests/reject"
        return try {
            http.postDiscardingResponse(path, body(token, userId))
            RequestAnswerResult.Done
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode == ApiErrorCodes.NO_FOLLOW_REQUEST) RequestAnswerResult.Gone else throw translateAuthError(e)
        }
    }

    private suspend fun send(path: String, token: String, userId: String, alreadyDone: String) {
        try {
            http.postDiscardingResponse(path, body(token, userId))
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode != alreadyDone) throw translateAuthError(e)
        }
    }

    private fun body(token: String, userId: String): JSONObject = JSONObject().put("i", token).put("userId", userId)
}

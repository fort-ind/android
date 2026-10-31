package com.fortind.zenith.net

import com.fortind.zenith.auth.TokenRejectedException
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

class HttpStatusException(val code: Int, val apiErrorCode: String? = null) :
    IOException(if (apiErrorCode == null) "HTTP $code" else "HTTP $code $apiErrorCode")

class MissingPermissionException : IOException("The access token lacks a required permission")

object ApiErrorCodes {
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val LTL_DISABLED = "LTL_DISABLED"
    const val GTL_DISABLED = "GTL_DISABLED"
    const val ALREADY_REACTED = "ALREADY_REACTED"
    const val NOT_REACTED = "NOT_REACTED"
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val NO_SUCH_NOTE = "NO_SUCH_NOTE"
    const val NO_SUCH_REPLY_TARGET = "NO_SUCH_REPLY_TARGET"
    const val NO_SUCH_RENOTE_TARGET = "NO_SUCH_RENOTE_TARGET"
    const val YOU_HAVE_BEEN_BLOCKED = "YOU_HAVE_BEEN_BLOCKED"
    const val CONTAINS_TOO_MANY_MENTIONS = "CONTAINS_TOO_MANY_MENTIONS"
    const val CONTAINS_PROHIBITED_WORDS = "CONTAINS_PROHIBITED_WORDS"
    const val NO_SUCH_FILE = "NO_SUCH_FILE"
    const val NO_FREE_SPACE = "NO_FREE_SPACE"
    const val FILE_TOO_BIG = "FILE_TOO_BIG"
    const val ALREADY_VOTED = "ALREADY_VOTED"
    const val ALREADY_EXPIRED = "ALREADY_EXPIRED"
    const val ALREADY_FAVORITED = "ALREADY_FAVORITED"
    const val NOT_FAVORITED = "NOT_FAVORITED"
}

const val HTTP_TOO_MANY_REQUESTS = 429

fun parseApiErrorCode(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        JSONObject(body).optJSONObject("error")?.optStringOrNull("code")
    } catch (_: JSONException) {
        null
    }
}

suspend fun <T> translatingAuthErrors(request: suspend () -> T): T = try {
    request()
} catch (e: HttpStatusException) {
    throw translateAuthError(e)
}

fun translateAuthError(e: HttpStatusException): IOException = when {
    e.code == 401 -> TokenRejectedException(e.code)
    e.code == 403 && (e.apiErrorCode == null || e.apiErrorCode == ApiErrorCodes.PERMISSION_DENIED) -> MissingPermissionException()
    else -> e
}

val Throwable.isRateLimited: Boolean
    get() = this is HttpStatusException && (code == HTTP_TOO_MANY_REQUESTS || apiErrorCode == ApiErrorCodes.RATE_LIMIT_EXCEEDED)

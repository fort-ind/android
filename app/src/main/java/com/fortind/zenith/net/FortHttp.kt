package com.fortind.zenith.net

import com.fortind.zenith.auth.FortInstance
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class FortHttp(
    private val host: String = FortInstance.HOST,
    private val rateLimits: RateLimitGate = RateLimitGate(),
) {
    @Volatile
    var onPermissionDenied: (() -> Unit)? = null

    suspend fun postJson(path: String, body: JSONObject): JSONObject {
        val text = post(path, body)
        return try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IOException("Malformed response", e)
        }
    }

    suspend fun postJsonArray(path: String, body: JSONObject): JSONArray {
        val text = post(path, body)
        return try {
            JSONArray(text)
        } catch (e: JSONException) {
            throw IOException("Malformed response", e)
        }
    }

    suspend fun postDiscardingResponse(path: String, body: JSONObject) {
        post(path, body)
    }

    private suspend fun post(path: String, body: JSONObject): String =
        withContext(NonCancellable + Dispatchers.IO) {
            if (rateLimits.isHeld(path)) throw HttpStatusException(HTTP_TOO_MANY_REQUESTS, ApiErrorCodes.RATE_LIMIT_EXCEEDED)
            val connection = URL("https://$host$path").openConnection() as HttpURLConnection
            try {
                val timeout = timeoutFor(path)
                connection.requestMethod = "POST"
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = connection.responseCode
                if (code !in 200..299) {
                    val apiCode = parseApiErrorCode(readErrorBody(connection))
                    if (code == HTTP_TOO_MANY_REQUESTS) {
                        rateLimits.hold(path, connection.getHeaderField("Retry-After"), connection.getHeaderField("X-RateLimit-Clear"))
                    }
                    if (code == HttpURLConnection.HTTP_FORBIDDEN && apiCode == ApiErrorCodes.PERMISSION_DENIED) onPermissionDenied?.invoke()
                    throw HttpStatusException(code, apiCode)
                }
                if (code == HttpURLConnection.HTTP_NO_CONTENT) "" else connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

    private fun readErrorBody(connection: HttpURLConnection): String? = try {
        connection.errorStream?.bufferedReader()?.use { reader ->
            val buffer = CharArray(MAX_ERROR_BODY_CHARS)
            val read = reader.read(buffer)
            if (read <= 0) null else String(buffer, 0, read)
        }
    } catch (_: IOException) {
        null
    }

    private fun timeoutFor(path: String): Int =
        if (path == PATH_ME || path.startsWith(PATH_MIAUTH)) SESSION_TIMEOUT_MS else TIMEOUT_MS

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val SESSION_TIMEOUT_MS = 30_000
        const val PATH_ME = "/api/i"
        const val PATH_MIAUTH = "/api/miauth/"
        const val MAX_ERROR_BODY_CHARS = 4_096
    }
}

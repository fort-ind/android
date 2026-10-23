package com.fortind.zenith.auth

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

class HttpStatusException(val code: Int) : IOException("HTTP $code")


class FortHttp(private val host: String = FortInstance.HOST) {


    suspend fun postJson(path: String, body: JSONObject): JSONObject =
        withContext(NonCancellable + Dispatchers.IO) {
            val connection = URL("https://$host$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = connection.responseCode
                if (code !in 200..299) throw HttpStatusException(code)
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    throw IOException("Malformed response", e)
                }
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

fun parseCheckResponse(json: JSONObject): CheckResult {
    val token = json.optString("token")
    return if (json.optBoolean("ok") && token.isNotEmpty()) CheckResult.Approved(token) else CheckResult.Pending
}

class HttpMiAuthApi(private val http: FortHttp) : MiAuthApi {
    override suspend fun check(session: String): CheckResult =
        parseCheckResponse(http.postJson("/api/miauth/$session/check", JSONObject()))
}

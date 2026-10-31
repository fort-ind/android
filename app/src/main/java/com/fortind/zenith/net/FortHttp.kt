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

class FortHttp(private val host: String = FortInstance.HOST) {

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
                if (code !in 200..299) throw HttpStatusException(code, parseApiErrorCode(readErrorBody(connection)))
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

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_ERROR_BODY_CHARS = 4_096
    }
}

package com.fortind.zenith.drive

import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.media.MediaReader
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.parseApiErrorCode
import com.fortind.zenith.net.translateAuthError
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.timeline.parseNoteFile
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.json.JSONException
import org.json.JSONObject

interface DriveApi {
    suspend fun upload(token: String, media: LocalMedia, onProgress: (Float) -> Unit): NoteFile

    suspend fun update(token: String, fileId: String, altText: String, isSensitive: Boolean)

    suspend fun delete(token: String, fileId: String)
}

class HttpDriveApi(
    private val http: FortHttp,
    client: OkHttpClient,
    private val reader: MediaReader,
    private val host: String = FortInstance.HOST,
) : DriveApi {
    private val uploadClient = client.newBuilder()
        .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun upload(token: String, media: LocalMedia, onProgress: (Float) -> Unit): NoteFile {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("i", token)
            .addFormDataPart("name", media.name)
            .addFormDataPart("file", media.name, StreamingBody(media, reader, onProgress))
            .build()
        val request = Request.Builder().url("https://$host/api/drive/files/create").post(body).build()
        val text = withContext(Dispatchers.IO) {
            uploadClient.newCall(request).await().use { response ->
                val payload = response.body.string()
                if (!response.isSuccessful) throw translateAuthError(HttpStatusException(response.code, parseApiErrorCode(payload)))
                payload
            }
        }
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IOException("Malformed upload response", e)
        }
        return parseNoteFile(json) ?: throw IOException("Malformed upload response")
    }

    override suspend fun update(token: String, fileId: String, altText: String, isSensitive: Boolean) {
        val body = JSONObject().put("i", token).put("fileId", fileId).put("comment", altText).put("isSensitive", isSensitive)
        try {
            http.postDiscardingResponse("/api/drive/files/update", body)
        } catch (e: HttpStatusException) {
            throw translateAuthError(e)
        }
    }

    override suspend fun delete(token: String, fileId: String) {
        try {
            http.postDiscardingResponse("/api/drive/files/delete", JSONObject().put("i", token).put("fileId", fileId))
        } catch (e: HttpStatusException) {
            throw translateAuthError(e)
        }
    }

    private companion object {
        const val UPLOAD_TIMEOUT_SECONDS = 120L
    }
}

private class StreamingBody(
    private val media: LocalMedia,
    private val reader: MediaReader,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType() = media.mimeType.toMediaTypeOrNull()

    override fun contentLength(): Long = media.sizeBytes

    override fun writeTo(sink: BufferedSink) {
        val input: InputStream = reader.open(media.uri)
        input.source().use { source ->
            var written = 0L
            while (true) {
                val read = source.read(sink.buffer, CHUNK_BYTES)
                if (read == -1L) break
                sink.emitCompleteSegments()
                written += read
                if (media.sizeBytes > 0) onProgress((written.toFloat() / media.sizeBytes).coerceAtMost(1f))
            }
        }
    }

    private companion object {
        const val CHUNK_BYTES = 8L * 1024
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}

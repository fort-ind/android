package com.fortind.zenith.timeline

import com.fortind.zenith.net.ApiErrorCodes
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

enum class TimelineKind(
    val endpoint: String,
    val channel: String,
    val requiresAuth: Boolean,
    val supportsPartial: Boolean,
    val supportsReplies: Boolean,
) {
    Home("/api/notes/timeline", "homeTimeline", requiresAuth = true, supportsPartial = true, supportsReplies = false),
    Local("/api/notes/local-timeline", "localTimeline", requiresAuth = false, supportsPartial = true, supportsReplies = true),
    Global("/api/notes/global-timeline", "globalTimeline", requiresAuth = false, supportsPartial = false, supportsReplies = false),
}

class TimelineUnavailableException(val kind: TimelineKind) : IOException("$kind timeline is disabled")

interface NotesApi {
    suspend fun timeline(token: String?, kind: TimelineKind, untilId: String?, sinceId: String?, limit: Int): List<Note>

    suspend fun show(token: String?, noteId: String): Note

    suspend fun conversation(token: String?, noteId: String, limit: Int): List<Note>

    suspend fun children(token: String?, noteId: String, untilId: String?, limit: Int): List<Note>

    suspend fun renotedByMe(token: String, noteId: String, userId: String): Boolean

    suspend fun react(token: String, noteId: String, reaction: String)

    suspend fun unreact(token: String, noteId: String)

    suspend fun renote(token: String, noteId: String, visibility: Visibility, localOnly: Boolean)

    suspend fun unrenote(token: String, noteId: String)

    suspend fun create(token: String, note: NewNote): Note

    suspend fun edit(token: String, noteId: String, note: NewNote): Note

    suspend fun delete(token: String, noteId: String)

    suspend fun vote(token: String, noteId: String, choice: Int)

    suspend fun isBookmarked(token: String, noteId: String): Boolean

    suspend fun bookmark(token: String, noteId: String)

    suspend fun unbookmark(token: String, noteId: String)

    suspend fun bookmarks(token: String, untilId: String?, limit: Int): List<Bookmark>
}

class HttpNotesApi(private val http: FortHttp) : NotesApi {

    override suspend fun timeline(
        token: String?,
        kind: TimelineKind,
        untilId: String?,
        sinceId: String?,
        limit: Int,
    ): List<Note> {
        val body = request(token)
            .put("limit", limit)
            .put("withRenotes", true)
            .put("withFiles", false)
        if (kind.supportsPartial) body.put("allowPartial", true)
        if (kind.supportsReplies) body.put("withReplies", false)
        untilId?.let { body.put("untilId", it) }
        sinceId?.let { body.put("sinceId", it) }
        val notes = notes(kind.endpoint, body) { error ->
            when (error.apiErrorCode) {
                ApiErrorCodes.LTL_DISABLED, ApiErrorCodes.GTL_DISABLED -> TimelineUnavailableException(kind)
                else -> null
            }
        }
        return if (sinceId != null && untilId == null) notes.sortedNewestFirst() else notes
    }

    override suspend fun show(token: String?, noteId: String): Note {
        val json = call { http.postJson("/api/notes/show", request(token).put("noteId", noteId)) }
        return withContext(Dispatchers.Default) { parseNote(json) } ?: throw IOException("Malformed note")
    }

    override suspend fun conversation(token: String?, noteId: String, limit: Int): List<Note> =
        notes("/api/notes/conversation", request(token).put("noteId", noteId).put("limit", limit)).asReversed()

    override suspend fun children(token: String?, noteId: String, untilId: String?, limit: Int): List<Note> {
        val body = request(token).put("noteId", noteId).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        return notes("/api/notes/children", body)
    }

    override suspend fun renotedByMe(token: String, noteId: String, userId: String): Boolean {
        val body = request(token).put("noteId", noteId).put("userId", userId).put("limit", 1).put("quote", false)
        return call { http.postJsonArray("/api/notes/renotes", body) }.length() > 0
    }

    override suspend fun react(token: String, noteId: String, reaction: String) {
        call { http.postDiscardingResponse("/api/notes/reactions/create", request(token).put("noteId", noteId).put("reaction", reaction)) }
    }

    override suspend fun unreact(token: String, noteId: String) {
        call { http.postDiscardingResponse("/api/notes/reactions/delete", request(token).put("noteId", noteId)) }
    }

    override suspend fun renote(token: String, noteId: String, visibility: Visibility, localOnly: Boolean) {
        val body = request(token)
            .put("renoteId", noteId)
            .put("visibility", visibility.apiName)
            .put("localOnly", localOnly)
        call { http.postDiscardingResponse("/api/notes/create", body) }
    }

    override suspend fun unrenote(token: String, noteId: String) {
        call { http.postDiscardingResponse("/api/notes/unrenote", request(token).put("noteId", noteId)) }
    }

    override suspend fun create(token: String, note: NewNote): Note =
        createdNote(call { http.postJson("/api/notes/create", note.toCreateRequest(token)) })

    override suspend fun edit(token: String, noteId: String, note: NewNote): Note =
        createdNote(call { http.postJson("/api/notes/edit", note.toEditRequest(token, noteId)) })

    override suspend fun delete(token: String, noteId: String) {
        call { http.postDiscardingResponse("/api/notes/delete", request(token).put("noteId", noteId)) }
    }

    override suspend fun vote(token: String, noteId: String, choice: Int) {
        call { http.postDiscardingResponse("/api/notes/polls/vote", request(token).put("noteId", noteId).put("choice", choice)) }
    }

    override suspend fun isBookmarked(token: String, noteId: String): Boolean =
        call { http.postJson("/api/notes/state", request(token).put("noteId", noteId)) }.optBoolean("isFavorited")

    override suspend fun bookmark(token: String, noteId: String) {
        call { http.postDiscardingResponse("/api/notes/favorites/create", request(token).put("noteId", noteId)) }
    }

    override suspend fun unbookmark(token: String, noteId: String) {
        call { http.postDiscardingResponse("/api/notes/favorites/delete", request(token).put("noteId", noteId)) }
    }

    override suspend fun bookmarks(token: String, untilId: String?, limit: Int): List<Bookmark> {
        val body = request(token).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        val array = call { http.postJsonArray("/api/i/favorites", body) }
        return withContext(Dispatchers.Default) {
            array.objects().mapNotNull { entry ->
                val id = entry.optStringOrNull("id") ?: return@mapNotNull null
                entry.optJSONObject("note")?.let { parseNote(it) }?.let { Bookmark(id, it) }
            }
        }
    }

    private suspend fun createdNote(json: JSONObject): Note = withContext(Dispatchers.Default) {
        (json.optJSONObject("createdNote") ?: json.takeIf { it.has("id") })?.let { parseNote(it) }
    } ?: throw IOException("Malformed note")

    private fun request(token: String?): JSONObject = JSONObject().apply { token?.let { put("i", it) } }

    private suspend fun notes(
        path: String,
        body: JSONObject,
        mapError: (HttpStatusException) -> IOException? = { null },
    ): List<Note> {
        val array: JSONArray = call(mapError) { http.postJsonArray(path, body) }
        return withContext(Dispatchers.Default) { array.objects().mapNotNull { parseNote(it) } }
    }

    private suspend fun <T> call(
        mapError: (HttpStatusException) -> IOException? = { null },
        request: suspend () -> T,
    ): T = try {
        request()
    } catch (e: HttpStatusException) {
        throw mapError(e) ?: translateAuthError(e)
    }
}

private fun List<Note>.sortedNewestFirst(): List<Note> = sortedWith(NewestFirst)

val NewestFirst: Comparator<Note> = compareByDescending<Note> { it.createdAtMillis }.thenByDescending { it.id }

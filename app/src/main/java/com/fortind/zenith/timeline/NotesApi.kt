package com.fortind.zenith.timeline

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.parseCurrentUser
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
    val channel: String?,
    val requiresAuth: Boolean,
    val supportsPartial: Boolean,
    val supportsReplies: Boolean,
    val oneNotePerPerson: Boolean = false,
) {
    Home("/api/notes/timeline", "homeTimeline", requiresAuth = true, supportsPartial = true, supportsReplies = false),
    Following("/api/notes/following", null, requiresAuth = true, supportsPartial = false, supportsReplies = false, oneNotePerPerson = true),
    Local("/api/notes/local-timeline", "localTimeline", requiresAuth = false, supportsPartial = true, supportsReplies = true),
    Global("/api/notes/global-timeline", "globalTimeline", requiresAuth = false, supportsPartial = false, supportsReplies = false),
    Lists("", null, requiresAuth = true, supportsPartial = false, supportsReplies = false),
}

data class NoteFlags(val isBookmarked: Boolean, val isThreadMuted: Boolean)

data class NoteReactionRow(val id: String, val user: NoteUser, val reaction: String)

fun parseReactionRow(json: JSONObject): NoteReactionRow? {
    val id = json.optStringOrNull("id") ?: return null
    val user = json.optJSONObject("user")?.let(::parseNoteUser) ?: return null
    return NoteReactionRow(id, user, Reactions.keyFor(json.optStringOrNull("type").orEmpty()))
}

enum class CollectionKind { UserList, Antenna }

data class NoteCollection(val kind: CollectionKind, val id: String)

enum class SearchMedia(val apiName: String) { Image("image"), Video("video"), Audio("audio") }

data class NoteSearch(val query: String, val media: SearchMedia? = null, val onlyHere: Boolean = false)

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

    suspend fun noteFlags(token: String, noteId: String): NoteFlags

    suspend fun muteThread(token: String, noteId: String)

    suspend fun unmuteThread(token: String, noteId: String)

    suspend fun pin(token: String, noteId: String): CurrentUser

    suspend fun unpin(token: String, noteId: String): CurrentUser

    suspend fun renotesOf(token: String?, noteId: String, untilId: String?, limit: Int): List<Note>

    suspend fun reactionsOn(token: String?, noteId: String, type: String?, untilId: String?, limit: Int): List<NoteReactionRow>

    suspend fun bookmark(token: String, noteId: String)

    suspend fun unbookmark(token: String, noteId: String)

    suspend fun bookmarks(token: String, untilId: String?, limit: Int): List<Bookmark>

    suspend fun userNotes(token: String?, userId: String, tab: UserNotesTab, untilId: String?, limit: Int): List<Note>

    suspend fun collectionNotes(token: String, collection: NoteCollection, untilId: String?, sinceId: String?, limit: Int): List<Note>

    suspend fun searchNotes(token: String, search: NoteSearch, untilId: String?, limit: Int): List<Note>

    suspend fun notesByTag(token: String?, tag: String, untilId: String?, limit: Int): List<Note>
}

class HttpNotesApi(private val http: FortHttp) : NotesApi {

    override suspend fun timeline(
        token: String?,
        kind: TimelineKind,
        untilId: String?,
        sinceId: String?,
        limit: Int,
    ): List<Note> {
        val body = request(token).put("limit", limit)
        if (kind != TimelineKind.Following) body.put("withRenotes", true).put("withFiles", false)
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

    override suspend fun collectionNotes(
        token: String,
        collection: NoteCollection,
        untilId: String?,
        sinceId: String?,
        limit: Int,
    ): List<Note> {
        val body = request(token).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        sinceId?.let { body.put("sinceId", it) }
        val notes = when (collection.kind) {
            CollectionKind.UserList -> {
                body.put("listId", collection.id).put("withRenotes", true).put("withFiles", false).put("allowPartial", true)
                notes("/api/notes/user-list-timeline", body)
            }
            CollectionKind.Antenna -> notes("/api/antennas/notes", body.put("antennaId", collection.id))
        }
        return if (sinceId != null && untilId == null) notes.sortedNewestFirst() else notes
    }

    override suspend fun searchNotes(token: String, search: NoteSearch, untilId: String?, limit: Int): List<Note> {
        val body = request(token).put("query", search.query).put("limit", limit)
        if (search.onlyHere) body.put("host", ".")
        search.media?.let { body.put("filetype", it.apiName) }
        untilId?.let { body.put("untilId", it) }
        return notes("/api/notes/search", body)
    }

    override suspend fun notesByTag(token: String?, tag: String, untilId: String?, limit: Int): List<Note> {
        val body = request(token).put("tag", tag).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        return notes("/api/notes/search-by-tag", body)
    }

    override suspend fun userNotes(token: String?, userId: String, tab: UserNotesTab, untilId: String?, limit: Int): List<Note> {
        val body = request(token)
            .put("userId", userId)
            .put("limit", limit)
            .put("allowPartial", true)
            .put("withReplies", tab == UserNotesTab.NotesAndReplies)
            .put("withRenotes", tab != UserNotesTab.Media)
            .put("withFiles", tab == UserNotesTab.Media)
        untilId?.let { body.put("untilId", it) }
        return notes("/api/users/notes", body)
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

    override suspend fun noteFlags(token: String, noteId: String): NoteFlags {
        val json = call { http.postJson("/api/notes/state", request(token).put("noteId", noteId)) }
        return NoteFlags(isBookmarked = json.optBoolean("isFavorited"), isThreadMuted = json.optBoolean("isMutedThread"))
    }

    override suspend fun muteThread(token: String, noteId: String) {
        call { http.postDiscardingResponse("/api/notes/thread-muting/create", request(token).put("noteId", noteId)) }
    }

    override suspend fun unmuteThread(token: String, noteId: String) {
        call { http.postDiscardingResponse("/api/notes/thread-muting/delete", request(token).put("noteId", noteId)) }
    }

    override suspend fun pin(token: String, noteId: String): CurrentUser =
        parseCurrentUser(call { http.postJson("/api/i/pin", request(token).put("noteId", noteId)) })

    override suspend fun unpin(token: String, noteId: String): CurrentUser =
        parseCurrentUser(call { http.postJson("/api/i/unpin", request(token).put("noteId", noteId)) })

    override suspend fun renotesOf(token: String?, noteId: String, untilId: String?, limit: Int): List<Note> {
        val body = request(token).put("noteId", noteId).put("limit", limit).put("quote", false)
        untilId?.let { body.put("untilId", it) }
        return notes("/api/notes/renotes", body)
    }

    override suspend fun reactionsOn(token: String?, noteId: String, type: String?, untilId: String?, limit: Int): List<NoteReactionRow> {
        val body = request(token).put("noteId", noteId).put("limit", limit)
        type?.let { body.put("type", it) }
        untilId?.let { body.put("untilId", it) }
        val array: JSONArray = call { http.postJsonArray("/api/notes/reactions", body) }
        return withContext(Dispatchers.Default) { array.objects().mapNotNull(::parseReactionRow) }
    }

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

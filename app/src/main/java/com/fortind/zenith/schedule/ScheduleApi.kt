package com.fortind.zenith.schedule

import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.parseTimestamp
import com.fortind.zenith.net.translateAuthError
import com.fortind.zenith.timeline.NewNote
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.parseNote
import com.fortind.zenith.timeline.toScheduleRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ScheduledNote(
    val id: String,
    val scheduledAtMillis: Long,
    val note: Note,
    val fileIds: List<String>,
) {
    val canEdit: Boolean
        get() {
            val packed = note.files.mapTo(HashSet()) { it.id }
            return fileIds.all { it in packed }
        }
}

fun parseScheduledNote(json: JSONObject): ScheduledNote? {
    val id = json.optStringOrNull("id") ?: return null
    val scheduledAt = parseTimestamp(json.optStringOrNull("scheduledAt")).takeIf { it > 0 } ?: return null
    val body = json.optJSONObject("note") ?: return null
    if (!body.has("id")) body.put("id", id)
    val parsed = parseNote(body) ?: return null
    val recipients = body.optJSONArray("visibleUsers")?.objects().orEmpty().mapNotNull { it.optStringOrNull("id") }
    val fileIds = body.optJSONArray("fileIds")?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
    }.orEmpty()
    return ScheduledNote(
        id = id,
        scheduledAtMillis = scheduledAt,
        note = parsed.copy(visibleUserIds = recipients.ifEmpty { parsed.visibleUserIds }),
        fileIds = fileIds,
    )
}

interface ScheduleApi {
    suspend fun create(token: String, note: NewNote, scheduledAtMillis: Long)

    suspend fun list(token: String, untilId: String?, limit: Int): List<ScheduledNote>

    suspend fun delete(token: String, scheduleId: String)
}

class HttpScheduleApi(private val http: FortHttp) : ScheduleApi {
    override suspend fun create(token: String, note: NewNote, scheduledAtMillis: Long) {
        try {
            http.postDiscardingResponse("/api/notes/schedule/create", note.toScheduleRequest(token, scheduledAtMillis))
        } catch (e: HttpStatusException) {
            throw translateAuthError(e)
        }
    }

    override suspend fun list(token: String, untilId: String?, limit: Int): List<ScheduledNote> {
        val body = JSONObject().put("i", token).put("limit", limit)
        untilId?.let { body.put("untilId", it) }
        val array = try {
            http.postJsonArray("/api/notes/schedule/list", body)
        } catch (e: HttpStatusException) {
            throw translateAuthError(e)
        }
        return withContext(Dispatchers.Default) { array.objects().mapNotNull(::parseScheduledNote) }
    }

    override suspend fun delete(token: String, scheduleId: String) {
        try {
            http.postDiscardingResponse("/api/notes/schedule/delete", JSONObject().put("i", token).put("noteId", scheduleId))
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode == ApiErrorCodes.NO_SUCH_NOTE) return
            throw translateAuthError(e)
        }
    }
}

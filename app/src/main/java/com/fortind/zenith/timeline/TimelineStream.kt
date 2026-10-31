package com.fortind.zenith.timeline

import com.fortind.zenith.net.ChannelEvent
import com.fortind.zenith.net.MisskeyStreaming
import com.fortind.zenith.net.StreamFrame
import com.fortind.zenith.net.optIntOrNull
import com.fortind.zenith.net.optStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONObject

sealed interface NoteUpdate {
    val noteId: String

    data class Reacted(override val noteId: String, val reaction: String, val emojiUrl: String?, val userId: String?) : NoteUpdate

    data class Unreacted(override val noteId: String, val reaction: String, val userId: String?) : NoteUpdate

    data class Deleted(override val noteId: String) : NoteUpdate

    data class PollVoted(override val noteId: String, val choice: Int, val userId: String?) : NoteUpdate

    data class Updated(override val noteId: String, val text: String?, val cw: String?, val hasContent: Boolean) : NoteUpdate
}

sealed interface TimelineStreamEvent {
    data object Connected : TimelineStreamEvent

    data class Posted(val note: Note) : TimelineStreamEvent
}

interface TimelineStream {
    fun events(token: String?, kind: TimelineKind): Flow<TimelineStreamEvent>

    fun updates(token: String?, captured: Flow<Set<String>>): Flow<NoteUpdate>
}

fun parseTimelineEvent(event: ChannelEvent): TimelineStreamEvent? = when (event) {
    ChannelEvent.Connected -> TimelineStreamEvent.Connected
    is ChannelEvent.Message -> if (event.type == "note") event.body?.let(::parseNote)?.let(TimelineStreamEvent::Posted) else null
}

fun parseNoteUpdate(noteId: String, type: String, body: JSONObject?): NoteUpdate? = when (type) {
    "reacted" -> body?.optStringOrNull("reaction")?.let { reaction ->
        NoteUpdate.Reacted(
            noteId = noteId,
            reaction = reaction,
            emojiUrl = body.optJSONObject("emoji")?.optStringOrNull("url"),
            userId = body.optStringOrNull("userId"),
        )
    }
    "unreacted" -> body?.optStringOrNull("reaction")?.let { NoteUpdate.Unreacted(noteId, it, body.optStringOrNull("userId")) }
    "deleted" -> NoteUpdate.Deleted(noteId)
    "pollVoted" -> body?.optIntOrNull("choice")?.let { NoteUpdate.PollVoted(noteId, it, body.optStringOrNull("userId")) }
    "updated" -> NoteUpdate.Updated(
        noteId = noteId,
        text = body?.optStringOrNull("text"),
        cw = body?.optStringOrNull("cw"),
        hasContent = body != null && (body.has("text") || body.has("cw")),
    )
    else -> null
}

class StreamingTimelineStream(private val streaming: MisskeyStreaming) : TimelineStream {
    override fun events(token: String?, kind: TimelineKind): Flow<TimelineStreamEvent> {
        val params = JSONObject().put("withRenotes", true)
        if (kind.supportsReplies) params.put("withReplies", false)
        return streaming.connection(token).channel(kind.channel, params)
            .mapNotNull(::parseTimelineEvent)
            .flowOn(Dispatchers.Default)
    }

    override fun updates(token: String?, captured: Flow<Set<String>>): Flow<NoteUpdate> =
        streaming.connection(token).noteUpdates(captured)
            .mapNotNull { frame: StreamFrame.NoteUpdated -> parseNoteUpdate(frame.noteId, frame.type, frame.body) }
}

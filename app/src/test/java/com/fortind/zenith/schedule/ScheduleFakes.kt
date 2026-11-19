package com.fortind.zenith.schedule

import com.fortind.zenith.timeline.NewNote
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.testNote
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class FakeScheduleApi : ScheduleApi {
    val created = CopyOnWriteArrayList<Pair<NewNote, Long>>()
    val deleted = CopyOnWriteArrayList<String>()
    val listCalls = CopyOnWriteArrayList<String?>()
    var items: List<ScheduledNote> = emptyList()
    var createFailure: IOException? = null
    var listFailure: IOException? = null
    var deleteFailure: IOException? = null

    override suspend fun create(token: String, note: NewNote, scheduledAtMillis: Long) {
        created += note to scheduledAtMillis
        createFailure?.let { throw it }
    }

    override suspend fun list(token: String, untilId: String?, limit: Int): List<ScheduledNote> {
        listCalls += untilId
        listFailure?.let { throw it }
        return items
    }

    override suspend fun delete(token: String, scheduleId: String) {
        deleted += scheduleId
        deleteFailure?.let { throw it }
        items = items.filterNot { it.id == scheduleId }
    }
}

fun testScheduled(id: String, atMillis: Long, note: Note = testNote(id), fileIds: List<String> = emptyList()) =
    ScheduledNote(id = id, scheduledAtMillis = atMillis, note = note, fileIds = fileIds)

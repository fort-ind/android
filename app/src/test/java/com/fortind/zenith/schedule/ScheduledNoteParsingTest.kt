package com.fortind.zenith.schedule

import com.fortind.zenith.net.parseTimestamp
import com.fortind.zenith.timeline.Visibility
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledNoteParsingTest {

    private fun entry(
        scheduledAt: String = "2026-11-20T09:00:00.000Z",
        noteId: String? = "s1",
    ): JSONObject = JSONObject(
        """
        {
          "id": "s1",
          "userId": "me",
          "scheduledAt": "$scheduledAt",
          "note": {
            ${if (noteId != null) "\"id\": \"$noteId\"," else ""}
            "createdAt": "2026-11-20T09:00:00.000Z",
            "text": "good morning",
            "cw": null,
            "visibility": "public",
            "fileIds": [],
            "visibleUsers": [],
            "isSchedule": true,
            "user": {"id": "me", "username": "me"}
          }
        }
        """.trimIndent(),
    )

    @Test
    fun readsTheScheduleIdTimeAndNote() {
        val parsed = parseScheduledNote(entry())!!

        assertEquals("s1", parsed.id)
        assertEquals(parseTimestamp("2026-11-20T09:00:00.000Z"), parsed.scheduledAtMillis)
        assertEquals("good morning", parsed.note.text)
        assertEquals(Visibility.Public, parsed.note.visibility)
        assertTrue(parsed.canEdit)
    }

    @Test
    fun aNoteWithoutItsOwnIdTakesTheScheduleId() {
        val parsed = parseScheduledNote(entry(noteId = null))!!

        assertEquals("s1", parsed.note.id)
    }

    @Test
    fun directRecipientsComeFromVisibleUsers() {
        val json = entry()
        json.getJSONObject("note")
            .put("visibility", "specified")
            .put(
                "visibleUsers",
                JSONArray().put(JSONObject().put("id", "u2").put("username", "sam")).put(JSONObject().put("id", "u3").put("username", "kit")),
            )

        val parsed = parseScheduledNote(json)!!

        assertEquals(listOf("u2", "u3"), parsed.note.visibleUserIds)
        assertEquals(Visibility.Specified, parsed.note.visibility)
    }

    @Test
    fun attachmentsThatWerePackedCanBeEdited() {
        val json = entry()
        json.getJSONObject("note")
            .put("fileIds", JSONArray().put("f1"))
            .put(
                "files",
                JSONArray().put(JSONObject().put("id", "f1").put("url", "https://f/1").put("type", "image/png")),
            )

        val parsed = parseScheduledNote(json)!!

        assertEquals(listOf("f1"), parsed.fileIds)
        assertEquals(1, parsed.note.files.size)
        assertTrue(parsed.canEdit)
    }

    @Test
    fun aNoteWhoseAttachmentsWereNotPackedCannotBeEditedWithoutLosingThem() {
        val json = entry()
        json.getJSONObject("note").put("fileIds", JSONArray().put("f1"))

        val parsed = parseScheduledNote(json)!!

        assertFalse(parsed.canEdit)
    }

    @Test
    fun aReplyKeepsItsParent() {
        val json = entry()
        json.getJSONObject("note").put(
            "reply",
            JSONObject().put("id", "p1").put("createdAt", "2026-11-19T09:00:00.000Z").put("user", JSONObject().put("id", "u9").put("username", "pat")),
        )

        val parsed = parseScheduledNote(json)!!

        assertEquals("p1", parsed.note.reply?.id)
    }

    @Test
    fun anUnparsableTimeDropsTheEntry() {
        assertNull(parseScheduledNote(entry(scheduledAt = "soon")))
    }

    @Test
    fun anEntryWithoutANoteOrIdIsDropped() {
        assertNull(parseScheduledNote(JSONObject().put("id", "s1").put("scheduledAt", "2026-11-20T09:00:00.000Z")))
        assertNull(parseScheduledNote(JSONObject().put("scheduledAt", "2026-11-20T09:00:00.000Z")))
        assertNotNull(parseScheduledNote(entry()))
    }
}

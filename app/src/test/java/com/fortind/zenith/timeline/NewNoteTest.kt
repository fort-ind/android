package com.fortind.zenith.timeline

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewNoteTest {

    @Test
    fun aPlainPostSendsOnlyWhatItNeeds() {
        val json = NewNote(text = "hello").toCreateRequest("tok")

        assertEquals("tok", json.getString("i"))
        assertEquals("hello", json.getString("text"))
        assertEquals("public", json.getString("visibility"))
        assertFalse(json.getBoolean("localOnly"))
        assertFalse(json.has("cw"))
        assertFalse(json.has("replyId"))
        assertFalse(json.has("renoteId"))
        assertFalse(json.has("fileIds"))
        assertFalse(json.has("visibleUserIds"))
    }

    @Test
    fun repliesAndQuotesCarryTheirTargets() {
        val reply = NewNote(text = "re", replyId = "n1").toCreateRequest("tok")
        val quote = NewNote(text = "look", renoteId = "n2").toCreateRequest("tok")

        assertEquals("n1", reply.getString("replyId"))
        assertEquals("n2", quote.getString("renoteId"))
    }

    @Test
    fun anEmptyContentWarningIsStillSent() {
        val json = NewNote(text = "hi", cw = "").toCreateRequest("tok")

        assertTrue(json.has("cw"))
        assertEquals("", json.getString("cw"))
    }

    @Test
    fun recipientsAreOnlySentForDirectNotes() {
        val direct = NewNote(text = "psst", visibility = Visibility.Specified, visibleUserIds = listOf("u1", "u2")).toCreateRequest("tok")
        val followers = NewNote(text = "psst", visibility = Visibility.Followers, visibleUserIds = listOf("u1")).toCreateRequest("tok")

        assertEquals(listOf("u1", "u2"), direct.getJSONArray("visibleUserIds").let { array -> List(array.length(), array::getString) })
        assertFalse(followers.has("visibleUserIds"))
    }

    @Test
    fun attachmentsAreSentAsFileIds() {
        val json = NewNote(text = "pics", fileIds = listOf("f1", "f2")).toCreateRequest("tok")

        assertEquals(2, json.getJSONArray("fileIds").length())
    }

    @Test
    fun editsNameTheNoteAndExplicitlyClearAbsentFields() {
        val json = NewNote(text = "fixed", cw = null).toEditRequest("tok", "n9")

        assertEquals("n9", json.getString("editId"))
        assertEquals("fixed", json.getString("text"))
        assertTrue(json.isNull("cw"))
        assertEquals(0, json.getJSONArray("fileIds").length())
    }

    @Test
    fun editingAQuoteKeepsTheQuotedNote() {
        val json = NewNote(text = "fixed", renoteId = "q1").toEditRequest("tok", "n9")

        assertEquals("q1", json.getString("renoteId"))
    }

    @Test
    fun editingWithoutAQuoteSendsNoRenoteId() {
        val json = NewNote(text = "fixed").toEditRequest("tok", "n9")

        assertFalse(json.has("renoteId"))
        assertFalse(json.has("replyId"))
    }

    @Test
    fun editingAPollResendsItUnchanged() {
        val poll = PollBody(choices = listOf("a", "b"), multiple = true, expiresAtMillis = 1_800_000_000_123L)

        val json = NewNote(text = "fixed", poll = poll).toEditRequest("tok", "n9").getJSONObject("poll")

        assertEquals(listOf("a", "b"), json.getJSONArray("choices").let { array -> List(array.length(), array::getString) })
        assertTrue(json.getBoolean("multiple"))
        assertEquals(1_800_000_000_123L, json.getLong("expiresAt"))
    }

    @Test
    fun aPollWithNoEndSendsNullExpiry() {
        val poll = PollBody(choices = listOf("a", "b"), multiple = false, expiresAtMillis = null)

        val json = NewNote(text = "fixed", poll = poll).toEditRequest("tok", "n9").getJSONObject("poll")

        assertTrue(json.isNull("expiresAt"))
    }

    @Test
    fun editingWithoutAPollOmitsIt() {
        assertFalse(NewNote(text = "fixed").toEditRequest("tok", "n9").has("poll"))
    }

    @Test
    fun editingSendsTheReactionLimitEvenWhenThereIsNone() {
        val limited = NewNote(text = "fixed", reactionAcceptance = ReactionAcceptance.LikeOnly).toEditRequest("tok", "n9")
        val open = NewNote(text = "fixed").toEditRequest("tok", "n9")

        assertEquals("likeOnly", limited.getString("reactionAcceptance"))
        assertTrue(open.has("reactionAcceptance"))
        assertTrue(open.isNull("reactionAcceptance"))
    }

    @Test
    fun blankTextIsOmittedFromCreateButNulledInEdit() {
        val create = NewNote(text = "   ", fileIds = listOf("f1")).toCreateRequest("tok")
        val edit = NewNote(text = "   ", fileIds = listOf("f1")).toEditRequest("tok", "n9")

        assertFalse(create.has("text"))
        assertEquals(JSONObject.NULL, edit.get("text"))
    }

    @Test
    fun aScheduledNoteCarriesItsTimeInMilliseconds() {
        val json = NewNote(text = "later").toScheduleRequest("tok", 1_800_000_000_123L)

        assertEquals("tok", json.getString("i"))
        assertEquals("later", json.getString("text"))
        assertEquals(1_800_000_000_123L, json.getJSONObject("scheduleNote").getLong("scheduledAt"))
    }

    @Test
    fun aScheduledNoteNeverSendsLocalOnly() {
        val json = NewNote(text = "later", localOnly = true).toScheduleRequest("tok", 1L)

        assertFalse(json.has("localOnly"))
    }

    @Test
    fun aScheduledNoteLeavesOutAnEmptyContentWarning() {
        val empty = NewNote(text = "later", cw = "").toScheduleRequest("tok", 1L)
        val filled = NewNote(text = "later", cw = "spoilers").toScheduleRequest("tok", 1L)

        assertFalse(empty.has("cw"))
        assertEquals("spoilers", filled.getString("cw"))
    }

    @Test
    fun aScheduledNoteKeepsItsTargetsFilesAndRecipients() {
        val reply = NewNote(text = "re", replyId = "n1", fileIds = listOf("f1")).toScheduleRequest("tok", 1L)
        val direct = NewNote(text = "psst", visibility = Visibility.Specified, visibleUserIds = listOf("u1")).toScheduleRequest("tok", 1L)
        val followers = NewNote(text = "psst", visibility = Visibility.Followers, visibleUserIds = listOf("u1")).toScheduleRequest("tok", 1L)

        assertEquals("n1", reply.getString("replyId"))
        assertEquals(1, reply.getJSONArray("fileIds").length())
        assertEquals(1, direct.getJSONArray("visibleUserIds").length())
        assertFalse(followers.has("visibleUserIds"))
    }

    @Test
    fun aScheduledPollKeepsAPresetAsRelativeToThePostingTime() {
        val poll = PollBody(choices = listOf("a", "b"), multiple = false, expiresAtMillis = null, expiredAfterMillis = 3_600_000L)

        val json = NewNote(text = "vote", poll = poll).toScheduleRequest("tok", 1L).getJSONObject("poll")

        assertEquals(3_600_000L, json.getLong("expiredAfter"))
        assertFalse(json.has("expiresAt"))
    }
}

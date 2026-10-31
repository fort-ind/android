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
    fun blankTextIsOmittedFromCreateButNulledInEdit() {
        val create = NewNote(text = "   ", fileIds = listOf("f1")).toCreateRequest("tok")
        val edit = NewNote(text = "   ", fileIds = listOf("f1")).toEditRequest("tok", "n9")

        assertFalse(create.has("text"))
        assertEquals(JSONObject.NULL, edit.get("text"))
    }
}

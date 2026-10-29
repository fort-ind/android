package com.fortind.zenith.notifications

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParsingTest {

    private fun user(id: String = "u1", username: String = "alice", host: String? = null, name: String? = "Alice") =
        JSONObject()
            .put("id", id)
            .put("username", username)
            .put("host", host ?: JSONObject.NULL)
            .put("name", name ?: JSONObject.NULL)
            .put("avatarUrl", "https://cdn.example/$username.png")

    private fun note(id: String = "note1", text: String? = "hello", cw: String? = null) = JSONObject()
        .put("id", id)
        .put("text", text ?: JSONObject.NULL)
        .put("cw", cw ?: JSONObject.NULL)
        .put("reactionEmojis", JSONObject().put("blobcat", "https://cdn.example/blobcat.png"))

    private fun base(type: String) = JSONObject()
        .put("id", "n1")
        .put("createdAt", "2025-10-28T10:00:00.000Z")
        .put("type", type)

    @Test
    fun reactionWithCustomEmojiResolvesItsImage() {
        val json = base("reaction").put("user", user()).put("note", note()).put("reaction", ":blobcat@.:")

        val parsed = parseNotification(json)!!

        assertEquals(NotificationType.Reaction, parsed.type)
        assertEquals("https://cdn.example/blobcat.png", parsed.reactionEmojiUrl)
        assertEquals("note1", parsed.noteId)
        assertEquals("hello", parsed.notePreview)
        assertEquals("Alice", parsed.actor?.displayName)
        assertEquals(Instant.parse("2025-10-28T10:00:00.000Z").toEpochMilli(), parsed.createdAtMillis)
    }

    @Test
    fun unicodeReactionHasNoImage() {
        val json = base("reaction").put("user", user()).put("note", note()).put("reaction", "👍")

        val parsed = parseNotification(json)!!

        assertEquals("👍", parsed.reaction)
        assertNull(parsed.reactionEmojiUrl)
    }

    @Test
    fun contentWarningIsPreferredOverTheNoteText() {
        val json = base("mention").put("user", user()).put("note", note(text = "secret", cw = "spoilers"))

        assertEquals("spoilers", parseNotification(json)!!.notePreview)
    }

    @Test
    fun notificationsWithANoteLinkToTheNote() {
        val json = base("reply").put("user", user()).put("note", note(id = "abc123"))

        assertEquals("https://social.fort1nd.com/notes/abc123", parseNotification(json)!!.webUrl)
    }

    @Test
    fun followLinksToTheProfileAndQualifiesRemoteUsers() {
        val local = parseNotification(base("follow").put("user", user()))!!
        val remote = parseNotification(base("follow").put("user", user(username = "bob", host = "example.org")))!!

        assertEquals("https://social.fort1nd.com/@alice", local.webUrl)
        assertEquals("https://social.fort1nd.com/@bob@example.org", remote.webUrl)
    }

    @Test
    fun nullNameFallsBackToTheUsernameInsteadOfTheWordNull() {
        val parsed = parseNotification(base("follow").put("user", user(name = null)))!!

        assertNull(parsed.actor?.name)
        assertEquals("alice", parsed.actor?.displayName)
    }

    @Test
    fun unknownTypesBecomeOtherWithNoLink() {
        val parsed = parseNotification(base("someTypeFromTheFuture"))!!

        assertEquals(NotificationType.Other, parsed.type)
        assertNull(parsed.actor)
        assertNull(parsed.webUrl)
    }

    @Test
    fun appNotificationsCarryTheirHeaderAndBody() {
        val parsed = parseNotification(
            base("app").put("header", "Backup done").put("body", "All saved").put("icon", JSONObject.NULL)
        )!!

        assertEquals(NotificationType.App, parsed.type)
        assertEquals("Backup done", parsed.title)
        assertEquals("All saved", parsed.body)
    }

    @Test
    fun followRequestsKeepTheRequesterForAcceptAndReject() {
        val parsed = parseNotification(base("receiveFollowRequest").put("user", user(id = "requester")))!!

        assertEquals(NotificationType.FollowRequest, parsed.type)
        assertEquals("requester", parsed.actor?.id)
    }

    @Test
    fun entriesWithoutAnIdAreSkipped() {
        val array = JSONArray()
            .put(base("follow").put("user", user()))
            .put(JSONObject().put("type", "follow"))
            .put(base("mention").put("id", "n2").put("user", user()).put("note", note()))

        val parsed = parseNotifications(array)

        assertEquals(listOf("n1", "n2"), parsed.map { it.id })
    }

    @Test
    fun unreadCountPrefersTheNumericField() {
        assertEquals(7, parseUnreadCount(JSONObject().put("unreadNotificationsCount", 7).put("hasUnreadNotification", true)))
    }

    @Test
    fun unreadCountFallsBackToTheBooleanFlag() {
        assertEquals(1, parseUnreadCount(JSONObject().put("hasUnreadNotification", true)))
        assertEquals(0, parseUnreadCount(JSONObject().put("hasUnreadNotification", false)))
        assertEquals(0, parseUnreadCount(JSONObject()))
    }
}

package com.fortind.zenith.timeline

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLogicTest {

    @Test
    fun mergingNewerPrependsOnlyUnknownNotes() {
        val existing = listOf(testNote("n3"), testNote("n2"))
        val merged = mergeNewer(existing, listOf(testNote("n5"), testNote("n4"), testNote("n3")))

        assertEquals(listOf("n5", "n4", "n3", "n2"), merged.map { it.id })
        assertSame(existing, mergeNewer(existing, listOf(testNote("n3"))))
    }

    @Test
    fun mergingOlderAppendsOnlyUnknownNotes() {
        val merged = mergeOlder(listOf(testNote("n3"), testNote("n2")), listOf(testNote("n2"), testNote("n1")))

        assertEquals(listOf("n3", "n2", "n1"), merged.map { it.id })
    }

    @Test
    fun mappingReachesRenotesAndReplies() {
        val target = testNote("t1", renoteCount = 0)
        val items = listOf(
            testNote("r1", text = null, renote = target),
            testNote("q1", renote = target),
            testNote("p1", reply = target),
            testNote("o1"),
        )
        val mapped = items.mapNote("t1") { it.copy(renoteCount = 9) }

        assertEquals(9, mapped[0].renote?.renoteCount)
        assertEquals(9, mapped[1].renote?.renoteCount)
        assertEquals(9, mapped[2].reply?.renoteCount)
        assertSame(items[3], mapped[3])
        assertSame(items, items.mapNote("missing") { it })
    }

    @Test
    fun removingANoteDropsItsPureRenotesAndStubsEmbeds() {
        val target = testNote("t1")
        val items = listOf(target, testNote("r1", text = null, renote = target), testNote("q1", renote = target), testNote("o1"))
        val remaining = items.removeNote("t1")

        assertEquals(listOf("q1", "o1"), remaining.map { it.id })
        assertTrue(remaining.first().renote!!.deleted)
        assertNull(remaining.first().renote!!.text)
    }

    @Test
    fun reactionKeysNormaliseBetweenApiAndMapForms() {
        assertEquals(":neko@.:", Reactions.keyFor(":neko:"))
        assertEquals(":neko@.:", Reactions.keyFor(":neko@.:"))
        assertEquals(":neko@remote.example:", Reactions.keyFor(":neko@remote.example:"))
        assertEquals("❤", Reactions.keyFor("❤️"))
        assertEquals("👨‍👩‍👧", Reactions.keyFor("👨‍👩‍👧"))

        assertEquals(":neko:", Reactions.toApi(":neko@.:", null))
        assertEquals(":neko:", Reactions.toApi(":neko@remote.example:", setOf("neko")))
        assertNull(Reactions.toApi(":other@remote.example:", setOf("neko")))
        assertEquals("❤", Reactions.toApi("❤️", null))
    }

    @Test
    fun customEmojiUrlsComeFromTheRightPlace() {
        val local = Reactions.emojiUrl(":neko@.:", emptyMap()) { "https://local/$it" }
        val remote = Reactions.emojiUrl(":neko@r.example:", mapOf("neko@r.example" to "https://r/neko")) { "unused" }

        assertEquals("https://local/neko", local)
        assertEquals("https://r/neko", remote)
        assertNull(Reactions.emojiUrl("❤", emptyMap()) { "unused" })
    }

    @Test
    fun applyingMyReactionMovesTheCount() {
        val note = testNote("n1", reactions = mapOf("❤" to 2, ":neko@.:" to 1), myReaction = ":neko@.:")

        val switched = note.withMyReaction("❤️")
        assertEquals(mapOf("❤" to 3), switched.reactions)
        assertEquals("❤", switched.myReaction)

        val removed = switched.withMyReaction(null)
        assertEquals(mapOf("❤" to 2), removed.reactions)
        assertNull(removed.myReaction)

        assertSame(note, note.withMyReaction(":neko:"))
    }

    @Test
    fun streamedReactionCountsAddRemoteEmojiUrls() {
        val note = testNote("n1").withReactionCount(":blob@r.example:", 1, "https://r/blob")

        assertEquals(mapOf(":blob@r.example:" to 1), note.reactions)
        assertEquals("https://r/blob", note.reactionEmojis["blob@r.example"])
        assertTrue(note.withReactionCount(":blob@r.example:", -1).reactions.isEmpty())
    }

    @Test
    fun renoteRulesFollowVisibility() {
        val mine = testUser("me")
        assertTrue(testNote("a").canRenote("me"))
        assertTrue(testNote("b", visibility = Visibility.Home).canRenote("me"))
        assertFalse(testNote("c", visibility = Visibility.Followers).canRenote("me"))
        assertTrue(testNote("d", visibility = Visibility.Followers, user = mine).canRenote("me"))
        assertFalse(testNote("e").canRenote(null))
    }

    @Test
    fun likeOnlyRules() {
        val remote = testUser("r", host = "r.example")
        assertTrue(testNote("a", reactionAcceptance = ReactionAcceptance.LikeOnly).acceptsOnlyLike())
        assertTrue(testNote("b", user = remote, reactionAcceptance = ReactionAcceptance.LikeOnlyForRemote).acceptsOnlyLike())
        assertFalse(testNote("c", reactionAcceptance = ReactionAcceptance.LikeOnlyForRemote).acceptsOnlyLike())
        assertFalse(testNote("d", reactionAcceptance = ReactionAcceptance.NonSensitiveOnly).acceptsSensitiveReactions())
    }

    @Test
    fun streamUpdatesAreParsed() {
        val reacted = parseNoteUpdate(
            "n1",
            "reacted",
            JSONObject().put("reaction", ":blob@r.example:").put("userId", "u2")
                .put("emoji", JSONObject().put("name", "blob@r.example").put("url", "https://r/blob")),
        )
        assertEquals(NoteUpdate.Reacted("n1", ":blob@r.example:", "https://r/blob", "u2"), reacted)
        assertEquals(NoteUpdate.Unreacted("n1", "❤", "u2"), parseNoteUpdate("n1", "unreacted", JSONObject().put("reaction", "❤").put("userId", "u2")))
        assertEquals(NoteUpdate.Deleted("n1"), parseNoteUpdate("n1", "deleted", JSONObject().put("deletedAt", "x")))
        assertEquals(NoteUpdate.PollVoted("n1", 1, "u2"), parseNoteUpdate("n1", "pollVoted", JSONObject().put("choice", 1).put("userId", "u2")))
        assertEquals(NoteUpdate.Updated("n1", "new", null, true), parseNoteUpdate("n1", "updated", JSONObject().put("text", "new")))
        assertEquals(NoteUpdate.Updated("n1", null, null, false), parseNoteUpdate("n1", "updated", null))
        assertNull(parseNoteUpdate("n1", "reacted", null))
        assertNull(parseNoteUpdate("n1", "mystery", null))
    }
}

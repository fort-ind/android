package com.fortind.zenith.timeline

import com.fortind.zenith.mfm.MfmBold
import com.fortind.zenith.mfm.MfmEmojiCode
import com.fortind.zenith.mfm.MfmText
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteParsingTest {

    private fun user(id: String = "u1", host: Any? = JSONObject.NULL) = JSONObject()
        .put("id", id)
        .put("username", "alice")
        .put("host", host)
        .put("name", "Alice :wave:")
        .put("avatarUrl", "https://x/a.webp")
        .put("avatarBlurhash", JSONObject.NULL)
        .put("emojis", JSONObject().put("wave", "https://remote/wave.png"))
        .put("isBot", true)

    private fun note(id: String = "n1") = JSONObject()
        .put("id", id)
        .put("createdAt", "2026-10-29T20:27:56.000Z")
        .put("user", user())
        .put("text", "**hi** :blob:")
        .put("cw", JSONObject.NULL)
        .put("visibility", "home")
        .put("reactions", JSONObject().put("❤", 2).put(":neocat@.:", 1))
        .put("reactionEmojis", JSONObject())
        .put("renoteCount", 3)
        .put("repliesCount", 4)

    @Test
    fun parsesTheCoreFields() {
        val parsed = parseNote(note())!!

        assertEquals("n1", parsed.id)
        assertEquals(Instant.parse("2026-10-29T20:27:56.000Z").toEpochMilli(), parsed.createdAtMillis)
        assertEquals(Visibility.Home, parsed.visibility)
        assertEquals(MfmBold(listOf(MfmText("hi"))), parsed.textMfm.first())
        assertEquals(MfmEmojiCode("blob"), parsed.textMfm.last())
        assertEquals(mapOf("❤" to 2, ":neocat@.:" to 1), parsed.reactions)
        assertEquals(3, parsed.reactionCount)
        assertEquals(3, parsed.renoteCount)
        assertEquals(4, parsed.repliesCount)
        assertNull(parsed.cw)
        assertNull(parsed.myReaction)
        assertEquals(ReactionAcceptance.All, parsed.reactionAcceptance)
        assertEquals("https://social.fort1nd.com/notes/n1", parsed.webUrl)
    }

    @Test
    fun parsesTheAuthor() {
        val author = parseNote(note())!!.user

        assertNull(author.host)
        assertEquals("@alice", author.handle)
        assertEquals("Alice :wave:", author.displayName)
        assertEquals(MfmEmojiCode("wave"), author.nameMfm.last())
        assertEquals("https://remote/wave.png", author.emojis["wave"])
        assertTrue(author.isBot)
        assertNull(author.avatarBlurhash)
    }

    @Test
    fun pureRenotesTargetTheInnerNote() {
        val json = note("r1").put("text", JSONObject.NULL).put("renote", note("n2"))
        val parsed = parseNote(json)!!

        assertTrue(parsed.isPureRenote)
        assertEquals("n2", parsed.target.id)
    }

    @Test
    fun quotesAreNotPureRenotes() {
        val parsed = parseNote(note("q1").put("renote", note("n2")))!!

        assertFalse(parsed.isPureRenote)
        assertEquals("q1", parsed.target.id)
    }

    @Test
    fun embeddedNotesStopAtTheDepthCap() {
        val deep = note("a").put("reply", note("b").put("reply", note("c").put("reply", note("d"))))
        val parsed = parseNote(deep)!!

        assertEquals("c", parsed.reply?.reply?.id)
        assertNull(parsed.reply?.reply?.reply)
    }

    @Test
    fun filesKeepAltTextSizeAndSensitivity() {
        val file = JSONObject()
            .put("id", "f1")
            .put("type", "image/gif")
            .put("url", "https://x/f.gif")
            .put("thumbnailUrl", JSONObject.NULL)
            .put("blurhash", "LEHV6nWB2yk8pyo0adR*.7kCMdnj")
            .put("isSensitive", true)
            .put("comment", "a cat")
            .put("properties", JSONObject().put("width", 400).put("height", 200))
        val parsed = parseNote(note().put("files", JSONArray().put(file)))!!.files.single()

        assertEquals(MediaKind.Image, parsed.kind)
        assertNull(parsed.thumbnailUrl)
        assertTrue(parsed.isSensitive)
        assertEquals("a cat", parsed.altText)
        assertEquals(2f, parsed.aspectRatio)
    }

    @Test
    fun pollsAreParsed() {
        val poll = JSONObject()
            .put("multiple", false)
            .put("expiresAt", JSONObject.NULL)
            .put(
                "choices",
                JSONArray()
                    .put(JSONObject().put("text", "yes").put("votes", 3).put("isVoted", true))
                    .put(JSONObject().put("text", "no").put("votes", 1).put("isVoted", false)),
            )
        val parsed = parseNote(note().put("poll", poll))!!.poll!!

        assertEquals(4, parsed.totalVotes)
        assertTrue(parsed.choices.first().isVoted)
        assertNull(parsed.expiresAtMillis)
    }

    @Test
    fun nullStringsAreNotTreatedAsText() {
        val parsed = parseNote(note().put("text", JSONObject.NULL).put("myReaction", JSONObject.NULL))!!

        assertNull(parsed.text)
        assertTrue(parsed.textMfm.isEmpty())
        assertNull(parsed.myReaction)
    }

    @Test
    fun remoteNotesKeepTheirOriginalUrlAndAcceptance() {
        val parsed = parseNote(
            note()
                .put("user", user(host = "remote.example"))
                .put("uri", "https://remote.example/notes/1")
                .put("reactionAcceptance", "likeOnly")
                .put("myReaction", ":neocat@.:"),
        )!!

        assertEquals("https://remote.example/notes/1", parsed.remoteUrl)
        assertEquals(ReactionAcceptance.LikeOnly, parsed.reactionAcceptance)
        assertEquals(":neocat@.:", parsed.myReaction)
        assertEquals("@alice@remote.example", parsed.user.handle)
    }

    @Test
    fun notesWithoutAnIdOrUserAreDropped() {
        assertNull(parseNote(note().put("id", JSONObject.NULL)))
        assertNull(parseNote(note().put("user", JSONObject.NULL)))
        assertEquals(1, parseNotes(JSONArray().put(note()).put(JSONObject())).size)
    }
}

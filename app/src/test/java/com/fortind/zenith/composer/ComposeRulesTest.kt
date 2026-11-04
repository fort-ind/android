package com.fortind.zenith.composer

import com.fortind.zenith.mfm.MfmBold
import com.fortind.zenith.mfm.MfmMention
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.mfm.MfmText
import com.fortind.zenith.timeline.Poll
import com.fortind.zenith.timeline.PollBody
import com.fortind.zenith.timeline.PollChoice
import com.fortind.zenith.timeline.ReactionAcceptance
import com.fortind.zenith.timeline.testNote
import com.fortind.zenith.timeline.testUser
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeRulesTest {

    private val now = 1_000_000L

    private fun poll(votes: Int = 0, expiresAt: Long? = null, multiple: Boolean = false) = Poll(
        listOf(PollChoice("a", votes, false), PollChoice("b", 0, false)),
        multiple,
        expiresAt,
    )

    @Test
    fun aPollNeedsTwoDifferentChoices() {
        assertEquals(PollProblem.TooFewChoices, PollDraft(listOf("a", "  ")).problem(now))
        assertEquals(PollProblem.DuplicateChoices, PollDraft(listOf("a", " a ")).problem(now))
        assertNull(PollDraft(listOf("a", "b", "")).problem(now))
    }

    @Test
    fun aChoiceCountsCodePointsUpToTheLimit() {
        val emoji = "😀"
        assertNull(PollDraft(listOf(emoji.repeat(150), "b")).problem(now))
        assertEquals(PollProblem.ChoiceTooLong, PollDraft(listOf("a".repeat(151), "b")).problem(now))
    }

    @Test
    fun aCustomEndMustBeInTheFuture() {
        assertEquals(PollProblem.EndInPast, PollDraft(listOf("a", "b"), endsAtMillis = now).problem(now))
        assertNull(PollDraft(listOf("a", "b"), endsAtMillis = now + 1).problem(now))
    }

    @Test
    fun presetsSendExpiredAfterAndCustomEndsSendExpiresAt() {
        val preset = PollBody(listOf("a", "b"), false, null, expiredAfterMillis = 60_000).toJson()
        assertEquals(60_000L, preset.getLong("expiredAfter"))
        assertFalse(preset.has("expiresAt"))

        val custom = PollDraft(listOf("a", " b "), multiple = true, endsAtMillis = 5).toBody()
        assertEquals(listOf("a", "b"), custom.choices)
        assertEquals(5L, custom.toJson().getLong("expiresAt"))
        assertFalse(custom.toJson().has("expiredAfter"))

        assertTrue(PollDraft(listOf("a", "b")).toBody().toJson().isNull("expiresAt"))
    }

    @Test
    fun anUnchangedPollKeepsItsExactEnd() {
        val original = poll(votes = 3, expiresAt = now + 123_456)
        val draft = original.toDraft(now)

        assertEquals(original.expiresAtMillis, draft.toBody().expiresAtMillis)
        assertFalse(pollChanged(original, draft))
        assertFalse(pollEditResetsVotes(original, draft))
    }

    @Test
    fun anyRealChangeToAVotedPollResetsItsVotes() {
        val original = poll(votes = 3)
        val draft = original.toDraft(now)

        assertTrue(pollEditResetsVotes(original, draft.copy(choices = listOf("a", "c"))))
        assertTrue(pollEditResetsVotes(original, draft.copy(multiple = true)))
        assertTrue(pollEditResetsVotes(original, draft.copy(endsAfterMillis = 1_000)))
        assertTrue(pollEditResetsVotes(original, null))
        assertFalse(pollEditResetsVotes(poll(votes = 0), null))
        assertFalse(pollEditResetsVotes(null, draft))
    }

    @Test
    fun aCustomEndUsesThePickedDayInTheLocalZone() {
        val zone = TimeZone.getTimeZone("America/Chicago")
        val pickedDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
            clear()
            set(2026, Calendar.NOVEMBER, 3)
            timeInMillis
        }

        val end = Calendar.getInstance(zone).apply { timeInMillis = customPollEnd(pickedDay, 18, 30, zone) }

        assertEquals(3, end.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, end.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, end.get(Calendar.MINUTE))
    }

    @Test
    fun mentionsAreFoundInsideFormatting() {
        val nodes = listOf(MfmText("x"), MfmBold(listOf(MfmMention("a", null))), MfmMention("b", "h"))

        assertEquals(listOf(MfmMention("a", null), MfmMention("b", "h")), mentionsIn(nodes))
    }

    @Test
    fun aReplyAddressesTheAuthorAndEveryoneElseMentioned() {
        val parent = testNote("p", user = testUser("u1", "alex", "remote.example"), text = "hi @sam and @me and @lee@other.example @alex@remote.example")
            .copy(textMfm = MfmParser.parse("hi @sam and @me@social.fort1nd.com and @lee@other.example @alex@remote.example"))

        assertEquals("@alex@remote.example @sam@remote.example @lee@other.example ", replyPrefix(parent, "me"))
    }

    @Test
    fun aMentionWithNoHostInALocalNoteStaysLocal() {
        val parent = testNote("p", user = testUser("u1", "alex")).copy(textMfm = MfmParser.parse("hi @sam"))

        assertEquals("@alex @sam ", replyPrefix(parent, "me"))
    }

    @Test
    fun replyingToYourselfOnlyAddsOthers() {
        val own = testNote("p", user = testUser("me", "me")).copy(textMfm = MfmParser.parse("thanks"))
        val withOther = testNote("p", user = testUser("me", "me")).copy(textMfm = MfmParser.parse("hi @sam"))

        assertEquals("", replyPrefix(own, "me"))
        assertEquals("@sam ", replyPrefix(withOther, "me"))
    }

    @Test
    fun newDraftFieldsSurviveEncodingAndOldDraftsStillLoad() {
        val draft = ComposerDraft(
            text = "t",
            poll = PollDraft(listOf("a", "b"), multiple = true, endsAfterMillis = 60_000),
            reactionAcceptance = ReactionAcceptance.LikeOnly,
            recipients = listOf(testUser("u9", "sam", "other.example")),
        )

        val decoded = decodeDrafts(encodeDrafts(mapOf("k" to StoredDraft(draft, 1L))))["k"]!!.draft

        assertEquals(draft.poll, decoded.poll)
        assertEquals(ReactionAcceptance.LikeOnly, decoded.reactionAcceptance)
        assertEquals("u9", decoded.recipients.single().id)
        assertEquals("other.example", decoded.recipients.single().host)

        val old = decodeDrafts("""{"k":{"text":"t","cw":null,"visibility":"public","localOnly":false,"attachments":[],"updatedAt":1}}""")["k"]!!.draft
        assertNull(old.poll)
        assertEquals(ReactionAcceptance.All, old.reactionAcceptance)
        assertTrue(old.recipients.isEmpty())
    }

    @Test
    fun aDraftOfDrivePicksRemembersTheyAreNotOurs() {
        val file = com.fortind.zenith.timeline.NoteFile("f", "n", "image/png", "u", null, null, false, null, null, null)
        val draft = ComposerDraft(attachments = listOf(DraftAttachment(file, "", false, owned = false)))

        val decoded = decodeDrafts(encodeDrafts(mapOf("k" to StoredDraft(draft, 1L))))["k"]!!.draft.attachments.single()

        assertFalse(decoded.owned)
        assertFalse(decoded.toAttachment().owned)
    }

    @Test
    fun aBlankPollIsNotADraftButAFilledOneIs() {
        assertTrue(ComposerDraft(poll = PollDraft()).isBlank)
        assertFalse(ComposerDraft(poll = PollDraft(listOf("a", ""))).isBlank)
    }
}

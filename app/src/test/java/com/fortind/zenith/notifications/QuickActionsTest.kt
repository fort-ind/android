package com.fortind.zenith.notifications

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.composer.ComposerDraft
import com.fortind.zenith.composer.DraftStore
import com.fortind.zenith.composer.draftStorageKey
import com.fortind.zenith.composer.failedReplySlot
import com.fortind.zenith.composer.quickReplyNote
import com.fortind.zenith.composer.replyText
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.timeline.FakeNotesApi
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.timeline.testNote
import com.fortind.zenith.timeline.testUser
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsTest {
    private class MemoryDrafts : DraftStore {
        val saved = HashMap<String, ComposerDraft>()

        override suspend fun load(key: String): ComposerDraft? = saved[key]

        override suspend fun save(key: String, draft: ComposerDraft) {
            saved[key] = draft
        }

        override suspend fun clear(key: String) {
            saved.remove(key)
        }

        override suspend fun adopt(userId: String) = Unit

        override suspend fun forget(userId: String) = Unit
    }

    private class Fixture(token: String? = "token") {
        val session = UserSessionService(FakeTokenStore(token), FakeUserApi())
        val notes = FakeNotesApi()
        val drafts = MemoryDrafts()
        val notifier = FakeSystemNotifier()
        val actions = NotificationQuickActions(session, notes, drafts, notifier) { "❤" }
    }

    private suspend fun fixture(token: String? = "token") = Fixture(token).also { it.session.restore() }

    private val me = CurrentUser("me", "me", null, null, null)
    private val author = testUser(id = "a", username = "ann")

    @Test
    fun aQuickReplyUsesTheInlineReplyDefaults() {
        val parent = testNote("n1", user = author, visibility = Visibility.Followers).copy(localOnly = true, cw = "spoilers")

        val note = quickReplyNote(parent, "hello", me)

        assertEquals("@ann hello", note.text)
        assertEquals("spoilers", note.cw)
        assertEquals(Visibility.Followers, note.visibility)
        assertTrue(note.localOnly)
        assertEquals("n1", note.replyId)
        assertTrue(note.visibleUserIds.isEmpty())
    }

    @Test
    fun aReplyToADirectNoteStaysWithTheSamePeople() {
        val parent = testNote("n1", user = author, visibility = Visibility.Specified).copy(visibleUserIds = listOf("me", "b"), localOnly = true)

        val note = quickReplyNote(parent, "hi", me)

        assertEquals(Visibility.Specified, note.visibility)
        assertEquals(listOf("b", "a"), note.visibleUserIds)
        assertFalse(note.localOnly)
    }

    @Test
    fun typingTheAuthorsHandleSkipsThePrefix() {
        val parent = testNote("n1", user = author)

        assertEquals("@ann hi", replyText(parent, "@ann hi", "me"))
        assertEquals("@ANN\nhi", replyText(parent, "@ANN\nhi", "me"))
        assertEquals("@ann @anna hi", replyText(parent, "@anna hi", "me"))
    }

    @Test
    fun aReplyLoadsTheParentThenPosts() = runTest {
        val f = fixture()
        f.notes.notes["n1"] = testNote("n1", user = author)

        f.actions.reply("n1", "hello")

        assertEquals("@ann hello", f.notes.createCalls.single().text)
        assertEquals("n1", f.notes.createCalls.single().replyId)
        assertTrue(f.notifier.failed.isEmpty())
        assertTrue(f.drafts.saved.isEmpty())
    }

    @Test
    fun aFailedPostKeepsTheTextForTheComposerOnce() = runTest {
        val f = fixture()
        f.notes.notes["n1"] = testNote("n1", user = author)
        f.notes.createFailure = IOException("offline")

        f.actions.reply("n1", "hello")

        assertEquals(1, f.notes.createCalls.size)
        assertEquals("hello", f.drafts.saved[draftStorageKey("me", failedReplySlot("n1"))]?.text)
        assertEquals(listOf(QuickAction.Reply to "n1"), f.notifier.failed.toList())
    }

    @Test
    fun aParentThatWontLoadStillKeepsTheText() = runTest {
        val f = fixture()

        f.actions.reply("gone", "hello")

        assertTrue(f.notes.createCalls.isEmpty())
        assertEquals("hello", f.drafts.saved.values.single().text)
        assertEquals(listOf(QuickAction.Reply to "gone"), f.notifier.failed.toList())
    }

    @Test
    fun aDeadTokenSignsOutAndDropsTheText() = runTest {
        val f = fixture()
        f.notes.notes["n1"] = testNote("n1", user = author)
        f.notes.createFailure = TokenRejectedException(401)

        f.actions.reply("n1", "hello")

        assertEquals(SessionState.SignedOut, f.session.state.first())
        assertTrue(f.drafts.saved.isEmpty())
        assertEquals(listOf(QuickAction.Reply to "n1"), f.notifier.failed.toList())
    }

    @Test
    fun signedOutReportsWithoutKeepingText() = runTest {
        val f = fixture(token = null)

        f.actions.reply("n1", "hello")

        assertTrue(f.drafts.saved.isEmpty())
        assertEquals(1, f.notifier.failed.size)
    }

    @Test
    fun aReplyTooLongToQueueGoesStraightBackToTheComposer() = runTest {
        val f = fixture()

        f.actions.replyTooLong("n1", "x".repeat(4_000))

        assertTrue(f.notes.createCalls.isEmpty())
        assertEquals(4_000, f.drafts.saved.values.single().text.length)
        assertEquals(1, f.notifier.failed.size)
    }

    @Test
    fun aLikeSendsTheInstancesReaction() = runTest {
        val f = fixture()

        assertEquals(QuickOutcome.Done, f.actions.like("n1"))

        assertEquals(listOf("n1" to "❤"), f.notes.reactCalls.toList())
    }

    @Test
    fun alreadyReactedCountsAsDone() = runTest {
        val f = fixture()
        f.notes.reactFailure = HttpStatusException(400, ApiErrorCodes.ALREADY_REACTED)

        assertEquals(QuickOutcome.Done, f.actions.like("n1"))
        assertTrue(f.notifier.failed.isEmpty())
    }

    @Test
    fun aRefusedLikeIsReportedAndNotRetried() = runTest {
        val f = fixture()
        f.notes.reactFailure = HttpStatusException(404, ApiErrorCodes.NO_SUCH_NOTE)

        assertEquals(QuickOutcome.Done, f.actions.like("n1"))
        assertEquals(listOf(QuickAction.Like to "n1"), f.notifier.failed.toList())
    }

    @Test
    fun aLikeRetriesOnNetworkAndServerErrors() = runTest {
        val f = fixture()

        f.notes.reactFailure = IOException("offline")
        assertEquals(QuickOutcome.Retry, f.actions.like("n1"))
        f.notes.reactFailure = HttpStatusException(500, null)
        assertEquals(QuickOutcome.Retry, f.actions.like("n1"))
        assertTrue(f.notifier.failed.isEmpty())
    }

    @Test
    fun yourReactionIsReadFromTheNotificationsNote() {
        val mine = parseNotification(JSONObject("""{"id":"1","type":"mention","createdAt":"2026-11-03T00:00:00.000Z","note":{"id":"n1","myReaction":"❤"}}"""))
        val none = parseNotification(JSONObject("""{"id":"2","type":"mention","createdAt":"2026-11-03T00:00:00.000Z","note":{"id":"n2","myReaction":null}}"""))

        assertTrue(mine?.hasMyReaction == true)
        assertFalse(none?.hasMyReaction == true)
        assertNull(parseNotification(JSONObject("""{"type":"mention"}""")))
    }
}

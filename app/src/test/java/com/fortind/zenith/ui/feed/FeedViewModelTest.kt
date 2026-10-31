package com.fortind.zenith.ui.feed

import androidx.lifecycle.viewModelScope
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.emoji.EmojiIndex
import com.fortind.zenith.emoji.InstanceInfo
import com.fortind.zenith.emoji.InstanceMeta
import com.fortind.zenith.emoji.RecentReactionsStore
import com.fortind.zenith.emoji.UnicodeEmoji
import com.fortind.zenith.emoji.UnicodeEmojiGroup
import com.fortind.zenith.emoji.UnicodeEmojiSource
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import com.fortind.zenith.timeline.Bookmark
import com.fortind.zenith.timeline.FakeNotesApi
import com.fortind.zenith.timeline.FakeTimelineStream
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUpdate
import com.fortind.zenith.timeline.Poll
import com.fortind.zenith.timeline.PollChoice
import com.fortind.zenith.timeline.TimelineKind
import com.fortind.zenith.timeline.TimelineStreamEvent
import com.fortind.zenith.timeline.TimelineUnavailableException
import com.fortind.zenith.timeline.hasVoted
import com.fortind.zenith.timeline.testNote
import com.fortind.zenith.timeline.testUser
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    private val fixtures = mutableListOf<Fixture>()

    @After
    fun tearDown() {
        runBlocking {
            fixtures.filter { it.viewModelCreated.isInitialized() }.forEach {
                it.viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
        mainDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private class FakeInstance : InstanceInfo {
        override val emojis = MutableStateFlow<EmojiIndex?>(EmojiIndex(emptyList()))
        override val meta: StateFlow<InstanceMeta?> = MutableStateFlow(InstanceMeta("❤️", ltlAvailable = true, gtlAvailable = true))

        override fun ensureFresh() = Unit

        override fun emojiUrl(name: String) = "https://x/$name"
    }

    private class FakeRecents : RecentReactionsStore {
        val recorded = mutableListOf<String>()
        override val recent: Flow<List<String>> = MutableStateFlow(emptyList())

        override suspend fun record(reaction: String) {
            recorded += reaction
        }
    }

    private class FakeUnicode : UnicodeEmojiSource {
        override suspend fun groups() = listOf(UnicodeEmojiGroup("Smileys", listOf(UnicodeEmoji("😀", "grinning face"))))
    }

    private class Fixture(initialToken: String?, debounceMs: Long) {
        val session = UserSessionService(FakeTokenStore(initialToken), FakeUserApi())
        val api = FakeNotesApi()
        val stream = FakeTimelineStream()
        val recents = FakeRecents()
        val viewModelCreated = lazy { FeedViewModel(session, api, stream, FakeInstance(), recents, FakeUnicode(), debounceMs) }
        val viewModel by viewModelCreated
    }

    private suspend fun fixture(token: String? = "token", debounceMs: Long = 0): Fixture {
        val f = Fixture(token, debounceMs).also(fixtures::add)
        if (token == null) f.session.browseAsGuest() else f.session.restore()
        return f
    }

    private suspend fun FeedViewModel.awaitTimeline(
        kind: TimelineKind,
        predicate: (TimelineState) -> Boolean = { it.status == TimelineStatus.Ready },
    ): TimelineState = uiState.first { predicate(it.timeline(kind)) }.timeline(kind)

    private suspend fun Fixture.startStreaming() {
        viewModel.setActive(true)
        stream.events.subscriptionCount.first { it > 0 }
    }

    private fun page(vararg ids: Int) = ids.map { testNote("n$it") }

    private fun TestScope.collectEvents(viewModel: FeedViewModel): MutableList<FeedEvent> {
        val events = mutableListOf<FeedEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return events
    }

    @Test
    fun guestsStartOnLocalAndHomeAsksThemToSignIn() = runTest {
        val f = fixture(token = null)
        f.api.timelines[TimelineKind.Local] = page(3, 2, 1)

        val local = f.viewModel.awaitTimeline(TimelineKind.Local)
        val state = f.viewModel.uiState.value

        assertEquals(TimelineKind.Local, state.selected)
        assertFalse(state.signedIn)
        assertEquals(TimelineStatus.SignInRequired, state.timeline(TimelineKind.Home).status)
        assertEquals(listOf("n3", "n2", "n1"), local.items.map { it.id })
        assertNull(f.api.calls.single().token)
    }

    @Test
    fun signedInUsersStartOnHome() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(2, 1)

        val home = f.viewModel.awaitTimeline(TimelineKind.Home)

        assertEquals(TimelineKind.Home, f.viewModel.uiState.value.selected)
        assertEquals("me", f.viewModel.uiState.value.myUserId)
        assertEquals(listOf("n2", "n1"), home.items.map { it.id })
        assertEquals("token", f.api.calls.single().token)
    }

    @Test
    fun eachTimelineKeepsItsOwnItems() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(2, 1)
        f.api.timelines[TimelineKind.Global] = page(9, 8)
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.select(TimelineKind.Global)
        f.viewModel.awaitTimeline(TimelineKind.Global)
        f.viewModel.select(TimelineKind.Home)

        val state = f.viewModel.uiState.value
        assertEquals(listOf("n2", "n1"), state.timeline(TimelineKind.Home).items.map { it.id })
        assertEquals(listOf("n9", "n8"), state.timeline(TimelineKind.Global).items.map { it.id })
        assertEquals(2, f.api.calls.size)
    }

    @Test
    fun loadingMoreUsesUntilIdAndOnlyAnEmptyPageEndsTheList() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = (60 downTo 1).map { testNote("n$it") }
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.loadMore()
        val second = f.viewModel.awaitTimeline(TimelineKind.Home) { it.items.size == 60 && !it.loadingMore }
        assertEquals("n31", f.api.calls.last().untilId)
        assertFalse(second.endReached)

        f.viewModel.loadMore()
        val end = f.viewModel.awaitTimeline(TimelineKind.Home) { it.endReached }
        assertEquals(60, end.items.size)
    }

    @Test
    fun postsArriveAtTheTopOrWaitBehindThePill() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(2, 1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.startStreaming()

        f.stream.events.emit(TimelineStreamEvent.Posted(testNote("n3")))
        assertEquals("n3", f.viewModel.uiState.value.current.items.first().id)

        f.viewModel.setAtTop(TimelineKind.Home, false)
        f.stream.events.emit(TimelineStreamEvent.Posted(testNote("n4")))
        val queued = f.viewModel.uiState.value.current
        assertEquals("n3", queued.items.first().id)
        assertEquals(listOf("n4"), queued.pending.map { it.id })

        val events = collectEvents(f.viewModel)
        f.viewModel.showPending()
        val shown = f.viewModel.uiState.value.current
        assertEquals(listOf("n4", "n3", "n2", "n1"), shown.items.map { it.id })
        assertTrue(shown.pending.isEmpty())
        assertEquals(FeedEvent.ScrollToTop(TimelineKind.Home), events.last())
    }

    @Test
    fun scrollingBackToTheTopReleasesQueuedPosts() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.startStreaming()
        f.viewModel.setAtTop(TimelineKind.Home, false)
        f.stream.events.emit(TimelineStreamEvent.Posted(testNote("n2")))

        f.viewModel.setAtTop(TimelineKind.Home, true)

        assertEquals(listOf("n2", "n1"), f.viewModel.uiState.value.current.items.map { it.id })
    }

    @Test
    fun reconnectingCatchesUpWithSinceId() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(2, 1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.startStreaming()
        f.api.timelines[TimelineKind.Home] = page(4, 3, 2, 1)

        f.stream.events.emit(TimelineStreamEvent.Connected)

        val caughtUp = f.viewModel.awaitTimeline(TimelineKind.Home) { it.items.size == 4 }
        assertEquals("n2", f.api.calls.last().sinceId)
        assertEquals(listOf("n4", "n3", "n2", "n1"), caughtUp.items.map { it.id })
    }

    @Test
    fun disabledTimelinesAreMarkedUnavailable() = runTest {
        val f = fixture(token = null)
        f.api.failures[TimelineKind.Local] = TimelineUnavailableException(TimelineKind.Local)

        f.viewModel.awaitTimeline(TimelineKind.Local) { it.status == TimelineStatus.Unavailable }
    }

    @Test
    fun streamUpdatesAdjustCountsButSkipMyOwnReactions() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.setActive(true)
        f.stream.updates.subscriptionCount.first { it > 0 }

        f.stream.updates.emit(NoteUpdate.Reacted("n1", "❤", null, "someone"))
        f.stream.updates.emit(NoteUpdate.Reacted("n1", "❤", null, "me"))

        assertEquals(mapOf("❤" to 1), f.viewModel.uiState.value.current.items.single().reactions)

        f.stream.updates.emit(NoteUpdate.Deleted("n1"))
        assertTrue(f.viewModel.uiState.value.current.items.isEmpty())
    }

    @Test
    fun capturedNotesCombineVisibleNotesAndTheOpenThread() = runTest {
        val f = fixture()
        val target = testNote("t9")
        f.api.timelines[TimelineKind.Home] = listOf(testNote("r2", text = null, renote = target), testNote("n1"))
        f.api.notes["n5"] = testNote("n5")
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.setActive(true)

        f.viewModel.setVisibleNotes(TimelineKind.Home, setOf("r2"))
        f.viewModel.openThread("n5")

        f.stream.captured.first { it == setOf("t9", "n5") }
    }

    @Test
    fun reactingUpdatesOptimisticallyAndRecordsRecents() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1", reactions = mapOf("❤" to 1)))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.react("n1", ":neko:")

        val note = f.viewModel.uiState.value.current.items.single()
        assertEquals(":neko@.:", note.myReaction)
        assertEquals(mapOf("❤" to 1, ":neko@.:" to 1), note.reactions)
        assertEquals(listOf("n1" to ":neko:"), f.api.reactCalls)
        assertEquals(listOf(":neko:"), f.recents.recorded)
    }

    @Test
    fun switchingAReactionDeletesThenCreates() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1", reactions = mapOf("❤" to 1), myReaction = "❤"))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.toggleReaction("n1", "👍")

        assertEquals(listOf("n1"), f.api.unreactCalls)
        assertEquals(listOf("n1" to "👍"), f.api.reactCalls)
        assertEquals(mapOf("👍" to 1), f.viewModel.uiState.value.current.items.single().reactions)
    }

    @Test
    fun failedReactionsRollBack() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1"))
        f.api.reactFailure = IOException("offline")
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.toggleReaction("n1", "👍")

        val note = f.viewModel.uiState.value.current.items.single()
        assertNull(note.myReaction)
        assertTrue(note.reactions.isEmpty())
        assertEquals(listOf<FeedEvent>(FeedEvent.ReactionFailed), events)
    }

    @Test
    fun alreadyReactedIsRetriedOnce() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1"))
        f.api.reactFailure = HttpStatusException(400, "ALREADY_REACTED")
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.toggleReaction("n1", "👍")

        assertEquals(2, f.api.reactCalls.size)
        assertEquals(listOf("n1"), f.api.unreactCalls)
        assertEquals("👍", f.viewModel.uiState.value.current.items.single().myReaction)
    }

    @Test
    fun rateLimitsAreReported() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1"))
        f.api.reactFailure = HttpStatusException(429)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.toggleReaction("n1", "👍")

        assertEquals(listOf<FeedEvent>(FeedEvent.RateLimited), events)
    }

    @Test
    fun rapidTogglesThatCancelOutNeverHitTheServer() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val f = fixture(debounceMs = 600)
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1"))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.toggleReaction("n1", "👍")
        advanceTimeBy(200)
        f.viewModel.toggleReaction("n1", "👍")
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(f.api.reactCalls.isEmpty())
        assertTrue(f.api.unreactCalls.isEmpty())
        assertNull(f.viewModel.uiState.value.current.items.single().myReaction)
    }

    @Test
    fun guestsAreAskedToSignInBeforeReacting() = runTest {
        val f = fixture(token = null)
        f.api.timelines[TimelineKind.Local] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Local)
        val events = collectEvents(f.viewModel)

        f.viewModel.toggleReaction("n1", "👍")
        f.viewModel.renote("n1")

        assertEquals(listOf<FeedEvent>(FeedEvent.SignInRequired, FeedEvent.SignInRequired), events)
        assertTrue(f.api.reactCalls.isEmpty())
    }

    @Test
    fun renotingAndUnrenotingTrackCountsAndMyRenotes() = runTest {
        val f = fixture()
        val target = testNote("t1", renoteCount = 2)
        val mine = testNote("r5", user = testUser("me"), text = null, renote = target)
        f.api.timelines[TimelineKind.Home] = listOf(mine, testNote("n2"), testNote("n1"))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.renote("n2")
        assertEquals(1, f.viewModel.uiState.value.current.items[1].renoteCount)
        assertTrue("n2" in f.viewModel.uiState.value.renoted)
        assertEquals(listOf("n2"), f.api.renoteCalls)

        f.api.renotedByMe = true
        f.viewModel.checkRenoted("t1")
        assertTrue("t1" in f.viewModel.uiState.value.renoted)

        f.viewModel.unrenote("t1")
        assertEquals(listOf("n2", "n1"), f.viewModel.uiState.value.current.items.map { it.id })
        assertEquals(listOf("t1"), f.api.unrenoteCalls)
    }

    private fun pollNote(id: String = "p1", multiple: Boolean = false, votes: List<Int> = listOf(2, 0, 1), voted: Set<Int> = emptySet()) = testNote(
        id,
        poll = Poll(
            choices = votes.mapIndexed { index, count -> PollChoice("c$index", count, index in voted) },
            multiple = multiple,
            expiresAtMillis = null,
        ),
    )

    private suspend fun Fixture.withPoll(note: Note = pollNote()): Fixture {
        api.timelines[TimelineKind.Home] = listOf(note)
        viewModel.awaitTimeline(TimelineKind.Home)
        return this
    }

    private val Fixture.poll: Poll get() = viewModel.uiState.value.current.items.single().poll!!

    @Test
    fun votingOnAPollShowsMyVoteAtOnceAndSendsIt() = runTest {
        val f = fixture().withPoll()

        f.viewModel.votePoll("p1", listOf(1))

        assertEquals(listOf(2, 1, 1), f.poll.choices.map { it.votes })
        assertEquals(listOf(false, true, false), f.poll.choices.map { it.isVoted })
        assertEquals(listOf("p1" to 1), f.api.voteCalls)
    }

    @Test
    fun aMultipleChoiceVoteSendsEveryPickedChoice() = runTest {
        val f = fixture().withPoll(pollNote(multiple = true))

        f.viewModel.votePoll("p1", listOf(0, 2))

        assertEquals(listOf("p1" to 0, "p1" to 2), f.api.voteCalls)
        assertEquals(listOf(true, false, true), f.poll.choices.map { it.isVoted })
    }

    @Test
    fun aVoteThatFailsHalfwayKeepsWhatTheServerAccepted() = runTest {
        val f = fixture().withPoll(pollNote(multiple = true))
        f.api.voteFailures[2] = HttpStatusException(500)
        val events = collectEvents(f.viewModel)

        f.viewModel.votePoll("p1", listOf(0, 2))

        assertEquals(listOf(3, 0, 1), f.poll.choices.map { it.votes })
        assertEquals(listOf(true, false, false), f.poll.choices.map { it.isVoted })
        assertEquals(listOf<FeedEvent>(FeedEvent.PollVoteFailed), events)
    }

    @Test
    fun aRateLimitedVoteIsTakenBackAndReported() = runTest {
        val f = fixture().withPoll()
        f.api.voteFailures[1] = HttpStatusException(429)
        val events = collectEvents(f.viewModel)

        f.viewModel.votePoll("p1", listOf(1))

        assertEquals(pollNote().poll, f.poll)
        assertEquals(listOf<FeedEvent>(FeedEvent.RateLimited), events)
    }

    @Test
    fun aPollThatAlreadyEndedIsRefreshedAndReported() = runTest {
        val f = fixture().withPoll()
        f.api.notes["p1"] = pollNote(votes = listOf(5, 0, 1))
        f.api.voteFailures[0] = HttpStatusException(400, "ALREADY_EXPIRED")
        val events = collectEvents(f.viewModel)

        f.viewModel.votePoll("p1", listOf(0))

        assertEquals(listOf(5, 0, 1), f.poll.choices.map { it.votes })
        assertFalse(f.poll.hasVoted)
        assertEquals(listOf<FeedEvent>(FeedEvent.PollEnded), events)
    }

    @Test
    fun aVoteAlreadyCastElsewhereIsPulledInQuietly() = runTest {
        val f = fixture().withPoll()
        f.api.notes["p1"] = pollNote(votes = listOf(2, 0, 2), voted = setOf(2))
        f.api.voteFailures[0] = HttpStatusException(400, "ALREADY_VOTED")
        val events = collectEvents(f.viewModel)

        f.viewModel.votePoll("p1", listOf(0))

        assertEquals(listOf(false, false, true), f.poll.choices.map { it.isVoted })
        assertTrue(events.isEmpty())
    }

    @Test
    fun aPollIsVotedOnOnlyOnce() = runTest {
        val f = fixture().withPoll()

        f.viewModel.votePoll("p1", listOf(0))
        f.viewModel.votePoll("p1", listOf(1))

        assertEquals(listOf("p1" to 0), f.api.voteCalls)
    }

    @Test
    fun singleChoicePollsRefuseSeveralPicks() = runTest {
        val f = fixture().withPoll()

        f.viewModel.votePoll("p1", listOf(0, 1))
        f.viewModel.votePoll("p1", listOf(7))

        assertTrue(f.api.voteCalls.isEmpty())
    }

    @Test
    fun guestsAreAskedToSignInBeforeVoting() = runTest {
        val f = fixture(token = null)
        f.api.timelines[TimelineKind.Local] = listOf(pollNote())
        f.viewModel.awaitTimeline(TimelineKind.Local)
        val events = collectEvents(f.viewModel)

        f.viewModel.votePoll("p1", listOf(0))

        assertEquals(listOf<FeedEvent>(FeedEvent.SignInRequired), events)
        assertTrue(f.api.voteCalls.isEmpty())
    }

    @Test
    fun aTokenWithoutVotePermissionAsksToReauthorizeAndRestoresThePoll() = runTest {
        val f = fixture().withPoll()
        f.api.voteFailures[0] = MissingPermissionException()

        f.viewModel.votePoll("p1", listOf(0))

        assertTrue(f.viewModel.uiState.value.needsPermission)
        assertEquals(pollNote().poll, f.poll)
    }

    @Test
    fun checkingABookmarkRemembersWhichPostsAreBookmarked() = runTest {
        val f = fixture().withPoll(testNote("n1"))
        f.api.bookmarkedIds += "n1"

        f.viewModel.checkBookmarked("n1")

        assertTrue("n1" in f.viewModel.uiState.value.bookmarked)
        assertTrue(f.viewModel.uiState.value.bookmarkChecks.isEmpty())
    }

    @Test
    fun bookmarkingAndUnbookmarkingUpdateStateServerAndEvents() = runTest {
        val f = fixture().withPoll(testNote("n1"))
        val events = collectEvents(f.viewModel)

        f.viewModel.toggleBookmark("n1")
        assertTrue("n1" in f.viewModel.uiState.value.bookmarked)
        f.viewModel.toggleBookmark("n1")

        assertFalse("n1" in f.viewModel.uiState.value.bookmarked)
        assertEquals(listOf("n1"), f.api.bookmarkCalls)
        assertEquals(listOf("n1"), f.api.unbookmarkCalls)
        assertEquals(listOf<FeedEvent>(FeedEvent.Bookmarked, FeedEvent.BookmarkRemoved), events)
    }

    @Test
    fun aPostThatWasAlreadyBookmarkedElsewhereStillCountsAsBookmarked() = runTest {
        val f = fixture().withPoll(testNote("n1"))
        f.api.bookmarkFailure = HttpStatusException(400, "ALREADY_FAVORITED")
        val events = collectEvents(f.viewModel)

        f.viewModel.toggleBookmark("n1")

        assertTrue("n1" in f.viewModel.uiState.value.bookmarked)
        assertEquals(listOf<FeedEvent>(FeedEvent.Bookmarked), events)
    }

    @Test
    fun aFailedBookmarkIsTakenBackAndReported() = runTest {
        val f = fixture().withPoll(testNote("n1"))
        f.api.bookmarkFailure = HttpStatusException(500)
        val events = collectEvents(f.viewModel)

        f.viewModel.toggleBookmark("n1")

        assertFalse("n1" in f.viewModel.uiState.value.bookmarked)
        assertEquals(listOf<FeedEvent>(FeedEvent.BookmarkFailed), events)
    }

    @Test
    fun guestsAreAskedToSignInBeforeBookmarking() = runTest {
        val f = fixture(token = null)
        f.api.timelines[TimelineKind.Local] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Local)
        val events = collectEvents(f.viewModel)

        f.viewModel.toggleBookmark("n1")

        assertEquals(listOf<FeedEvent>(FeedEvent.SignInRequired), events)
        assertTrue(f.api.bookmarkCalls.isEmpty())
    }

    private fun bookmarkEntries(count: Int) = (1..count).map { Bookmark("f$it", testNote("b$it")) }

    @Test
    fun theBookmarksListPagesWithTheEntryCursorUntilAnEmptyPage() = runTest {
        val f = fixture().withPoll(testNote("n1"))
        f.api.bookmarkEntries = bookmarkEntries(35)

        f.viewModel.loadBookmarks()
        assertEquals(30, f.viewModel.uiState.value.bookmarks.items.size)
        f.viewModel.loadMoreBookmarks()
        assertEquals(35, f.viewModel.uiState.value.bookmarks.items.size)
        f.viewModel.loadMoreBookmarks()

        assertEquals(listOf(null, "f30", "f35"), f.api.bookmarkPageCalls)
        assertTrue(f.viewModel.uiState.value.bookmarks.endReached)
        assertTrue(f.viewModel.uiState.value.bookmarked.containsAll(listOf("b1", "b35")))
    }

    @Test
    fun aFailedBookmarksLoadIsMarkedFailedAndCanBeRetried() = runTest {
        val f = fixture().withPoll(testNote("n1"))
        f.api.bookmarkEntries = bookmarkEntries(2)
        f.api.bookmarkPageFailure = IOException("offline")

        f.viewModel.loadBookmarks()
        assertEquals(TimelineStatus.Failed, f.viewModel.uiState.value.bookmarks.status)
        f.api.bookmarkPageFailure = null
        f.viewModel.loadBookmarks()

        assertEquals(TimelineStatus.Ready, f.viewModel.uiState.value.bookmarks.status)
        assertEquals(2, f.viewModel.uiState.value.bookmarks.items.size)
    }

    @Test
    fun bookmarkingFromTheFeedAddsToALoadedBookmarksListAndUnbookmarkingRemovesIt() = runTest {
        val f = fixture().withPoll(testNote("n1"))
        f.api.bookmarkEntries = bookmarkEntries(1)
        f.viewModel.loadBookmarks()

        f.viewModel.toggleBookmark("n1")
        assertEquals(listOf("n1", "b1"), f.viewModel.uiState.value.bookmarks.items.map { it.id })
        f.viewModel.toggleBookmark("n1")

        assertEquals(listOf("b1"), f.viewModel.uiState.value.bookmarks.items.map { it.id })
    }

    @Test
    fun reactionsAndVotesAlsoUpdatePostsShownInTheBookmarksList() = runTest {
        val f = fixture().withPoll(testNote("n1"))
        f.api.bookmarkEntries = listOf(Bookmark("f1", pollNote("p9")))
        f.viewModel.loadBookmarks()

        f.viewModel.votePoll("p9", listOf(1))

        val poll = f.viewModel.uiState.value.bookmarks.items.single().poll!!
        assertEquals(listOf(false, true, false), poll.choices.map { it.isVoted })
    }

    @Test
    fun deletingMyPostRemovesItEverywhereAndSaysSo() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n2", user = testUser("me")), testNote("n1"))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.deleteNote("n2")

        assertEquals(listOf("n2"), f.api.deleteCalls)
        assertEquals(listOf("n1"), f.viewModel.uiState.value.current.items.map { it.id })
        assertEquals(listOf<FeedEvent>(FeedEvent.NoteDeleted), events)
    }

    @Test
    fun otherPeoplesPostsAreNeverDeleted() = runTest {
        val f = fixture().withPoll(testNote("n1"))

        f.viewModel.deleteNote("n1")
        f.viewModel.redraftNote("n1")

        assertTrue(f.api.deleteCalls.isEmpty())
        assertEquals(1, f.viewModel.uiState.value.current.items.size)
    }

    @Test
    fun redraftingDeletesThePostThenHandsItBackForTheComposer() = runTest {
        val f = fixture()
        val mine = testNote("n2", user = testUser("me"), text = "oops")
        f.api.timelines[TimelineKind.Home] = listOf(mine, testNote("n1"))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.redraftNote("n2")

        assertEquals(listOf("n2"), f.api.deleteCalls)
        assertEquals(listOf<FeedEvent>(FeedEvent.Redraft(mine)), events)
        assertEquals(listOf("n1"), f.viewModel.uiState.value.current.items.map { it.id })
    }

    @Test
    fun aFailedDeleteKeepsThePostAndNeverOpensTheComposer() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n2", user = testUser("me")))
        f.api.deleteFailure = HttpStatusException(500)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.redraftNote("n2")

        assertEquals(1, f.viewModel.uiState.value.current.items.size)
        assertEquals(listOf<FeedEvent>(FeedEvent.DeleteFailed), events)
    }

    @Test
    fun deletingAPostThatIsAlreadyGoneStillSucceeds() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n2", user = testUser("me")))
        f.api.deleteFailure = HttpStatusException(400, "NO_SUCH_NOTE")
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.deleteNote("n2")

        assertTrue(f.viewModel.uiState.value.current.items.isEmpty())
        assertEquals(listOf<FeedEvent>(FeedEvent.NoteDeleted), events)
    }

    @Test
    fun missingPermissionsShowABannerAndKeepTheFeed() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        f.api.reactFailure = MissingPermissionException()
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.toggleReaction("n1", "👍")

        val state = f.viewModel.uiState.value
        assertTrue(state.needsPermission)
        assertEquals(1, state.current.items.size)
    }

    @Test
    fun rejectedTokensSignOut() = runTest {
        val f = fixture()
        f.api.failures[TimelineKind.Home] = TokenRejectedException(401)

        f.viewModel.uiState.first { it.authResolved }
        f.session.state.first { it == SessionState.SignedOut }
    }

    @Test
    fun threadsLoadAncestorsAndPageReplies() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(5)
        f.api.notes["n5"] = testNote("n5", reply = testNote("n4"))
        f.api.ancestors = listOf(testNote("n3"), testNote("n4"))
        f.api.replies = (40 downTo 6).map { testNote("n$it") }
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.openThread("n5")
        val thread = f.viewModel.uiState.first { it.thread?.loading == false }.thread!!
        assertEquals(listOf("n3", "n4"), thread.ancestors.map { it.id })
        assertEquals(30, thread.replies.size)
        assertFalse(thread.repliesEnd)

        f.viewModel.loadMoreReplies()
        val more = f.viewModel.uiState.first { it.thread?.repliesEnd == true }.thread!!
        assertEquals(35, more.replies.size)

        f.viewModel.closeThread()
        assertNull(f.viewModel.uiState.value.thread)
    }

    @Test
    fun aNewPostLandsAtTheTopOfHomeAndScrollsThere() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(2, 1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.noteCreated(testNote("n9", user = testUser("me")))

        assertEquals(listOf("n9", "n2", "n1"), f.viewModel.uiState.value.timeline(TimelineKind.Home).items.map { it.id })
        assertEquals(FeedEvent.ScrollToTop(TimelineKind.Home), events.last())
    }

    @Test
    fun aPostAlreadyDeliveredByTheStreamIsNotDuplicated() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val created = testNote("n9", user = testUser("me"))

        f.viewModel.noteCreated(created)
        f.viewModel.noteCreated(created)

        assertEquals(listOf("n9", "n1"), f.viewModel.uiState.value.timeline(TimelineKind.Home).items.map { it.id })
    }

    @Test
    fun aReplyBumpsTheParentCountAndJoinsTheOpenThread() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(5)
        f.api.notes["n5"] = testNote("n5")
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.openThread("n5")
        f.viewModel.uiState.first { it.thread?.loading == false }

        f.viewModel.noteCreated(testNote("n9", user = testUser("me"), reply = testNote("n5")))

        val state = f.viewModel.uiState.value
        val thread = state.thread!!
        assertEquals(1, state.current.items.single().repliesCount)
        assertEquals(listOf("n9"), thread.replies.map { it.id })
        assertEquals(1, thread.note!!.repliesCount)
        assertEquals(listOf("n5"), state.current.items.map { it.id })
    }

    @Test
    fun aQuoteBumpsTheQuotedNotesRenoteCount() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(5)
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.noteCreated(testNote("n9", user = testUser("me"), renote = testNote("n5")))

        val items = f.viewModel.uiState.value.timeline(TimelineKind.Home).items
        assertEquals(listOf("n9", "n5"), items.map { it.id })
        assertEquals(1, items.last().renoteCount)
    }

    @Test
    fun guestsNeverGetPostsInserted() = runTest {
        val f = fixture(token = null)
        f.api.timelines[TimelineKind.Local] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Local)

        f.viewModel.noteCreated(testNote("n9"))

        assertEquals(listOf("n1"), f.viewModel.uiState.value.timeline(TimelineKind.Local).items.map { it.id })
    }

    @Test
    fun anEditedNoteUpdatesEverywhereItAppears() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.noteEdited(testNote("n1", text = "fixed typo"))

        val edited = f.viewModel.uiState.value.current.items.single()
        assertEquals("fixed typo", edited.text)
        assertTrue(edited.editedAtMillis != null)
    }

    @Test
    fun askingForPermissionRaisesTheReauthorizeBanner() = runTest {
        val f = fixture()
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.requestPermission()

        assertTrue(f.viewModel.uiState.value.needsPermission)
    }
}

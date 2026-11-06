package com.fortind.zenith.ui.feed

import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.timeline.NoteReactionRow
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.timeline.renoteVisibilities
import com.fortind.zenith.timeline.CollectionKind
import com.fortind.zenith.timeline.NoteCollection
import com.fortind.zenith.timeline.NoteSearch
import com.fortind.zenith.timeline.SearchMedia
import androidx.lifecycle.viewModelScope
import com.fortind.zenith.account.FakeModerationApi
import com.fortind.zenith.account.AccountSettings
import com.fortind.zenith.account.MuteWordSet
import com.fortind.zenith.account.MuteWordsParse
import com.fortind.zenith.auth.CurrentUser
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
import com.fortind.zenith.timeline.NoteListKey
import com.fortind.zenith.timeline.NoteUpdate
import com.fortind.zenith.timeline.UserNotesTab
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
import kotlinx.coroutines.yield
import com.fortind.zenith.notifications.awaitTrue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val NOW_MILLIS = 1_000_000L

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

    private class Fixture(initialToken: String?, debounceMs: Long, settings: AccountSettings) {
        val userApi = FakeUserApi(settings)
        val session = UserSessionService(FakeTokenStore(initialToken), userApi)
        var now = NOW_MILLIS
        val api = FakeNotesApi()
        val stream = FakeTimelineStream()
        val recents = FakeRecents()
        val moderation = FakeModerationApi()
        val viewModelCreated = lazy {
            FeedViewModel(
                session,
                api,
                stream,
                FakeInstance(),
                recents,
                FakeUnicode(),
                moderation,
                debounceMs,
                clock = { now },
                muteDispatcher = Dispatchers.Unconfined,
            )
        }
        val viewModel by viewModelCreated
    }

    private fun muted(soft: String = "", hard: String = "") = AccountSettings(
        mutedWords = (MuteWordSet.parseEditor(soft) as MuteWordsParse.Valid).set,
        hardMutedWords = (MuteWordSet.parseEditor(hard) as MuteWordsParse.Valid).set,
    )

    private suspend fun fixture(
        token: String? = "token",
        debounceMs: Long = 0,
        settings: AccountSettings = AccountSettings(),
        offline: Boolean = false,
    ): Fixture {
        val f = Fixture(token, debounceMs, settings).also(fixtures::add)
        if (offline) f.userApi.failure = IOException("offline")
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
    fun hiddenWordsLeaveTheTimelineAndMutedWordsAreMarked() = runTest {
        val f = fixture(settings = muted(soft = "soft", hard = "hide"))
        f.api.timelines[TimelineKind.Home] = listOf(
            testNote("n3", text = "plain"),
            testNote("n2", text = "soft thing"),
            testNote("n1", text = "hide thing"),
        )

        val timeline = f.viewModel.awaitTimeline(TimelineKind.Home)

        assertEquals(listOf("n3", "n2"), timeline.items.map { it.id })
        assertEquals(mapOf("n2" to listOf("soft")), timeline.softMuted)
    }

    @Test
    fun pagingContinuesFromTheLastRawNoteNotTheLastShownOne() = runTest {
        val f = fixture(settings = muted(hard = "hide"))
        val shown = (60 downTo 41).map { testNote("n$it", text = "plain") }
        val hidden = (40 downTo 31).map { testNote("n$it", text = "hide") }
        val older = (30 downTo 1).map { testNote("n$it", text = "plain") }
        f.api.timelines[TimelineKind.Home] = shown + hidden + older

        val first = f.viewModel.awaitTimeline(TimelineKind.Home)
        assertEquals("n41", first.items.last().id)
        assertEquals("n31", first.cursor)

        f.viewModel.loadMore()
        f.viewModel.awaitTimeline(TimelineKind.Home) { it.items.size == 50 && !it.loadingMore }

        assertEquals("n31", f.api.calls.last().untilId)
    }

    @Test
    fun aFullyHiddenPageKeepsAskingThenStops() = runTest {
        val f = fixture(settings = muted(hard = "hide"))
        f.api.timelines[TimelineKind.Home] = (200 downTo 1).map { testNote("n$it", text = "hide") }

        val timeline = f.viewModel.awaitTimeline(TimelineKind.Home)

        assertEquals(1 + FeedViewModel.HARD_MUTE_EXTRA_PAGES, f.api.calls.size)
        assertTrue(timeline.items.isEmpty())
        assertTrue(timeline.endReached)
    }

    @Test
    fun aHiddenPageFollowedByRealNotesShowsThem() = runTest {
        val f = fixture(settings = muted(hard = "hide"))
        f.api.timelines[TimelineKind.Home] =
            (60 downTo 31).map { testNote("n$it", text = "hide") } + (30 downTo 1).map { testNote("n$it", text = "plain") }

        val timeline = f.viewModel.awaitTimeline(TimelineKind.Home)

        assertEquals(2, f.api.calls.size)
        assertEquals(30, timeline.items.size)
        assertFalse(timeline.endReached)
    }

    @Test
    fun streamedNotesAreFilteredToo() = runTest {
        val f = fixture(settings = muted(soft = "soft", hard = "hide"))
        f.api.timelines[TimelineKind.Home] = page(1)
        f.startStreaming()
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.stream.events.emit(TimelineStreamEvent.Posted(testNote("n2", text = "hide")))
        f.stream.events.emit(TimelineStreamEvent.Posted(testNote("n3", text = "soft")))
        val timeline = f.viewModel.awaitTimeline(TimelineKind.Home) { it.items.size == 2 }

        assertEquals(listOf("n3", "n1"), timeline.items.map { it.id })
        assertEquals(setOf("n3"), timeline.softMuted.keys)
    }

    @Test
    fun threadsAndPersonListsAreNotFiltered() = runTest {
        val f = fixture(settings = muted(hard = "hide"))
        f.api.timelines[TimelineKind.Home] = page(1)
        f.api.notes["n9"] = testNote("n9", text = "hide")
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.openThread("n9")

        awaitTrue { f.viewModel.uiState.value.thread?.note?.text == "hide" }
    }

    @Test
    fun savingNewWordsReloadsTheOpenTimelineAndForgetsTheOthers() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n2", text = "hide"), testNote("n1", text = "plain"))
        f.api.timelines[TimelineKind.Local] = page(5)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.select(TimelineKind.Local)
        f.viewModel.awaitTimeline(TimelineKind.Local)
        f.viewModel.select(TimelineKind.Home)

        f.session.applyUpdatedUser(CurrentUser("me", "me", null, null, null, settings = muted(hard = "hide")))

        val home = f.viewModel.awaitTimeline(TimelineKind.Home) { it.items.size == 1 }
        assertEquals("n1", home.items.single().id)
        assertEquals(TimelineStatus.Idle, f.viewModel.uiState.value.timeline(TimelineKind.Local).status)
    }

    @Test
    fun anUnrelatedProfileChangeDoesNotReload() = runTest {
        val settings = muted(hard = "hide")
        val f = fixture(settings = settings)
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val calls = f.api.calls.size

        f.session.applyUpdatedUser(CurrentUser("me", "me", "New name", null, null, settings = settings.copy(isLocked = true)))
        yield()

        assertEquals(calls, f.api.calls.size)
    }

    @Test
    fun aRenoteUsesTheChosenVisibilityWithinTheRules() = runTest {
        val f = fixture()
        val note = testNote("n1", user = testUser("other"))
        f.api.timelines[TimelineKind.Home] = listOf(note)
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.renote("n1", Visibility.Followers)
        awaitTrue { f.api.renoteCalls.size == 1 }

        assertEquals(Visibility.Followers, f.api.renoteVisibility)
    }

    @Test
    fun aRenoteCannotWidenAHomeNote() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1", user = testUser("other"), visibility = Visibility.Home))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.renote("n1", Visibility.Public)

        assertTrue(f.api.renoteCalls.isEmpty())
    }

    @Test
    fun theRenoteRulesMatchTheServer() {
        val other = testUser("other")
        val mine = testUser("me")
        assertEquals(
            listOf(Visibility.Public, Visibility.Home, Visibility.Followers),
            testNote("n", user = other).renoteVisibilities("me"),
        )
        assertEquals(listOf(Visibility.Home, Visibility.Followers), testNote("n", user = other, visibility = Visibility.Home).renoteVisibilities("me"))
        assertEquals(listOf(Visibility.Followers), testNote("n", user = mine, visibility = Visibility.Followers).renoteVisibilities("me"))
        assertTrue(testNote("n", user = other, visibility = Visibility.Followers).renoteVisibilities("me").isEmpty())
        assertTrue(testNote("n", user = mine, visibility = Visibility.Specified).renoteVisibilities("me").isEmpty())
        assertTrue(testNote("n", user = other).renoteVisibilities(null).isEmpty())
    }

    @Test
    fun pinningAppliesTheServersAnswer() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.togglePin("n1")
        awaitTrue { "n1" in f.viewModel.uiState.value.pinned }

        f.viewModel.togglePin("n1")
        awaitTrue { "n1" !in f.viewModel.uiState.value.pinned }
        assertEquals(listOf("pin:n1", "unpin:n1"), f.api.pinCalls.toList())
    }

    @Test
    fun thePinLimitIsNamed() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        val events = collectEvents(f.viewModel)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.api.pinFailure =HttpStatusException(400, ApiErrorCodes.PIN_LIMIT_EXCEEDED)

        f.viewModel.togglePin("n1")

        awaitTrue { FeedEvent.PinLimitReached in events }
    }

    @Test
    fun aThreadMuteFlipsBackOnFailure() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.toggleThreadMute("n1")
        awaitTrue { "n1" in f.api.threadMutedIds }
        assertTrue("n1" in f.viewModel.uiState.value.threadMuted)

        f.api.muteThreadFailure = IOException("offline")
        f.viewModel.toggleThreadMute("n1")
        awaitTrue { "n1" in f.viewModel.uiState.value.threadMuted }
    }

    @Test
    fun theMenuCheckReadsThreadMuteState() = runTest {
        val f = fixture()
        f.api.threadMutedIds += "n1"
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.checkBookmarked("n1")

        awaitTrue { "n1" in f.viewModel.uiState.value.threadMuted }
    }

    @Test
    fun aNoteThatIsGoneDisappearsEverywhere() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(2, 1)
        val events = collectEvents(f.viewModel)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.api.renoteFailure =HttpStatusException(400, ApiErrorCodes.NO_SUCH_NOTE)

        f.viewModel.renote("n1")

        f.viewModel.awaitTimeline(TimelineKind.Home) { it.items.map(Note::id) == listOf("n2") }
        awaitTrue { FeedEvent.NoteUnavailable in events }
    }

    @Test
    fun threadRenotesAndReactionsLoadOnDemand() = runTest {
        val f = fixture()
        f.api.notes["n1"] = testNote("n1")
        f.api.renoteRows = listOf(testNote("r1", user = testUser("u2")))
        f.api.reactionRows = listOf(NoteReactionRow("x1", testUser("u3"), "❤"), NoteReactionRow("x2", testUser("u4"), "🎉"))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.openThread("n1")
        awaitTrue { f.viewModel.uiState.value.thread?.loading == false }

        f.viewModel.loadThreadRenotes()
        awaitTrue { f.viewModel.uiState.value.thread?.renotes?.status == TimelineStatus.Ready }
        assertEquals("u2", f.viewModel.uiState.value.thread?.renotes?.rows?.single()?.user?.id)

        f.viewModel.loadThreadReactions("🎉")
        awaitTrue { f.viewModel.uiState.value.thread?.reactions?.get("🎉")?.status == TimelineStatus.Ready }
        assertEquals(listOf("u4"), f.viewModel.uiState.value.thread?.reactions?.get("🎉")?.rows?.map { it.user.id })

        f.viewModel.loadThreadRenotes(more = true)
        awaitTrue { f.viewModel.uiState.value.thread?.renotes?.endReached == true }
    }

    @Test
    fun renotingForgetsTheLoadedRenoteList() = runTest {
        val f = fixture()
        f.api.notes["n1"] = testNote("n1", user = testUser("other"))
        f.api.renoteRows = listOf(testNote("r1", user = testUser("u2")))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.openThread("n1")
        awaitTrue { f.viewModel.uiState.value.thread?.loading == false }
        f.viewModel.loadThreadRenotes()
        awaitTrue { f.viewModel.uiState.value.thread?.renotes?.status == TimelineStatus.Ready }

        f.viewModel.renote("n1")

        assertEquals(TimelineStatus.Idle, f.viewModel.uiState.value.thread?.renotes?.status)
    }

    @Test
    fun aChosenListLoadsAndHiddenWordsApplyToIt() = runTest {
        val f = fixture(settings = muted(hard = "hide"))
        val list = NoteCollection(CollectionKind.UserList, "l1")
        f.api.collectionPages[list] = listOf(testNote("n2", text = "hide"), testNote("n1", text = "plain"))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.select(TimelineKind.Lists)
        assertTrue(f.api.collectionCalls.isEmpty())
        f.viewModel.selectCollection(list)

        val timeline = f.viewModel.awaitTimeline(TimelineKind.Lists)
        assertEquals(listOf("n1"), timeline.items.map { it.id })
        assertEquals(list, f.viewModel.uiState.value.collection)
    }

    @Test
    fun switchingListsReplacesTheNotes() = runTest {
        val f = fixture()
        val first = NoteCollection(CollectionKind.UserList, "l1")
        val second = NoteCollection(CollectionKind.Antenna, "a1")
        f.api.collectionPages[first] = page(1)
        f.api.collectionPages[second] = page(2)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.select(TimelineKind.Lists)
        f.viewModel.selectCollection(first)
        f.viewModel.awaitTimeline(TimelineKind.Lists) { it.items.map(Note::id) == listOf("n1") }

        f.viewModel.selectCollection(second)

        f.viewModel.awaitTimeline(TimelineKind.Lists) { it.items.map(Note::id) == listOf("n2") }
    }

    @Test
    fun anAntennaIsNotReadUntilItsSegmentIsOpen() = runTest {
        val f = fixture()
        val antenna = NoteCollection(CollectionKind.Antenna, "a1")
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.selectCollection(antenna)

        assertTrue(f.api.collectionCalls.isEmpty())
        f.viewModel.select(TimelineKind.Lists)
        awaitTrue { f.api.collectionCalls.toList() == listOf(antenna) }
    }

    @Test
    fun aChosenListStreamsWhileOpen() = runTest {
        val f = fixture()
        val list = NoteCollection(CollectionKind.UserList, "l1")
        f.api.collectionPages[list] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.setActive(true)
        f.viewModel.select(TimelineKind.Lists)
        f.viewModel.selectCollection(list)
        f.viewModel.awaitTimeline(TimelineKind.Lists)
        f.stream.collectionStream.subscriptionCount.first { it > 0 }

        f.stream.collectionStream.emit(TimelineStreamEvent.Posted(testNote("n2")))

        f.viewModel.awaitTimeline(TimelineKind.Lists) { it.items.size == 2 }
        assertEquals(listOf(list), f.stream.collectionSubscriptions.toList())
    }

    @Test
    fun theFollowingTimelineHasNoStreamAndRefreshesInFull() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Following] = page(2, 1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.setActive(true)

        f.viewModel.select(TimelineKind.Following)
        f.viewModel.awaitTimeline(TimelineKind.Following)
        assertFalse(f.stream.subscriptions.any { it.second == TimelineKind.Following })
        val before = f.api.calls.size

        f.viewModel.refresh()

        awaitTrue { f.api.calls.size == before + 1 }
        assertEquals(null, f.api.calls.last().sinceId)
    }

    @Test
    fun searchNeedsATokenAndTagsDoNot() = runTest {
        val guest = fixture(token = null)
        guest.viewModel.awaitTimeline(TimelineKind.Local)
        guest.viewModel.loadList(NoteListKey.Search(NoteSearch("x")))
        guest.viewModel.loadList(NoteListKey.Tag("linux"))

        awaitTrue { guest.api.tagCalls.isNotEmpty() }
        assertTrue(guest.api.searchCalls.isEmpty())
        assertEquals(null to "linux", guest.api.tagCalls.single())
    }

    @Test
    fun searchResultsPageByTheLastNote() = runTest {
        val f = fixture()
        f.api.searchResults = (60 downTo 1).map { testNote("n$it") }
        val key = NoteListKey.Search(NoteSearch("x", SearchMedia.Image, onlyHere = true))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.loadList(key)
        awaitTrue { f.viewModel.uiState.value.list(key).items.size == FeedViewModel.PAGE_SIZE }
        f.viewModel.loadMoreList(key)
        awaitTrue { f.viewModel.uiState.value.list(key).items.size == 60 }

        assertEquals(setOf(NoteSearch("x", SearchMedia.Image, onlyHere = true)), f.api.searchCalls.toSet())
    }

    @Test
    fun hiddenWordsDoNotFilterSearchOrTagResults() = runTest {
        val f = fixture(settings = muted(hard = "hide"))
        f.api.searchResults = listOf(testNote("n1", text = "hide"))
        val key = NoteListKey.Tag("linux")
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.loadList(key)

        awaitTrue { f.viewModel.uiState.value.list(key).items.size == 1 }
    }

    @Test
    fun comingBackAfterAWhilePicksUpMutesSetOnTheWeb() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n2", text = "hide"), testNote("n1", text = "plain"))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.userApi.settings = muted(hard = "hide")

        f.viewModel.setActive(true)
        yield()
        assertEquals(2, f.viewModel.uiState.value.timeline(TimelineKind.Home).items.size)

        f.viewModel.setActive(false)
        f.now += FeedViewModel.SETTINGS_REFRESH_MS
        f.viewModel.setActive(true)

        val timeline = f.viewModel.awaitTimeline(TimelineKind.Home) { it.items.size == 1 }
        assertEquals("n1", timeline.items.single().id)
    }

    @Test
    fun startingOfflineLearnsWhoYouAreOnceTheAppBecomesActiveAgain() = runTest {
        val f = fixture(offline = true)
        f.api.timelines[TimelineKind.Home] = page(2, 1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        assertNull(f.viewModel.uiState.value.myUserId)

        f.userApi.failure = null
        f.now += FeedViewModel.UNKNOWN_USER_RETRY_MS
        f.viewModel.setActive(true)

        assertEquals("me", f.viewModel.uiState.first { it.myUserId != null }.myUserId)
    }

    @Test
    fun anEditCanMuteANoteAfterItLoaded() = runTest {
        val f = fixture(settings = muted(soft = "soft"))
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1", text = "plain"))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.noteEdited(testNote("n1", text = "soft now"))

        val timeline = f.viewModel.awaitTimeline(TimelineKind.Home) { it.softMuted.isNotEmpty() }
        assertEquals(listOf("soft"), timeline.softMuted["n1"])
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
    fun switchingAReactionIsOneCreate() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1", reactions = mapOf("❤" to 1), myReaction = "❤"))
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.toggleReaction("n1", "👍")

        assertTrue(f.api.unreactCalls.isEmpty())
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
    fun alreadyReactedCountsAsDone() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1"))
        f.api.reactFailure = HttpStatusException(400, "ALREADY_REACTED")
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.toggleReaction("n1", "👍")

        assertEquals(1, f.api.reactCalls.size)
        assertTrue(f.api.unreactCalls.isEmpty())
        assertEquals("👍", f.viewModel.uiState.value.current.items.single().myReaction)
    }

    @Test
    fun aStreamEditReloadsTheWholeNote() = runTest {
        val f = fixture()
        val quoted = testNote("q1")
        val poll = Poll(listOf(PollChoice("a", 0, false)), multiple = false, expiresAtMillis = null)
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1", text = "old"))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.api.timelines[TimelineKind.Home] = emptyList()
        f.api.notes["n1"] = testNote("n1", text = "new", renote = quoted, poll = poll)
        f.startStreaming()
        f.stream.updates.subscriptionCount.first { it > 0 }

        f.stream.updates.emit(NoteUpdate.Updated("n1"))

        val note = f.viewModel.uiState.first { it.current.items.single().text == "new" }.current.items.single()
        assertEquals(poll, note.poll)
        assertEquals("q1", note.renote?.id)
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
    fun aFailedRenoteCheckIsAskedAgainTheNextTimeButASuccessfulOneIsNot() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(1)
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.api.renotedByMeFailure = IOException("offline")

        f.viewModel.checkRenoted("n1")
        assertFalse("n1" in f.viewModel.uiState.value.renoteChecks)
        assertFalse("n1" in f.viewModel.uiState.value.renoted)

        f.api.renotedByMeFailure = null
        f.api.renotedByMe = true
        f.viewModel.checkRenoted("n1")
        f.viewModel.checkRenoted("n1")

        assertTrue("n1" in f.viewModel.uiState.value.renoted)
        assertEquals(2, f.api.renotedByMeCalls.size)
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

    private suspend fun Fixture.scrollPastTheWindow() {
        api.timelines[TimelineKind.Home] = (300 downTo 1).map { testNote("n$it") }
        viewModel.awaitTimeline(TimelineKind.Home)
        repeat(5) { step ->
            viewModel.loadMore()
            val cursor = "n${271 - 30 * (step + 1)}"
            viewModel.awaitTimeline(TimelineKind.Home) { !it.loadingMore && it.cursor == cursor }
        }
    }

    @Test
    fun scrollingPastTheWindowDropsTheNewestNotesAndRemembersIt() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = (300 downTo 1).map { testNote("n$it") }

        f.scrollPastTheWindow()

        val timeline = f.viewModel.uiState.value.timeline(TimelineKind.Home)
        assertEquals(FeedViewModel.MAX_LOADED, timeline.items.size)
        assertEquals("n270", timeline.items.first().id)
        assertEquals("n121", timeline.items.last().id)
        assertTrue(timeline.headTrimmed)
    }

    @Test
    fun loadingNewerBringsBackTheDroppedNotesAndTrimsTheOldestEnd() = runTest {
        val f = fixture()
        f.scrollPastTheWindow()

        f.viewModel.loadNewer()
        val first = f.viewModel.awaitTimeline(TimelineKind.Home) { !it.loadingNewer && it.items.first().id == "n300" }

        assertEquals(FeedViewModel.MAX_LOADED, first.items.size)
        assertEquals("n151", first.items.last().id)
        assertTrue(first.headTrimmed)
        assertNull(first.cursor)
        assertFalse(first.endReached)

        f.viewModel.loadNewer()
        val second = f.viewModel.awaitTimeline(TimelineKind.Home) { !it.loadingNewer && !it.headTrimmed }

        assertEquals("n300", second.items.first().id)
        f.viewModel.loadMore()
        awaitTrue { f.api.calls.last().untilId == "n151" }
    }

    @Test
    fun streamedPostsAreNotMergedIntoATrimmedHead() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = (300 downTo 1).map { testNote("n$it") }
        f.startStreaming()
        f.scrollPastTheWindow()
        f.viewModel.setAtTop(TimelineKind.Home, true)

        f.stream.events.emit(TimelineStreamEvent.Posted(testNote("n999")))

        val timeline = f.viewModel.uiState.value.timeline(TimelineKind.Home)
        assertTrue(timeline.items.none { it.id == "n999" })
        assertTrue(timeline.pending.isEmpty())
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
    fun mutingAnAuthorSendsTheEndTimeAndDropsTheirPostsAndRenotesOfThem() = runTest {
        val f = fixture()
        val bob = testUser("bob")
        val alice = testUser("alice")
        f.api.timelines[TimelineKind.Home] = listOf(
            testNote("n4", user = bob),
            testNote("n3", user = alice, text = null, renote = testNote("n9", user = bob)),
            testNote("n2", user = alice),
            testNote("n1", user = alice),
        )
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.muteAuthor("n4", durationMillis = 60_000L)

        assertEquals(listOf("mute:bob:${NOW_MILLIS + 60_000L}"), f.moderation.calls)
        assertEquals(listOf("n2", "n1"), f.viewModel.uiState.value.current.items.map { it.id })
        assertEquals(listOf<FeedEvent>(FeedEvent.UserMuted(bob.id, bob.displayName)), events)
    }

    @Test
    fun mutingForeverSendsNoEndTime() = runTest {
        val f = fixture().withPoll(testNote("n1", user = testUser("bob")))

        f.viewModel.muteAuthor("n1", durationMillis = null)

        assertEquals(listOf("mute:bob:forever"), f.moderation.calls)
    }

    @Test
    fun hidingRenotesOnlyDropsTheirRenotes() = runTest {
        val f = fixture()
        val bob = testUser("bob")
        f.api.timelines[TimelineKind.Home] = listOf(
            testNote("n3", user = bob, text = null, renote = testNote("n9", user = testUser("alice"))),
            testNote("n2", user = bob),
        )
        f.viewModel.awaitTimeline(TimelineKind.Home)

        f.viewModel.hideAuthorRenotes("n2")

        assertEquals(listOf("hideRenotes:bob"), f.moderation.calls)
        assertEquals(listOf("n2"), f.viewModel.uiState.value.current.items.map { it.id })
    }

    @Test
    fun blockingDropsThePersonAndSaysSo() = runTest {
        val f = fixture()
        val bob = testUser("bob")
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n2", user = bob), testNote("n1", user = testUser("alice")))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.blockAuthor("n2")

        assertEquals(listOf("block:bob"), f.moderation.calls)
        assertEquals(listOf("n1"), f.viewModel.uiState.value.current.items.map { it.id })
        assertEquals(listOf<FeedEvent>(FeedEvent.UserBlocked(bob.id, bob.displayName)), events)
    }

    @Test
    fun aFailedBlockKeepsTheFeedAndReports() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = listOf(testNote("n1", user = testUser("bob")))
        f.moderation.failure = IOException("offline")
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val events = collectEvents(f.viewModel)

        f.viewModel.blockAuthor("n1")

        assertEquals(1, f.viewModel.uiState.value.current.items.size)
        assertEquals(listOf<FeedEvent>(FeedEvent.ModerationFailed), events)
    }

    @Test
    fun youCannotMuteOrBlockYourself() = runTest {
        val f = fixture().withPoll(testNote("n1", user = testUser("me")))

        f.viewModel.muteAuthor("n1", durationMillis = null)
        f.viewModel.blockAuthor("n1")

        assertTrue(f.moderation.calls.isEmpty())
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

        f.viewModel
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
    fun aFailedRepliesPageKeepsTheThreadOpenAndRetries() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(5)
        f.api.notes["n5"] = testNote("n5")
        f.api.replies = (40 downTo 6).map { testNote("n$it") }
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.openThread("n5")
        f.viewModel.uiState.first { it.thread?.loading == false }

        f.api.moreRepliesFailure = IOException("offline")
        f.viewModel.loadMoreReplies()
        val failed = f.viewModel.uiState.value.thread!!
        assertTrue(failed.repliesLoadMoreFailed)
        assertFalse(failed.repliesEnd)
        assertFalse(failed.repliesLoadingMore)
        assertEquals(30, failed.replies.size)

        f.api.moreRepliesFailure = null
        f.viewModel.loadMoreReplies()
        val recovered = f.viewModel.uiState.first { it.thread?.repliesEnd == true }.thread!!
        assertFalse(recovered.repliesLoadMoreFailed)
        assertEquals(35, recovered.replies.size)
    }

    @Test
    fun aDeletedReplyIsGoneFromTheCachedThreadToo() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(5)
        f.api.notes["n5"] = testNote("n5")
        f.api.replies = listOf(testNote("n7"), testNote("n6", user = testUser("me")))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.openThread("n5")
        f.viewModel.uiState.first { it.thread?.loading == false }

        f.viewModel.deleteNote("n6")
        f.viewModel.uiState.first { it.thread?.replies?.none { reply -> reply.id == "n6" } == true }
        f.viewModel.closeThread()
        f.api.notes.remove("n5")
        f.api.timelines[TimelineKind.Home] = emptyList()
        f.viewModel.openThread("n5")

        val reopened = f.viewModel.uiState.first { it.thread?.failed == true }.thread!!
        assertEquals(listOf("n7"), reopened.replies.map { it.id })
    }

    @Test
    fun branchesLoadNestedRepliesOnDemandAndToggle() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(5)
        f.api.notes["n5"] = testNote("n5")
        f.api.replies = listOf(testNote("n6", repliesCount = 2))
        f.api.branchReplies["n6"] = listOf(testNote("n7"), testNote("n8"))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.openThread("n5")
        f.viewModel.uiState.first { it.thread?.loading == false }

        f.viewModel.toggleBranch("n6")
        val loaded = f.viewModel.uiState.first { it.thread?.branches?.get("n6")?.loading == false }.thread!!
        val branch = loaded.branches.getValue("n6")
        assertEquals(listOf("n7", "n8"), branch.children.map { it.id })
        assertTrue(branch.expanded)
        assertTrue(branch.end)

        f.viewModel.toggleBranch("n6")
        assertFalse(f.viewModel.uiState.value.thread!!.branches.getValue("n6").expanded)

        f.viewModel.toggleBranch("n6")
        assertTrue(f.viewModel.uiState.value.thread!!.branches.getValue("n6").expanded)
        assertEquals(2, f.viewModel.uiState.value.thread!!.branches.getValue("n6").children.size)
    }

    @Test
    fun aFailedBranchLoadCanBeRetried() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(5)
        f.api.notes["n5"] = testNote("n5")
        f.api.replies = listOf(testNote("n6", repliesCount = 1))
        f.api.branchReplies["n6"] = listOf(testNote("n7"))
        f.api.branchFailure = java.io.IOException("offline")
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.openThread("n5")
        f.viewModel.uiState.first { it.thread?.loading == false }

        f.viewModel.toggleBranch("n6")
        f.viewModel.uiState.first { it.thread?.branches?.get("n6")?.failed == true }

        f.api.branchFailure = null
        f.viewModel.toggleBranch("n6")
        val loaded = f.viewModel.uiState.first { it.thread?.branches?.get("n6")?.children?.isNotEmpty() == true }.thread!!
        assertFalse(loaded.branches.getValue("n6").failed)
    }

    @Test
    fun aReplyToANestedReplyJoinsItsBranch() = runTest {
        val f = fixture()
        f.api.timelines[TimelineKind.Home] = page(5)
        f.api.notes["n5"] = testNote("n5")
        f.api.replies = listOf(testNote("n6", repliesCount = 1))
        f.api.branchReplies["n6"] = listOf(testNote("n7"))
        f.viewModel.awaitTimeline(TimelineKind.Home)
        f.viewModel.openThread("n5")
        f.viewModel.uiState.first { it.thread?.loading == false }
        f.viewModel.toggleBranch("n6")
        f.viewModel.uiState.first { it.thread?.branches?.get("n6")?.children?.isNotEmpty() == true }

        f.viewModel.noteCreated(testNote("n9", user = testUser("me"), reply = testNote("n6")))

        val thread = f.viewModel.uiState.value.thread!!
        assertEquals(listOf("n9", "n7"), thread.branches.getValue("n6").children.map { it.id })
        assertEquals(listOf("n6"), thread.replies.map { it.id })
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

    @Test
    fun userNoteListsLoadAndPageLikeAnyOtherList() = runTest {
        val f = fixture()
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val key = NoteListKey.UserNotes("them", UserNotesTab.Notes)
        f.api.userNotePages["them" to UserNotesTab.Notes] = (1..35).map { testNote("n$it") }

        f.viewModel.loadList(key)
        assertEquals(30, f.viewModel.uiState.first { it.list(key).status == TimelineStatus.Ready }.list(key).items.size)

        f.viewModel.loadMoreList(key)
        assertEquals(35, f.viewModel.uiState.first { it.list(key).items.size == 35 }.list(key).items.size)
    }

    @Test
    fun aNoteChangeReachesPinnedAndProfileLists() = runTest {
        val f = fixture()
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val pinned = NoteListKey.Pinned("them")
        val notes = NoteListKey.UserNotes("them", UserNotesTab.Notes)
        f.viewModel.seedList(pinned, listOf(testNote("n1")))
        f.api.userNotePages["them" to UserNotesTab.Notes] = listOf(testNote("n1"))
        f.viewModel.loadList(notes)
        f.viewModel.uiState.first { it.list(notes).status == TimelineStatus.Ready }

        f.viewModel.noteEdited(testNote("n1").copy(text = "edited"))

        val state = f.viewModel.uiState.first { it.list(pinned).items.single().text == "edited" }
        assertEquals("edited", state.list(notes).items.single().text)
        assertTrue(state.list(pinned).endReached)
    }

    @Test
    fun droppingAnAuthorKeepsTheirOwnPageButClearsOtherLists() = runTest {
        val f = fixture()
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val them = testUser("them")
        val note = testNote("n1").copy(user = them)
        val theirs = NoteListKey.UserNotes("them", UserNotesTab.Notes)
        val pinned = NoteListKey.Pinned("them")
        val other = NoteListKey.UserNotes("other", UserNotesTab.Notes)
        f.api.userNotePages["them" to UserNotesTab.Notes] = listOf(note)
        f.api.userNotePages["other" to UserNotesTab.Notes] = listOf(note, testNote("n2"))
        f.viewModel.seedList(pinned, listOf(note))
        f.viewModel.loadList(theirs)
        f.viewModel.loadList(other)
        f.viewModel.uiState.first { it.list(theirs).items.isNotEmpty() && it.list(other).items.size == 2 }

        f.viewModel.dropAuthor("them")

        val state = f.viewModel.uiState.first { it.list(other).items.size == 1 }
        assertEquals(listOf("n2"), state.list(other).items.map { it.id })
        assertEquals(listOf("n1"), state.list(theirs).items.map { it.id })
        assertEquals(listOf("n1"), state.list(pinned).items.map { it.id })
    }

    @Test
    fun aNewNoteJoinsOnlyTheOwnTabsItBelongsIn() = runTest {
        val f = fixture()
        f.viewModel.awaitTimeline(TimelineKind.Home)
        val notes = NoteListKey.UserNotes("me", UserNotesTab.Notes)
        val replies = NoteListKey.UserNotes("me", UserNotesTab.NotesAndReplies)
        val media = NoteListKey.UserNotes("me", UserNotesTab.Media)
        val unloaded = NoteListKey.UserNotes("them", UserNotesTab.Notes)
        listOf(notes, replies, media).forEach { key ->
            f.viewModel.loadList(key)
            f.viewModel.uiState.first { it.list(key).status == TimelineStatus.Ready }
        }

        f.viewModel.noteCreated(testNote("plain"))
        val afterPlain = f.viewModel.uiState.first { it.list(notes).items.isNotEmpty() }
        assertEquals(listOf("plain"), afterPlain.list(replies).items.map { it.id })
        assertTrue(afterPlain.list(media).items.isEmpty())

        f.viewModel.noteCreated(testNote("reply").copy(reply = testNote("parent")))
        val afterReply = f.viewModel.uiState.first { it.list(replies).items.size == 2 }
        assertEquals(listOf("plain"), afterReply.list(notes).items.map { it.id })
        assertEquals(TimelineStatus.Idle, afterReply.list(unloaded).status)
    }
}

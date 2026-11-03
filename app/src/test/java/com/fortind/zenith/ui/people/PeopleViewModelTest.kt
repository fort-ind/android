package com.fortind.zenith.ui.people

import androidx.lifecycle.viewModelScope
import com.fortind.zenith.account.FakeModerationApi
import com.fortind.zenith.account.ReportApi
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import com.fortind.zenith.notifications.awaitTrue
import com.fortind.zenith.people.FollowApi
import com.fortind.zenith.people.FollowListKind
import com.fortind.zenith.people.FollowState
import com.fortind.zenith.people.ListVisibility
import com.fortind.zenith.people.RequestAnswerResult
import com.fortind.zenith.people.UserDetail
import com.fortind.zenith.people.UserListRow
import com.fortind.zenith.people.UsersApi
import com.fortind.zenith.people.followState
import com.fortind.zenith.timeline.testUser
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun detail(id: String, isFollowing: Boolean = false, isLocked: Boolean = false, followers: Int = 0) = UserDetail(
    user = testUser(id),
    isFollowing = isFollowing,
    isLocked = isLocked,
    followersCount = followers,
    followersVisibility = ListVisibility.Public,
)

private class FakeUsersApi : UsersApi {
    val shown = HashMap<String, UserDetail>()
    val handles = HashMap<String, UserDetail>()
    val rows = HashMap<Pair<String, FollowListKind>, List<UserListRow>>()
    var requests: List<UserListRow> = emptyList()
    @Volatile var showFailure: IOException? = null
    @Volatile var listFailure: IOException? = null
    @Volatile var requestsFailure: IOException? = null
    val showCalls = CopyOnWriteArrayList<String>()
    val tokensSeen = CopyOnWriteArrayList<String?>()

    override suspend fun show(token: String?, userId: String): UserDetail {
        showCalls += userId
        tokensSeen += token
        showFailure?.let { throw it }
        return shown[userId] ?: throw HttpStatusException(400, ApiErrorCodes.NO_SUCH_USER)
    }

    override suspend fun showByHandle(token: String?, username: String, host: String?): UserDetail {
        showFailure?.let { throw it }
        return handles[PeopleViewModel.handleKey(username, host)] ?: throw HttpStatusException(400, ApiErrorCodes.FAILED_TO_RESOLVE_REMOTE_USER)
    }

    override suspend fun follows(token: String?, userId: String, kind: FollowListKind, untilId: String?, limit: Int): List<UserListRow> {
        listFailure?.let { throw it }
        val all = rows[userId to kind].orEmpty()
        val older = if (untilId == null) all else all.dropWhile { it.pagingId != untilId }.drop(1)
        return older.take(limit)
    }

    override suspend fun followRequests(token: String, untilId: String?, limit: Int): List<UserListRow> {
        requestsFailure?.let { throw it }
        val older = if (untilId == null) requests else requests.dropWhile { it.pagingId != untilId }.drop(1)
        return older.take(limit)
    }
}

private class FakeFollowApi : FollowApi {
    val calls = CopyOnWriteArrayList<String>()
    @Volatile var failure: IOException? = null
    @Volatile var answer = RequestAnswerResult.Done

    override suspend fun follow(token: String, userId: String) = record("follow:$userId")

    override suspend fun unfollow(token: String, userId: String) = record("unfollow:$userId")

    override suspend fun cancelRequest(token: String, userId: String) = record("cancel:$userId")

    override suspend fun answerRequest(token: String, userId: String, accept: Boolean): RequestAnswerResult {
        record(if (accept) "accept:$userId" else "reject:$userId")
        return answer
    }

    private fun record(call: String) {
        calls += call
        failure?.let { throw it }
    }
}

private class FakeReportApi : ReportApi {
    val calls = CopyOnWriteArrayList<Pair<String, String>>()
    @Volatile var failure: IOException? = null

    override suspend fun report(token: String, userId: String, comment: String) {
        calls += userId to comment
        failure?.let { throw it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val fixtures = mutableListOf<Fixture>()

    @After
    fun tearDown() {
        runBlocking { fixtures.forEach { it.viewModel.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private class Fixture(token: String?) {
        val session = UserSessionService(FakeTokenStore(token), FakeUserApi())
        val users = FakeUsersApi()
        val follows = FakeFollowApi()
        val moderation = FakeModerationApi()
        val reports = FakeReportApi()
        var now = 1_000_000L
        val viewModel = PeopleViewModel(session, users, follows, moderation, reports, clock = { now })
    }

    private suspend fun fixture(token: String? = "token"): Fixture {
        val f = Fixture(token).also(fixtures::add)
        if (token == null) f.session.browseAsGuest() else f.session.restore()
        if (token != null) f.viewModel.uiState.first { it.signedIn && it.myUserId != null }
        return f
    }

    private suspend fun Fixture.loaded(userId: String, detail: UserDetail): PersonState {
        users.shown[userId] = detail
        viewModel.open(userId)
        return viewModel.uiState.first { it.person(userId).status == PersonStatus.Ready }.person(userId)
    }

    private suspend fun Fixture.nextEvent(): PeopleEvent = withTimeout(5_000) { viewModel.events.first() }

    private suspend fun assertNoEvent(f: Fixture) {
        val none = withContext(Dispatchers.Default) {
            try {
                withTimeout(50) { f.viewModel.events.first() }
            } catch (_: TimeoutCancellationException) {
                null
            }
        }
        assertNull(none)
    }

    @Test
    fun openLoadsThePersonAndStaysFreshForAMinute() = runTest {
        val f = fixture()
        val person = f.loaded("a", detail("a", followers = 5))

        assertEquals(5, person.detail?.followersCount)
        f.viewModel.open("a")
        assertEquals(1, f.users.showCalls.size)

        f.now += PeopleViewModel.STALE_AFTER_MS
        f.viewModel.open("a")
        awaitTrue { f.users.showCalls.size == 2 }
    }

    @Test
    fun aSeedShowsUntilTheDetailArrivesAndNeverReplacesIt() = runTest {
        val f = fixture()
        f.viewModel.seed(testUser("a"))
        assertEquals("a", f.viewModel.uiState.value.person("a").user?.id)
        assertNull(f.viewModel.uiState.value.person("a").detail)

        f.loaded("a", detail("a", followers = 2))
        f.viewModel.seed(testUser("a", username = "stale"))
        assertEquals(2, f.viewModel.uiState.value.person("a").detail?.followersCount)
    }

    @Test
    fun anUnknownPersonIsNotFoundAndANetworkFailureCanRetry() = runTest {
        val f = fixture()
        f.viewModel.open("missing")
        f.viewModel.uiState.first { it.person("missing").status == PersonStatus.NotFound }

        f.users.showFailure = IOException("offline")
        f.viewModel.open("a")
        f.viewModel.uiState.first { it.person("a").status == PersonStatus.Failed }

        f.users.showFailure = null
        f.users.shown["a"] = detail("a")
        f.viewModel.refresh("a")
        f.viewModel.uiState.first { it.person("a").status == PersonStatus.Ready }
    }

    @Test
    fun aRefreshFailureKeepsWhatIsShown() = runTest {
        val f = fixture()
        f.loaded("a", detail("a", followers = 7))

        f.users.showFailure = IOException("offline")
        f.viewModel.refresh("a")

        val person = f.viewModel.uiState.first { !it.person("a").refreshing }.person("a")
        assertEquals(PersonStatus.Ready, person.status)
        assertEquals(7, person.detail?.followersCount)
    }

    @Test
    fun followingSendsOneCreateAndRereadsThePerson() = runTest {
        val f = fixture()
        f.loaded("a", detail("a"))
        f.users.shown["a"] = detail("a", isFollowing = true, followers = 1)

        f.viewModel.toggleFollow("a")

        val person = f.viewModel.uiState.first { !it.person("a").busy && it.person("a").detail?.followersCount == 1 }.person("a")
        assertEquals(listOf("follow:a"), f.follows.calls.toList())
        assertEquals(FollowState.Following, person.detail?.followState("me"))
    }

    @Test
    fun unfollowAndWithdrawUseTheirOwnEndpoints() = runTest {
        val f = fixture()
        f.loaded("a", detail("a", isFollowing = true))
        f.users.shown["b"] = detail("b").copy(hasPendingRequestFromYou = true)
        f.viewModel.open("b")
        f.viewModel.uiState.first { it.person("b").status == PersonStatus.Ready }

        f.viewModel.toggleFollow("a")
        awaitTrue { "unfollow:a" in f.follows.calls }
        f.viewModel.toggleFollow("b")
        awaitTrue { "cancel:b" in f.follows.calls }
    }

    @Test
    fun aLockedAccountShowsRequestedEvenIfTheRereadFails() = runTest {
        val f = fixture()
        f.loaded("a", detail("a", isLocked = true))
        f.users.showFailure = IOException("offline")

        f.viewModel.toggleFollow("a")

        val person = f.viewModel.uiState.first { !it.person("a").busy && "follow:a" in f.follows.calls }.person("a")
        assertEquals(FollowState.Requested, person.detail?.followState("me"))
    }

    @Test
    fun aFollowChangeUpdatesEveryRowForThatPerson() = runTest {
        val f = fixture()
        f.users.rows["x" to FollowListKind.Followers] = listOf(UserListRow("r1", detail("a")), UserListRow("r2", detail("b")))
        val key = FollowListKey("x", FollowListKind.Followers)
        f.viewModel.loadList(key)
        f.viewModel.uiState.first { it.list(key).status == UserListStatus.Ready }
        f.users.shown["a"] = detail("a", isFollowing = true)

        f.viewModel.toggleFollow("a")

        val rows = f.viewModel.uiState.first { state -> state.list(key).rows.first().detail.isFollowing }.list(key).rows
        assertFalse(rows.last().detail.isFollowing)
    }

    @Test
    fun aBusyPersonIgnoresASecondClick() = runTest {
        val f = fixture()
        f.loaded("a", detail("a"))
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val blocking = object : FollowApi by f.follows {
            override suspend fun follow(token: String, userId: String) {
                f.follows.calls += "follow:$userId"
                release.await()
            }
        }
        val viewModel = PeopleViewModel(f.session, f.users, blocking, f.moderation, f.reports, clock = { f.now })
        viewModel.uiState.first { it.signedIn && it.myUserId != null }
        viewModel.open("a")
        viewModel.uiState.first { it.person("a").status == PersonStatus.Ready }

        viewModel.toggleFollow("a")
        viewModel.uiState.first { it.person("a").busy }
        viewModel.toggleFollow("a")
        release.complete(Unit)
        viewModel.uiState.first { !it.person("a").busy }

        assertEquals(1, f.follows.calls.size)
        viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun followFailuresNameTheReason() = runTest {
        val f = fixture()
        f.loaded("a", detail("a"))

        f.follows.failure = HttpStatusException(400, ApiErrorCodes.BLOCKED)
        f.viewModel.toggleFollow("a")
        assertEquals(FollowFailure.BlockedByThem, (f.nextEvent() as PeopleEvent.FollowFailed).failure)
        f.viewModel.uiState.first { !it.person("a").busy }

        f.follows.failure = HttpStatusException(400, ApiErrorCodes.BLOCKING)
        f.viewModel.toggleFollow("a")
        assertEquals(FollowFailure.YouBlocked, (f.nextEvent() as PeopleEvent.FollowFailed).failure)
        f.viewModel.uiState.first { !it.person("a").busy }

        f.follows.failure = HttpStatusException(429, ApiErrorCodes.RATE_LIMIT_EXCEEDED)
        f.viewModel.toggleFollow("a")
        assertEquals(FollowFailure.RateLimited, (f.nextEvent() as PeopleEvent.FollowFailed).failure)
        f.viewModel.uiState.first { !it.person("a").busy }

        f.follows.failure = IOException("offline")
        f.viewModel.toggleFollow("a")
        assertEquals(FollowFailure.Other, (f.nextEvent() as PeopleEvent.FollowFailed).failure)
    }

    @Test
    fun aMissingScopeAsksToReauthorizeInsteadOfAFailureMessage() = runTest {
        val f = fixture()
        f.loaded("a", detail("a"))
        f.follows.failure = MissingPermissionException()

        f.viewModel.toggleFollow("a")

        assertEquals(PeopleEvent.PermissionMissing, f.nextEvent())
    }

    @Test
    fun guestsAreAskedToSignIn() = runTest {
        val f = fixture(token = null)
        f.users.shown["a"] = detail("a")
        f.viewModel.uiState.first { !it.signedIn }
        f.viewModel.open("a")
        f.viewModel.uiState.first { it.person("a").status == PersonStatus.Ready }
        assertEquals(listOf<String?>(null), f.users.tokensSeen.toList())

        f.viewModel.toggleFollow("a")

        assertEquals(PeopleEvent.SignInRequired, f.nextEvent())
        assertTrue(f.follows.calls.isEmpty())
    }

    @Test
    fun aRejectedTokenSignsOut() = runTest {
        val f = fixture()
        f.loaded("a", detail("a"))
        f.follows.failure = com.fortind.zenith.auth.TokenRejectedException(401)

        f.viewModel.toggleFollow("a")

        f.session.state.first { it == com.fortind.zenith.auth.SessionState.SignedOut }
        assertNoEvent(f)
    }

    @Test
    fun moderationCallsTheApiAndAnnounces() = runTest {
        val f = fixture()
        f.loaded("a", detail("a"))
        f.users.shown["a"] = detail("a").copy(isMuted = true)

        f.viewModel.moderate("a", ModerationAction.Mute, muteDurationMillis = 1_000L)

        assertEquals(PeopleEvent.Moderated("a", "a", ModerationAction.Mute), f.nextEvent())
        assertEquals("mute:a:${f.now + 1_000L}", f.moderation.calls.single())
        assertTrue(f.viewModel.uiState.first { it.person("a").detail?.isMuted == true }.person("a").detail?.isMuted == true)
    }

    @Test
    fun blockingClearsTheFollowLocallyBeforeTheReread() = runTest {
        val f = fixture()
        f.loaded("a", detail("a", isFollowing = true))
        f.users.showFailure = IOException("offline")

        f.viewModel.moderate("a", ModerationAction.Block)

        assertEquals(PeopleEvent.Moderated("a", "a", ModerationAction.Block), f.nextEvent())
        val person = f.viewModel.uiState.first { !it.person("a").busy }.person("a")
        assertTrue(person.detail?.isBlocking == true)
        assertFalse(person.detail?.isFollowing == true)
    }

    @Test
    fun aHiddenListSaysSoAndAMissingScopeAsksForIt() = runTest {
        val f = fixture()
        val key = FollowListKey("a", FollowListKind.Following)

        f.users.listFailure = HttpStatusException(400, ApiErrorCodes.FORBIDDEN)
        f.viewModel.loadList(key)
        f.viewModel.uiState.first { it.list(key).status == UserListStatus.Hidden }

        val requests = FollowListKey("me", FollowListKind.Requests)
        f.users.requestsFailure = MissingPermissionException()
        f.viewModel.loadList(requests)
        f.viewModel.uiState.first { it.list(requests).status == UserListStatus.NeedsPermission }
    }

    @Test
    fun listsPageByTheFollowRecordIdUntilAnEmptyPage() = runTest {
        val f = fixture()
        val rows = (1..25).map { UserListRow("r$it", detail("u$it")) }
        f.users.rows["a" to FollowListKind.Followers] = rows
        val key = FollowListKey("a", FollowListKind.Followers)

        f.viewModel.loadList(key)
        var list = f.viewModel.uiState.first { it.list(key).status == UserListStatus.Ready }.list(key)
        assertEquals(PeopleViewModel.LIST_PAGE_SIZE, list.rows.size)
        assertEquals("r20", list.cursor)

        f.viewModel.loadMore(key)
        list = f.viewModel.uiState.first { it.list(key).rows.size == 25 && !it.list(key).loadingMore }.list(key)
        assertFalse(list.endReached)

        f.viewModel.loadMore(key)
        list = f.viewModel.uiState.first { it.list(key).endReached }.list(key)
        assertEquals(25, list.rows.size)
    }

    @Test
    fun pendingRequestsCountAndMarkAFullSnapshotAsPartial() = runTest {
        val f = fixture()
        f.users.requests = (1..3).map { UserListRow("r$it", detail("u$it")) }
        f.viewModel.refreshPending()
        assertEquals(PendingRequests(3, partial = false), f.viewModel.uiState.first { it.pending != null }.pending)

        f.users.requests = (1..PeopleViewModel.PENDING_SNAPSHOT_SIZE).map { UserListRow("r$it", detail("u$it")) }
        f.viewModel.refreshPending()
        val full = f.viewModel.uiState.first { it.pending?.partial == true }.pending
        assertEquals(PeopleViewModel.PENDING_SNAPSHOT_SIZE, full?.count)
    }

    @Test
    fun aPendingLookupWithoutTheScopeShowsNoBadgeAndNoPrompt() = runTest {
        val f = fixture()
        f.users.requests = listOf(UserListRow("r1", detail("u1")))
        f.viewModel.refreshPending()
        f.viewModel.uiState.first { it.pending != null }

        f.users.requestsFailure = MissingPermissionException()
        f.viewModel.refreshPending()

        f.viewModel.uiState.first { it.pending == null }
        assertNoEvent(f)
    }

    @Test
    fun answeringARequestRecordsTheOutcomeAndLowersTheCount() = runTest {
        val f = fixture()
        f.users.requests = listOf(UserListRow("r1", detail("u1")), UserListRow("r2", detail("u2")))
        f.viewModel.refreshPending()
        f.viewModel.uiState.first { it.pending?.count == 2 }

        f.viewModel.answerRequest("u1", accept = true)
        f.viewModel.uiState.first { it.answers["u1"] == RequestAnswer.Accepted && it.pending?.count == 1 }

        f.follows.answer = RequestAnswerResult.Gone
        f.viewModel.answerRequest("u2", accept = true)
        f.viewModel.uiState.first { it.answers["u2"] == RequestAnswer.Gone && it.pending?.count == 0 }
        assertEquals(listOf("accept:u1", "accept:u2"), f.follows.calls.toList())
    }

    @Test
    fun aFailedAnswerRestoresTheButtonsAndTellsThePerson() = runTest {
        val f = fixture()
        f.follows.failure = IOException("offline")

        f.viewModel.answerRequest("u1", accept = false)

        assertEquals(PeopleEvent.RequestFailed("", accept = false), f.nextEvent())
        assertNull(f.viewModel.uiState.value.answers["u1"])
    }

    @Test
    fun reportingSendsTheCommentAndNamesFailures() = runTest {
        val f = fixture()

        f.viewModel.report("a", "spam")
        assertEquals(PeopleEvent.Reported, f.nextEvent())
        assertEquals("a" to "spam", f.reports.calls.single())

        f.reports.failure = HttpStatusException(400, ApiErrorCodes.CANNOT_REPORT_THE_ADMIN)
        f.viewModel.report("a", "spam")
        assertEquals(PeopleEvent.ReportFailed(ReportFailure.Admin), f.nextEvent())

        f.reports.failure = HttpStatusException(400, ApiErrorCodes.CANNOT_REPORT_YOURSELF)
        f.viewModel.report("a", "spam")
        assertEquals(PeopleEvent.ReportFailed(ReportFailure.Yourself), f.nextEvent())
    }

    @Test
    fun aHandleResolvesToAPersonOnce() = runTest {
        val f = fixture()
        f.users.handles["alice@remote.example"] = detail("a")

        f.viewModel.openHandle("alice", "remote.example")

        val state = f.viewModel.uiState.first { "alice@remote.example" in it.handles }
        assertEquals("a", state.handles["alice@remote.example"])
        assertEquals(PersonStatus.Ready, state.person("a").status)

        f.viewModel.openHandle("nobody", null)
        f.viewModel.uiState.first { "nobody" in it.failedHandles }
    }

    @Test
    fun theOldestPeopleAreForgottenButNeverTheSignedInOne() = runTest {
        val f = fixture()
        f.loaded("me", detail("me"))
        repeat(PeopleViewModel.MAX_PEOPLE + 3) { index -> f.loaded("u$index", detail("u$index")) }

        val people = f.viewModel.uiState.value.people
        assertTrue(people.size <= PeopleViewModel.MAX_PEOPLE)
        assertTrue("me" in people)
        assertTrue("u${PeopleViewModel.MAX_PEOPLE + 2}" in people)
    }

    @Test
    fun aRelationChangeOnlyRereadsPeopleAlreadyLoaded() = runTest {
        val f = fixture()
        f.viewModel.relationChanged("stranger")
        assertTrue(f.users.showCalls.isEmpty())

        f.loaded("a", detail("a"))
        f.users.shown["a"] = detail("a").copy(isMuted = true)
        f.viewModel.relationChanged("a")
        f.viewModel.uiState.first { it.person("a").detail?.isMuted == true }
    }

    @Test
    fun signingOutForgetsEverything() = runTest {
        val f = fixture()
        f.loaded("a", detail("a"))

        f.session.signOut()

        val state = f.viewModel.uiState.first { !it.signedIn }
        assertTrue(state.people.isEmpty())
        assertNull(state.myUserId)
    }
}

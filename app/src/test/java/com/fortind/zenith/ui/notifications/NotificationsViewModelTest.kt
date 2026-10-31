package com.fortind.zenith.ui.notifications

import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.notifications.FakeNotificationStream
import com.fortind.zenith.notifications.FakeNotificationsApi
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.notifications.NotificationStreamEvent
import com.fortind.zenith.notifications.NotificationType
import com.fortind.zenith.notifications.testNotification
import java.io.IOException
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val fixtures = mutableListOf<Fixture>()

    @After
    fun tearDown() {
        runBlocking {
            fixtures.filter { it.viewModelCreated.isInitialized() }.forEach {
                it.viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
        Dispatchers.resetMain()
    }

    private fun fixture(initialToken: String? = "token") = Fixture(initialToken).also(fixtures::add)

    private class Fixture(initialToken: String? = "token") {
        val tokenStore = FakeTokenStore(initialToken)
        val session = UserSessionService(tokenStore, FakeUserApi())
        val api = FakeNotificationsApi()
        val stream = FakeNotificationStream()
        val viewModelCreated = lazy { NotificationsViewModel(session, api, stream) }
        val viewModel by viewModelCreated

        suspend fun startListening() {
            viewModel.setForeground(true)
            stream.shared.subscriptionCount.first { it > 0 }
        }
    }

    private suspend fun NotificationsViewModel.awaitReady(
        predicate: (NotificationsUiState.Ready) -> Boolean = { !it.loading },
    ): NotificationsUiState.Ready =
        uiState.first { it is NotificationsUiState.Ready && predicate(it) } as NotificationsUiState.Ready

    @Test
    fun loadsTheFirstPageAndTheUnreadCount() = runTest {
        val f = fixture()
        f.api.items = listOf(testNotification("3"), testNotification("2"), testNotification("1"))
        f.api.unread = 2
        f.session.restore()

        val ready = f.viewModel.awaitReady()
        f.viewModel.unreadCount.first { it == 2 }

        assertEquals(listOf("3", "2", "1"), ready.items.map { it.id })
        assertTrue(ready.endReached)
    }

    @Test
    fun guestsSeeTheGuestState() = runTest {
        val f = fixture(initialToken = null)
        f.session.restore()
        f.session.browseAsGuest()

        f.viewModel.uiState.first { it == NotificationsUiState.Guest }
    }

    @Test
    fun filterRequestsOnlyItsTypes() = runTest {
        val f = fixture()
        f.api.items = listOf(
            testNotification("3", NotificationType.Reaction),
            testNotification("2", NotificationType.Reply),
            testNotification("1", NotificationType.Mention),
        )
        f.session.restore()
        f.viewModel.awaitReady()

        f.viewModel.setFilter(NotificationFilter.Mentions)
        val ready = f.viewModel.awaitReady { it.filter == NotificationFilter.Mentions && !it.loading }

        assertEquals(listOf("2", "1"), ready.items.map { it.id })
        assertEquals(NotificationFilter.Mentions.types, f.api.fetchTypes.last())
    }

    @Test
    fun scrollingLoadsOlderNotificationsWithoutDuplicates() = runTest {
        val f = fixture()
        val all = (1..45).map { testNotification(id = "%02d".format(46 - it)) }
        f.api.items = all
        f.session.restore()
        val first = f.viewModel.awaitReady()
        assertEquals(NotificationsViewModel.PAGE_SIZE, first.items.size)

        f.viewModel.loadMore()
        val second = f.viewModel.awaitReady { !it.loadingMore && it.items.size > NotificationsViewModel.PAGE_SIZE }

        assertEquals(all.map { it.id }, second.items.map { it.id })
        assertTrue(second.endReached)
    }

    @Test
    fun missingPermissionAsksToReauthorize() = runTest {
        val f = fixture()
        f.api.fetchFailure = MissingPermissionException()
        f.session.restore()

        f.viewModel.uiState.first { it == NotificationsUiState.NeedsPermission }
    }

    @Test
    fun aFailedLoadCanBeRetried() = runTest {
        val f = fixture()
        f.api.items = listOf(testNotification("1"))
        f.api.fetchFailure = IOException("offline")
        f.session.restore()
        f.viewModel.awaitReady { it.failed }

        f.api.fetchFailure = null
        f.viewModel.retry()
        val ready = f.viewModel.awaitReady { !it.failed && !it.loading }

        assertEquals(listOf("1"), ready.items.map { it.id })
    }

    @Test
    fun aRejectedTokenSignsOut() = runTest {
        val f = fixture()
        f.api.fetchFailure = TokenRejectedException(401)
        f.session.restore()
        f.viewModel

        f.session.state.first { it == SessionState.SignedOut }
    }

    @Test
    fun markAllAsReadClearsTheUnreadCount() = runTest {
        val f = fixture()
        f.api.unread = 4
        f.session.restore()
        f.viewModel.unreadCount.first { it == 4 }

        f.viewModel.markAllAsRead()

        f.viewModel.unreadCount.first { it == 0 }
        assertEquals(listOf("token"), f.api.markAllReadTokens.toList())
    }

    @Test
    fun aFailedMarkAsReadKeepsTheCountAndReportsIt() = runTest {
        val f = fixture()
        f.api.unread = 4
        f.api.markReadFailure = IOException("offline")
        f.session.restore()
        f.viewModel.unreadCount.first { it == 4 }

        f.viewModel.markAllAsRead()

        assertEquals(NotificationsEvent.MarkReadFailed, f.viewModel.events.first())
        assertEquals(4, f.viewModel.unreadCount.value)
    }

    @Test
    fun acceptingAFollowRequestCallsTheApiAndShowsTheOutcome() = runTest {
        val f = fixture()
        val request = testNotification("1", NotificationType.FollowRequest)
        f.api.items = listOf(request)
        f.session.restore()
        f.viewModel.awaitReady()

        f.viewModel.respondToFollowRequest(request, accept = true)
        val ready = f.viewModel.awaitReady { it.followRequests[request.id] == FollowRequestProgress.Accepted }

        assertEquals(FollowRequestProgress.Accepted, ready.followRequests[request.id])
        assertEquals(listOf("actor-1" to true), f.api.followResponses.toList())
    }

    @Test
    fun aFailedFollowRequestResponseCanBeTriedAgain() = runTest {
        val f = fixture()
        val request = testNotification("1", NotificationType.FollowRequest)
        f.api.items = listOf(request)
        f.api.followFailure = HttpStatusException(400)
        f.session.restore()
        f.viewModel.awaitReady()

        f.viewModel.respondToFollowRequest(request, accept = false)

        assertEquals(NotificationsEvent.FollowRequestFailed, f.viewModel.events.first())
        assertEquals(emptyMap<String, FollowRequestProgress>(), f.viewModel.awaitReady().followRequests)
    }

    @Test
    fun liveNotificationsArePrependedAndCounted() = runTest {
        val f = fixture()
        f.api.items = listOf(testNotification("1"))
        f.session.restore()
        f.viewModel.awaitReady()
        f.startListening()

        f.stream.shared.emit(NotificationStreamEvent.Connected)
        f.stream.shared.emit(NotificationStreamEvent.Received(testNotification("2")))

        val ready = f.viewModel.awaitReady { it.items.size == 2 }
        assertEquals(listOf("2", "1"), ready.items.map { it.id })
        assertEquals(1, f.viewModel.unreadCount.value)
    }

    @Test
    fun liveNotificationsOutsideTheActiveFilterOnlyBumpTheCount() = runTest {
        val f = fixture()
        f.api.items = listOf(testNotification("1", NotificationType.Mention))
        f.session.restore()
        f.viewModel.awaitReady()
        f.viewModel.setFilter(NotificationFilter.Mentions)
        f.viewModel.awaitReady { it.filter == NotificationFilter.Mentions && !it.loading }
        f.startListening()

        f.stream.shared.emit(NotificationStreamEvent.Received(testNotification("2", NotificationType.Reaction)))

        f.viewModel.unreadCount.first { it == 1 }
        assertEquals(listOf("1"), f.viewModel.awaitReady().items.map { it.id })
    }

    @Test
    fun readAllOnAnotherDeviceClearsTheCount() = runTest {
        val f = fixture()
        f.api.unread = 3
        f.session.restore()
        f.viewModel.unreadCount.first { it == 3 }
        f.startListening()

        f.stream.shared.emit(NotificationStreamEvent.AllRead)

        f.viewModel.unreadCount.first { it == 0 }
    }

    @Test
    fun reconnectingCatchesUpOnMissedNotifications() = runTest {
        val f = fixture()
        f.api.items = listOf(testNotification("1", createdAt = 1_000L))
        f.session.restore()
        f.viewModel.awaitReady()
        f.startListening()
        f.stream.shared.emit(NotificationStreamEvent.Connected)

        f.api.items = listOf(testNotification("2", createdAt = 2_000L), testNotification("1", createdAt = 1_000L))
        f.stream.shared.emit(NotificationStreamEvent.Connected)

        val ready = f.viewModel.awaitReady { it.items.size == 2 }
        assertEquals(listOf("2", "1"), ready.items.map { it.id })
    }

    @Test
    fun signingInAgainReloadsWithTheNewToken() = runTest {
        val f = fixture()
        f.api.fetchFailure = MissingPermissionException()
        f.session.restore()
        f.viewModel.uiState.first { it == NotificationsUiState.NeedsPermission }

        f.api.fetchFailure = null
        f.api.items = listOf(testNotification("1"))
        f.session.signIn("upgraded-token")

        val ready = f.viewModel.awaitReady { it.items.isNotEmpty() }
        assertEquals(listOf("1"), ready.items.map { it.id })
        assertEquals("upgraded-token", f.api.fetchTokens.last())
    }
}

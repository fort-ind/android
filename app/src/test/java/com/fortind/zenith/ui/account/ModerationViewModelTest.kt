package com.fortind.zenith.ui.account

import androidx.lifecycle.viewModelScope
import com.fortind.zenith.account.FakeModerationApi
import com.fortind.zenith.account.ModerationKind
import com.fortind.zenith.account.moderationEntry
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import com.fortind.zenith.notifications.awaitTrue
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val fixtures = mutableListOf<Fixture>()

    @After
    fun tearDown() {
        runBlocking {
            fixtures.forEach { it.viewModel.viewModelScope.coroutineContext.job.cancelAndJoin() }
        }
        Dispatchers.resetMain()
    }

    private class Fixture(token: String? = "token") {
        val session = UserSessionService(FakeTokenStore(token), FakeUserApi())
        val api = FakeModerationApi()
        val viewModel = ModerationViewModel(session, api, clock = { NOW })
    }

    private fun fixture(token: String? = "token") = Fixture(token).also(fixtures::add)

    private suspend fun ModerationViewModel.awaitList(kind: ModerationKind, status: ListStatus = ListStatus.Ready) =
        uiState.first { it.list(kind).status == status }.list(kind)

    @Test
    fun loadingAListShowsItsEntriesAndEmptyIsAnEndedList() = runTest {
        val f = fixture()
        f.api.pages[ModerationKind.Mute] = mutableListOf(listOf(moderationEntry("2"), moderationEntry("1")))

        f.viewModel.load(ModerationKind.Mute)
        f.viewModel.load(ModerationKind.Block)

        assertEquals(listOf("2", "1"), f.viewModel.awaitList(ModerationKind.Mute).items.map { it.id })
        val blocks = f.viewModel.awaitList(ModerationKind.Block)
        assertTrue(blocks.items.isEmpty())
        assertTrue(blocks.endReached)
    }

    @Test
    fun loadingTwiceDoesNotRefetch() = runTest {
        val f = fixture()
        f.viewModel.load(ModerationKind.Mute)
        f.viewModel.awaitList(ModerationKind.Mute)

        f.viewModel.load(ModerationKind.Mute)

        assertEquals(1, f.api.calls.count { it.startsWith("list:Mute") })
    }

    @Test
    fun loadMorePagesFromTheLastEntryWithoutDuplicates() = runTest {
        val f = fixture()
        f.api.pages[ModerationKind.Block] = mutableListOf(
            listOf(moderationEntry("3", kind = ModerationKind.Block), moderationEntry("2", kind = ModerationKind.Block)),
            listOf(moderationEntry("2", kind = ModerationKind.Block), moderationEntry("1", kind = ModerationKind.Block)),
        )
        f.viewModel.load(ModerationKind.Block)
        f.viewModel.awaitList(ModerationKind.Block)

        f.viewModel.loadMore(ModerationKind.Block)

        awaitTrue { f.viewModel.uiState.value.list(ModerationKind.Block).items.size == 3 }
        val list = f.viewModel.uiState.value.list(ModerationKind.Block)
        assertEquals(listOf("3", "2", "1"), list.items.map { it.id })
        assertTrue(f.api.calls.contains("list:Block:2"))
    }

    @Test
    fun removingAnEntryCallsTheRightEndpointDropsItAndOffersUndo() = runTest {
        val f = fixture()
        val entry = moderationEntry("1", kind = ModerationKind.Block)
        f.api.pages[ModerationKind.Block] = mutableListOf(listOf(entry))
        f.viewModel.load(ModerationKind.Block)
        f.viewModel.awaitList(ModerationKind.Block)

        f.viewModel.remove(entry)

        assertEquals(ModerationEvent.Removed(entry), f.viewModel.events.first())
        assertTrue(f.api.calls.contains("unblock:u1"))
        assertTrue(f.viewModel.uiState.value.list(ModerationKind.Block).items.isEmpty())
    }

    @Test
    fun undoingAMuteReappliesTheRemainingTimeAndRefreshes() = runTest {
        val f = fixture()
        val timed = moderationEntry("1", expiresAtMillis = NOW + 5_000L)
        val lapsed = moderationEntry("2", expiresAtMillis = NOW - 5_000L)

        f.viewModel.undo(timed)
        f.viewModel.undo(lapsed)

        awaitTrue { f.api.calls.contains("mute:u1:${NOW + 5_000L}") && f.api.calls.contains("mute:u2:forever") }
        assertTrue(f.api.calls.contains("mute:u1:${NOW + 5_000L}"))
        assertTrue(f.api.calls.contains("mute:u2:forever"))
    }

    @Test
    fun showingRenotesAndHidingThemAgainUseTheRenoteEndpoints() = runTest {
        val f = fixture()
        val entry = moderationEntry("1", kind = ModerationKind.RenoteMute)

        f.viewModel.remove(entry)
        awaitTrue { f.api.calls.contains("showRenotes:u1") && f.viewModel.uiState.value.busy.isEmpty() }
        f.viewModel.undo(entry)

        awaitTrue { f.api.calls.contains("showRenotes:u1") && f.api.calls.contains("hideRenotes:u1") }
        assertTrue(f.api.calls.contains("showRenotes:u1"))
        assertTrue(f.api.calls.contains("hideRenotes:u1"))
    }

    @Test
    fun aFailedRemovalKeepsTheEntryAndReports() = runTest {
        val f = fixture()
        val entry = moderationEntry("1")
        f.api.pages[ModerationKind.Mute] = mutableListOf(listOf(entry))
        f.viewModel.load(ModerationKind.Mute)
        f.viewModel.awaitList(ModerationKind.Mute)
        f.api.failure = IOException("offline")

        f.viewModel.remove(entry)

        assertEquals(1, f.viewModel.uiState.value.list(ModerationKind.Mute).items.size)
        assertEquals(ModerationEvent.Failed, f.viewModel.events.first())
        assertFalse(f.viewModel.uiState.value.busy.isNotEmpty())
    }

    @Test
    fun rateLimitsGetTheirOwnMessage() = runTest {
        val f = fixture()
        f.api.failure = HttpStatusException(429, "RATE_LIMIT_EXCEEDED")

        f.viewModel.remove(moderationEntry("1"))

        assertEquals(ModerationEvent.RateLimited, f.viewModel.events.first())
    }

    @Test
    fun missingPermissionsAskToReauthorizeInsteadOfShowingAnError() = runTest {
        val f = fixture()
        f.api.failure = MissingPermissionException()

        f.viewModel.load(ModerationKind.Mute)

        assertTrue(f.viewModel.uiState.first { it.needsPermission }.needsPermission)
        assertEquals(ListStatus.Failed, f.viewModel.uiState.value.list(ModerationKind.Mute).status)
    }

    @Test
    fun loggingInAgainClearsThePermissionPromptAndTheLists() = runTest {
        val f = fixture()
        f.api.failure = MissingPermissionException()
        f.viewModel.load(ModerationKind.Mute)
        f.viewModel.uiState.first { it.needsPermission }

        f.session.signIn("fresh-token")

        val state = f.viewModel.uiState.first { !it.needsPermission }
        assertEquals(ListStatus.Idle, state.list(ModerationKind.Mute).status)
    }

    @Test
    fun withoutATokenTheListFailsQuietly() = runTest {
        val f = fixture(token = null)

        f.viewModel.load(ModerationKind.Mute)

        assertEquals(ListStatus.Failed, f.viewModel.awaitList(ModerationKind.Mute, ListStatus.Failed).status)
        assertTrue(f.api.calls.isEmpty())
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}

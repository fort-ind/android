package com.fortind.zenith.ui.scheduled

import androidx.lifecycle.viewModelScope
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.auth.InMemoryScopeStampStore
import com.fortind.zenith.auth.ScopeStamp
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import com.fortind.zenith.schedule.FakeScheduleApi
import com.fortind.zenith.schedule.testScheduled
import com.fortind.zenith.window.WindowEvent
import com.fortind.zenith.window.WindowEvents
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<ScheduledViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        mainDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private class Fixture(granted: Boolean, events: WindowEvents) {
        val api = FakeScheduleApi()
        val session = UserSessionService(
            FakeTokenStore("token"),
            FakeUserApi(),
            InMemoryScopeStampStore(ScopeStamp(granted = if (granted) FortInstance.SCOPES.toSet() else null)),
        )
        val window = events.link(Any())
        val other = events.link(Any())
        val viewModel = ScheduledViewModel(session, api, window)
    }

    private suspend fun fixture(granted: Boolean = true, events: WindowEvents = WindowEvents()): Fixture =
        Fixture(granted, events).also {
            viewModels += it.viewModel
            it.session.restore()
        }

    private suspend fun ScheduledViewModel.awaitSettled() {
        uiState.first { it.status != ScheduledStatus.Idle && it.status != ScheduledStatus.Loading }
    }

    @Test
    fun loadsTheWaitingPostsSoonestFirst() = runTest {
        val f = fixture()
        f.api.items = listOf(testScheduled("late", 3_000L), testScheduled("soon", 1_000L), testScheduled("mid", 2_000L))

        f.viewModel.load()
        f.viewModel.awaitSettled()

        assertEquals(listOf("soon", "mid", "late"), f.viewModel.uiState.value.items.map { it.id })
        assertEquals(3, f.viewModel.uiState.value.count)
    }

    @Test
    fun asksForNothingWhenTheTokenLacksTheScope() = runTest {
        val f = fixture(granted = false)

        f.viewModel.load()
        f.viewModel.awaitSettled()

        assertEquals(ScheduledStatus.NeedsPermission, f.viewModel.uiState.value.status)
        assertTrue(f.api.listCalls.isEmpty())
    }

    @Test
    fun aServerRefusalOfThePermissionShowsTheReauthorizePrompt() = runTest {
        val f = fixture()
        f.api.listFailure = MissingPermissionException()

        f.viewModel.load()
        f.viewModel.awaitSettled()

        assertEquals(ScheduledStatus.NeedsPermission, f.viewModel.uiState.value.status)
    }

    @Test
    fun aFailedLoadCanBeRetried() = runTest {
        val f = fixture()
        f.api.listFailure = IOException("offline")
        f.viewModel.load()
        f.viewModel.awaitSettled()
        assertEquals(ScheduledStatus.Failed, f.viewModel.uiState.value.status)

        f.api.listFailure = null
        f.api.items = listOf(testScheduled("a", 1_000L))
        f.viewModel.refresh(quiet = false)
        f.viewModel.uiState.first { it.status == ScheduledStatus.Ready }

        assertEquals(1, f.viewModel.uiState.value.items.size)
    }

    @Test
    fun aQuietRefreshThatFailsKeepsWhatWasShown() = runTest {
        val f = fixture()
        f.api.items = listOf(testScheduled("a", 1_000L))
        f.viewModel.load()
        f.viewModel.awaitSettled()

        f.api.listFailure = IOException("offline")
        f.viewModel.refresh(quiet = true)

        assertEquals(ScheduledStatus.Ready, f.viewModel.uiState.value.status)
        assertEquals(1, f.viewModel.uiState.value.items.size)
    }

    @Test
    fun deletingRemovesTheRowAndSaysSo() = runTest {
        val f = fixture()
        f.api.items = listOf(testScheduled("a", 1_000L), testScheduled("b", 2_000L))
        f.viewModel.load()
        f.viewModel.awaitSettled()
        val event = async { f.viewModel.events.first() }

        f.viewModel.delete(f.viewModel.uiState.value.items.first())

        assertEquals(ScheduledEvent.Deleted, event.await())
        assertEquals(listOf("a"), f.api.deleted.toList())
        assertEquals(listOf("b"), f.viewModel.uiState.value.items.map { it.id })
        assertTrue(f.viewModel.uiState.value.busy.isEmpty())
    }

    @Test
    fun aFailedDeleteKeepsTheRowAndSaysSo() = runTest {
        val f = fixture()
        f.api.items = listOf(testScheduled("a", 1_000L))
        f.viewModel.load()
        f.viewModel.awaitSettled()
        f.api.deleteFailure = IOException("offline")
        val event = async { f.viewModel.events.first() }

        f.viewModel.delete(f.viewModel.uiState.value.items.single())

        assertEquals(ScheduledEvent.Failed, event.await())
        assertEquals(1, f.viewModel.uiState.value.items.size)
        assertTrue(f.viewModel.uiState.value.busy.isEmpty())
    }

    @Test
    fun anotherWindowsChangeRefreshesAWindowThatHasLoaded() = runTest {
        val events = WindowEvents()
        val f = fixture(events = events)
        f.api.items = listOf(testScheduled("a", 1_000L))
        f.viewModel.load()
        f.viewModel.awaitSettled()

        f.api.items = listOf(testScheduled("a", 1_000L), testScheduled("b", 2_000L))
        f.other.publish(WindowEvent.ScheduledChanged())
        f.viewModel.uiState.first { it.items.size == 2 }

        assertEquals(listOf("a", "b"), f.viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun aChangeBeforeAnythingWasLoadedDoesNotLoadIt() = runTest {
        val events = WindowEvents()
        val f = fixture(events = events)

        f.other.publish(WindowEvent.ScheduledChanged())

        assertEquals(ScheduledStatus.Idle, f.viewModel.uiState.value.status)
        assertTrue(f.api.listCalls.isEmpty())
    }
}

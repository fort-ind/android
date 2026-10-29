package com.fortind.zenith.notifications

import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.FakeSettingsStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSyncerTest {

    private class Fixture(settings: AppSettings = AppSettings(systemNotifications = true)) {
        val session = UserSessionService(FakeTokenStore("token"), FakeUserApi())
        val api = FakeNotificationsApi()
        val settingsStore = FakeSettingsStore(settings)
        val syncStore = FakeNotificationSyncStore()
        val foreground = AppForegroundTracker()
        val notifier = FakeSystemNotifier()
        val syncer = NotificationSyncer(session, api, settingsStore, syncStore, foreground, notifier)
    }

    @Test
    fun doesNothingWhenSystemNotificationsAreOff() = runTest {
        val f = Fixture(AppSettings(systemNotifications = false))
        f.api.items = listOf(testNotification("1"))

        assertEquals(SyncOutcome.Done, f.syncer.sync())

        assertTrue(f.api.fetchTokens.isEmpty())
        assertTrue(f.notifier.shown.isEmpty())
    }

    @Test
    fun theFirstRunOnlySetsTheBaselineInsteadOfNotifyingTheBacklog() = runTest {
        val f = Fixture()
        f.api.items = listOf(testNotification("2", createdAt = 2_000L), testNotification("1", createdAt = 1_000L))
        f.api.unread = 2

        f.syncer.sync()

        assertEquals(2_000L, f.syncStore.lastSeen)
        assertTrue(f.notifier.shown.isEmpty())
    }

    @Test
    fun notifiesOnlyFreshItemsThatAreStillUnread() = runTest {
        val f = Fixture()
        f.syncStore.lastSeen = 1_000L
        f.api.items = listOf(
            testNotification("3", createdAt = 3_000L),
            testNotification("2", createdAt = 2_000L),
            testNotification("1", createdAt = 1_000L),
        )
        f.api.unread = 1

        f.syncer.sync()

        assertEquals(listOf("3"), f.notifier.shown.map { it.id })
        assertEquals(3_000L, f.syncStore.lastSeen)
    }

    @Test
    fun nothingIsShownWhenEverythingWasAlreadyReadElsewhere() = runTest {
        val f = Fixture()
        f.syncStore.lastSeen = 1_000L
        f.api.items = listOf(testNotification("2", createdAt = 2_000L))
        f.api.unread = 0

        f.syncer.sync()

        assertTrue(f.notifier.shown.isEmpty())
        assertEquals(2_000L, f.syncStore.lastSeen)
    }

    @Test
    fun staysQuietWhileTheAppIsOnScreenButStillAdvancesTheBaseline() = runTest {
        val f = Fixture()
        f.syncStore.lastSeen = 1_000L
        f.foreground.inForeground = true
        f.api.items = listOf(testNotification("2", createdAt = 2_000L))
        f.api.unread = 1

        f.syncer.sync()

        assertTrue(f.notifier.shown.isEmpty())
        assertEquals(2_000L, f.syncStore.lastSeen)
    }

    @Test
    fun onlyEnabledCategoriesAreShown() = runTest {
        val f = Fixture(AppSettings(systemNotifications = true, notificationCategories = setOf(NotificationCategory.Follows)))
        f.syncStore.lastSeen = 1_000L
        f.api.items = listOf(
            testNotification("3", NotificationType.Mention, createdAt = 3_000L),
            testNotification("2", NotificationType.Follow, createdAt = 2_000L),
        )
        f.api.unread = 2

        f.syncer.sync()

        assertEquals(listOf("2"), f.notifier.shown.map { it.id })
    }

    @Test
    fun aNetworkFailureAsksForARetry() = runTest {
        val f = Fixture()
        f.api.fetchFailure = IOException("offline")

        assertEquals(SyncOutcome.Retry, f.syncer.sync())
    }

    @Test
    fun aRejectedTokenSignsOut() = runTest {
        val f = Fixture()
        f.session.restore()
        f.api.fetchFailure = TokenRejectedException(401)

        assertEquals(SyncOutcome.Done, f.syncer.sync())

        assertEquals(SessionState.SignedOut, f.session.state.first())
    }

    @Test
    fun missingPermissionStopsQuietlyWithoutRetrying() = runTest {
        val f = Fixture()
        f.api.fetchFailure = MissingPermissionException()

        assertEquals(SyncOutcome.Done, f.syncer.sync())
    }

    @Test
    fun acknowledgingSeenNotificationsMovesTheBaselineWithoutNotifying() = runTest {
        val f = Fixture()
        f.api.items = listOf(testNotification("5", createdAt = 5_000L))

        f.syncer.acknowledgeSeen()

        assertEquals(5_000L, f.syncStore.lastSeen)
        assertTrue(f.notifier.shown.isEmpty())
    }
}

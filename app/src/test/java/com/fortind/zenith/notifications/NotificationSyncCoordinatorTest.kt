package com.fortind.zenith.notifications

import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.FakeSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSyncCoordinatorTest {

    private class Fixture(enabled: Boolean) {
        val settings = FakeSettingsStore(AppSettings(systemNotifications = enabled))
        val session = UserSessionService(FakeTokenStore("token"), FakeUserApi())
        val syncStore = FakeNotificationSyncStore()
        val work = FakeNotificationWork()
        val notifier = FakeSystemNotifier()
        val coordinator = NotificationSyncCoordinator(settings, session, work, syncStore, notifier)
    }

    @Test
    fun pollsWhileTheSwitchIsOnAndSignedIn() = runTest(UnconfinedTestDispatcher()) {
        val f = Fixture(enabled = true)
        f.session.restore()

        f.coordinator.start(backgroundScope)

        awaitTrue { f.work.polling }
    }

    @Test
    fun startsPollingWhenTheSwitchIsTurnedOn() = runTest(UnconfinedTestDispatcher()) {
        val f = Fixture(enabled = false)
        f.session.restore()
        f.coordinator.start(backgroundScope)
        awaitTrue { f.notifier.clearCount > 0 }

        f.settings.setSystemNotifications(true)

        awaitTrue { f.work.polling }
    }

    @Test
    fun turningTheSwitchOffTearsEverythingDown() = runTest(UnconfinedTestDispatcher()) {
        val f = Fixture(enabled = true)
        f.session.restore()
        f.coordinator.start(backgroundScope)
        awaitTrue { f.work.polling }
        f.syncStore.lastSeen = 5_000L

        f.settings.setSystemNotifications(false)

        awaitTrue { !f.work.polling && f.syncStore.lastSeen == 0L && f.notifier.clearCount > 0 }
    }

    @Test
    fun staysIdleWhileTheSwitchIsOff() = runTest(UnconfinedTestDispatcher()) {
        val f = Fixture(enabled = false)
        f.session.restore()

        f.coordinator.start(backgroundScope)
        awaitTrue { f.notifier.clearCount > 0 }

        assertFalse(f.work.polling)
    }

    @Test
    fun signingOutStopsDelivery() = runTest(UnconfinedTestDispatcher()) {
        val f = Fixture(enabled = true)
        f.session.restore()
        f.coordinator.start(backgroundScope)
        awaitTrue { f.work.polling }

        f.session.signOut()

        awaitTrue { !f.work.polling }
    }
}

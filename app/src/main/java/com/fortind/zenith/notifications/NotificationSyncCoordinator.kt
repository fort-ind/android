package com.fortind.zenith.notifications

import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NotificationSyncCoordinator(
    private val settings: SettingsStore,
    private val session: UserSessionService,
    private val work: NotificationWork,
    private val syncStore: NotificationSyncStore,
    private val notifier: SystemNotifier,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            active().collectLatest { active ->
                if (active) work.schedulePolling() else tearDown()
            }
        }
    }

    private fun active() = combine(
        settings.settings.map { it.systemNotifications }.distinctUntilChanged(),
        session.state.filter { it != SessionState.Restoring }.map { it is SessionState.SignedIn }.distinctUntilChanged(),
    ) { enabled, signedIn -> enabled && signedIn }.distinctUntilChanged()

    private suspend fun tearDown() {
        work.cancelPolling()
        syncStore.clear()
        notifier.clearAll()
    }
}

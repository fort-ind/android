package com.fortind.zenith.notifications

import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.settings.SettingsStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first

class AppForegroundTracker {
    private val startedWindows = AtomicInteger(0)

    val inForeground: Boolean get() = startedWindows.get() > 0

    fun windowStarted() {
        startedWindows.incrementAndGet()
    }

    fun windowStopped(): Boolean = startedWindows.updateAndGet { (it - 1).coerceAtLeast(0) } == 0
}

enum class SyncOutcome { Done, Retry }

class NotificationSyncer(
    private val session: UserSessionService,
    private val api: NotificationsApi,
    private val settings: SettingsStore,
    private val syncStore: NotificationSyncStore,
    private val foreground: AppForegroundTracker,
    private val notifier: SystemNotifier,
) {
    suspend fun sync(): SyncOutcome {
        val current = settings.settings.first()
        if (!current.systemNotifications) return SyncOutcome.Done
        val token = session.token() ?: return SyncOutcome.Done
        return try {
            val page = api.fetch(token, null, null, SYNC_PAGE_SIZE)
            val baseline = syncStore.lastSeenMillis()
            val newest = page.maxOfOrNull { it.createdAtMillis } ?: return SyncOutcome.Done
            syncStore.advanceLastSeenMillis(newest)
            val unread = api.unreadCount(token)
            if (unread == 0) notifier.clearAll()
            if (baseline == 0L || foreground.inForeground) return SyncOutcome.Done
            val toShow = page
                .filter { it.createdAtMillis > baseline }
                .take(unread)
                .filter { it.type.category() in current.notificationCategories }
            if (toShow.isNotEmpty()) notifier.show(toShow, allowedActions())
            SyncOutcome.Done
        } catch (_: TokenRejectedException) {
            session.signOut()
            SyncOutcome.Done
        } catch (_: MissingPermissionException) {
            SyncOutcome.Done
        } catch (_: IOException) {
            SyncOutcome.Retry
        }
    }

    private suspend fun allowedActions(): Set<QuickAction> = buildSet {
        if (session.hasScope(SCOPE_WRITE_NOTES)) add(QuickAction.Reply)
        if (session.hasScope(SCOPE_WRITE_REACTIONS)) add(QuickAction.Like)
    }

    suspend fun acknowledgeSeen() {
        if (!settings.settings.first().systemNotifications) return
        val token = session.token() ?: return
        try {
            val newest = api.fetch(token, null, null, SYNC_PAGE_SIZE).maxOfOrNull { it.createdAtMillis }
            if (newest != null) syncStore.advanceLastSeenMillis(newest)
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val SYNC_PAGE_SIZE = 30
    }
}

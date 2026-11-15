package com.fortind.zenith.shortcuts

import android.util.Log
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.games.Game
import com.fortind.zenith.games.GameCatalog
import com.fortind.zenith.settings.AppIcon
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.xml.sax.SAXException

private const val TAG = "LauncherShortcuts"

class LauncherShortcuts(
    private val store: ShortcutStore,
    private val sessions: StateFlow<SessionState>,
    private val appIcon: StateFlow<AppIcon>,
    private val catalog: GameCatalog,
    private val scope: CoroutineScope,
    private val locales: () -> String,
    private val warn: (String, Throwable?) -> Unit = { message, error -> Log.w(TAG, message, error) },
) {
    private val mutex = Mutex()

    fun start() {
        scope.launch {
            combine(signedIn(), appIcon) { signedIn, icon -> signedIn to icon }
                .distinctUntilChanged()
                .collect { (signedIn, icon) -> sync(signedIn, icon) }
        }
        scope.launch {
            var previous: SessionState? = null
            sessions.collect { state ->
                if (previous == SessionState.Guest && state == SessionState.SignedOut) reportUsed(LauncherShortcut.SIGN_IN.id)
                previous = state
            }
        }
    }

    suspend fun refresh() {
        val state = sessions.value
        if (state != SessionState.Restoring) sync(state is SessionState.SignedIn, appIcon.value)
    }

    fun reportUsed(id: String) {
        scope.launch { guarded(Unit) { store.reportUsed(id) } }
    }

    fun canPin(): Boolean = guarded(false) { store.canPin() }

    fun requestPin(target: ShortcutTarget): Boolean = guarded(false) { store.requestPin(target, stamp(appIcon.value)) }

    private fun signedIn() = sessions
        .filter { it != SessionState.Restoring }
        .map { it is SessionState.SignedIn }
        .distinctUntilChanged()

    private fun stamp(icon: AppIcon) = ShortcutStamp(icon, locales())

    private suspend fun sync(signedIn: Boolean, icon: AppIcon) = mutex.withLock {
        try {
            val stamp = stamp(icon)
            val published = store.published()
            val games = if (needsGameCatalog(published)) loadGames() else null
            val changes = planShortcuts(published, LauncherShortcut.forSession(signedIn), stamp, games)
            if (changes.isEmpty) return@withLock
            if (changes.enable.isNotEmpty()) store.enable(changes.enable, stamp)
            changes.publish?.let { shortcuts ->
                if (!store.publish(shortcuts, stamp)) warn("Launcher shortcuts not published, rate limited", null)
            }
            if (changes.update.isNotEmpty() && !store.update(changes.update, stamp)) {
                warn("Pinned shortcuts not updated, rate limited", null)
            }
            if (changes.disableGames.isNotEmpty()) store.disableGames(changes.disableGames)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            warn("Could not sync launcher shortcuts", e)
        }
    }

    private suspend fun loadGames(): Map<String, Game>? = try {
        catalog.games().associateBy { it.url }
    } catch (e: IOException) {
        warn("Game catalog unavailable, pinned games left as they are", e)
        null
    } catch (e: SAXException) {
        warn("Game catalog unreadable, pinned games left as they are", e)
        null
    }

    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        warn("Shortcut call failed", e)
        fallback
    }
}

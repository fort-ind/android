package com.fortind.zenith.shortcuts

import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.games.Game
import com.fortind.zenith.games.GameCatalog
import com.fortind.zenith.games.GameCategory
import com.fortind.zenith.settings.AppIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherShortcutsTest {
    private val signedInUser = SessionState.SignedIn(user = null)
    private val slope = Game("Slope", GameCategory.HTML, "https://fort1nd.com/games/html/slope/")

    private class FakeStore : ShortcutStore {
        val shortcuts = mutableListOf<PublishedShortcut>()
        val publishes = mutableListOf<List<LauncherShortcut>>()
        val disabled = mutableListOf<List<String>>()
        val reported = mutableListOf<String>()
        var failNextRead: RuntimeException? = null

        override fun published(): List<PublishedShortcut> {
            failNextRead?.let {
                failNextRead = null
                throw it
            }
            return shortcuts.toList()
        }

        override fun enable(targets: List<ShortcutTarget>, stamp: ShortcutStamp) {
            replace(targets.map { it.id }) { it.copy(enabled = true) }
        }

        override fun publish(shortcuts: List<LauncherShortcut>, stamp: ShortcutStamp): Boolean {
            publishes += shortcuts
            val kept = this.shortcuts.filter { it.pinned }.map { it.copy(dynamic = false) }
            this.shortcuts.clear()
            this.shortcuts += kept
            shortcuts.forEachIndexed { rank, shortcut ->
                val existing = this.shortcuts.indexOfFirst { it.id == shortcut.id }
                val published = PublishedShortcut(shortcut.id, rank, stamp.value, dynamic = true, pinned = existing >= 0, enabled = true)
                if (existing >= 0) this.shortcuts[existing] = published else this.shortcuts += published
            }
            return true
        }

        override fun update(targets: List<ShortcutTarget>, stamp: ShortcutStamp): Boolean {
            replace(targets.map { it.id }) { it.copy(stamp = stamp.value) }
            return true
        }

        override fun disableGames(ids: List<String>) {
            disabled += ids
            replace(ids) { it.copy(enabled = false) }
        }

        override fun reportUsed(id: String) {
            reported += id
        }

        override fun canPin(): Boolean = true

        override fun requestPin(target: ShortcutTarget, stamp: ShortcutStamp): Boolean = true

        private fun replace(ids: List<String>, change: (PublishedShortcut) -> PublishedShortcut) {
            shortcuts.replaceAll { if (it.id in ids) change(it) else it }
        }
    }

    private class CountingCatalog(private val games: List<Game>) : GameCatalog {
        var loads = 0
        override suspend fun games(): List<Game> {
            loads++
            return games
        }
    }

    private class Harness(
        test: TestScope,
        val store: FakeStore = FakeStore(),
        val catalog: CountingCatalog = CountingCatalog(emptyList()),
    ) {
        val sessions = MutableStateFlow<SessionState>(SessionState.Restoring)
        val icon = MutableStateFlow(AppIcon.DEFAULT)
        var locales = "en-GB"
        val warnings = mutableListOf<String>()
        val shortcuts = LauncherShortcuts(
            store = store,
            sessions = sessions,
            appIcon = icon,
            catalog = catalog,
            scope = CoroutineScope(test.backgroundScope.coroutineContext + UnconfinedTestDispatcher(test.testScheduler)),
            locales = { locales },
            warn = { message, _ -> warnings += message },
        )
    }

    @Test
    fun nothingIsPublishedWhileTheSessionRestores() = runTest {
        val harness = Harness(this)
        harness.shortcuts.start()

        assertTrue(harness.store.publishes.isEmpty())
    }

    @Test
    fun theMenuFollowsTheSession() = runTest {
        val harness = Harness(this)
        harness.shortcuts.start()

        harness.sessions.value = signedInUser
        harness.sessions.value = SessionState.Guest
        harness.sessions.value = SessionState.SignedOut
        harness.sessions.value = signedInUser

        assertEquals(
            listOf(LauncherShortcut.forSession(true), LauncherShortcut.forSession(false), LauncherShortcut.forSession(true)),
            harness.store.publishes,
        )
    }

    @Test
    fun anUnchangedMenuIsNotRepublishedOnTheNextStart() = runTest {
        val store = FakeStore()
        Harness(this, store).apply { shortcuts.start(); sessions.value = signedInUser }
        Harness(this, store).apply { shortcuts.start(); sessions.value = signedInUser }

        assertEquals(1, store.publishes.size)
    }

    @Test
    fun switchingTheAppIconRepublishesForTheNewAlias() = runTest {
        val harness = Harness(this)
        harness.shortcuts.start()
        harness.sessions.value = signedInUser

        harness.icon.value = AppIcon.FALL

        assertEquals(2, harness.store.publishes.size)
        assertTrue(harness.store.shortcuts.all { it.stamp == ShortcutStamp(AppIcon.FALL, "en-GB").value })
    }

    @Test
    fun refreshPicksUpALocaleChange() = runTest {
        val harness = Harness(this)
        harness.shortcuts.start()
        harness.sessions.value = signedInUser

        harness.locales = "fr-FR"
        harness.shortcuts.refresh()

        assertEquals(2, harness.store.publishes.size)
    }

    @Test
    fun leavingGuestModeCountsAsSignIn() = runTest {
        val harness = Harness(this)
        harness.shortcuts.start()

        harness.sessions.value = SessionState.Guest
        harness.sessions.value = SessionState.SignedOut

        assertEquals(listOf(LauncherShortcut.SIGN_IN.id), harness.store.reported)
    }

    @Test
    fun theCatalogIsReadOnlyWhenAGameIsPinned() = runTest {
        val store = FakeStore()
        val catalog = CountingCatalog(listOf(slope))
        val harness = Harness(this, store, catalog)
        harness.shortcuts.start()
        harness.sessions.value = signedInUser
        assertEquals(0, catalog.loads)

        val gone = ShortcutTarget.gameId("https://fort1nd.com/games/html/gone/")
        store.shortcuts += PublishedShortcut(gone, 0, null, dynamic = false, pinned = true, enabled = true)
        harness.shortcuts.refresh()

        assertEquals(1, catalog.loads)
        assertEquals(listOf(listOf(gone)), store.disabled)
    }

    @Test
    fun aFailingStoreIsLoggedAndTheNextSyncStillRuns() = runTest {
        val harness = Harness(this)
        harness.store.failNextRead = IllegalStateException("user locked")
        harness.shortcuts.start()

        harness.sessions.value = signedInUser
        harness.shortcuts.refresh()

        assertEquals(listOf("Could not sync launcher shortcuts"), harness.warnings)
        assertEquals(1, harness.store.publishes.size)
    }
}

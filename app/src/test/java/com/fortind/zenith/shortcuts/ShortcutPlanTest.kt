package com.fortind.zenith.shortcuts

import com.fortind.zenith.games.Game
import com.fortind.zenith.games.GameCategory
import com.fortind.zenith.settings.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPlanTest {
    private val stamp = ShortcutStamp(AppIcon.DEFAULT, "en-GB")
    private val signedIn = LauncherShortcut.forSession(signedIn = true)
    private val slope = Game("Slope", GameCategory.HTML, "https://fort1nd.com/games/html/slope/")
    private val catalog = mapOf(slope.url to slope)

    private fun dynamic(shortcuts: List<LauncherShortcut>, stampValue: String = stamp.value) =
        shortcuts.mapIndexed { rank, shortcut -> PublishedShortcut(shortcut.id, rank, stampValue, dynamic = true, pinned = false, enabled = true) }

    private fun pinned(id: String, stampValue: String? = stamp.value, enabled: Boolean = true) =
        PublishedShortcut(id, 0, stampValue, dynamic = false, pinned = true, enabled = enabled)

    @Test
    fun firstRunPublishesTheMenuAndNothingElse() {
        val changes = planShortcuts(emptyList(), signedIn, stamp, games = null)

        assertEquals(signedIn, changes.publish)
        assertTrue(changes.enable.isEmpty() && changes.update.isEmpty() && changes.disableGames.isEmpty())
    }

    @Test
    fun aCurrentMenuIsLeftAlone() {
        assertTrue(planShortcuts(dynamic(signedIn), signedIn, stamp, games = null).isEmpty)
    }

    @Test
    fun aDifferentLocaleOrIconRepublishes() {
        val published = dynamic(signedIn)

        assertEquals(signedIn, planShortcuts(published, signedIn, stamp.copy(locales = "fr-FR"), null).publish)
        assertEquals(signedIn, planShortcuts(published, signedIn, stamp.copy(appIcon = AppIcon.FALL), null).publish)
    }

    @Test
    fun signingOutSwapsTheMenu() {
        val signedOut = LauncherShortcut.forSession(signedIn = false)

        assertEquals(signedOut, planShortcuts(dynamic(signedIn), signedOut, stamp, null).publish)
    }

    @Test
    fun aReorderedMenuRepublishes() {
        assertEquals(signedIn, planShortcuts(dynamic(signedIn.reversed()), signedIn, stamp, null).publish)
    }

    @Test
    fun aPinnedGameThatLeftTheCatalogIsDisabledOnce() {
        val gone = ShortcutTarget.gameId("https://fort1nd.com/games/html/gone/")
        val published = dynamic(signedIn) + pinned(gone)

        assertEquals(listOf(gone), planShortcuts(published, signedIn, stamp, catalog).disableGames)
        assertTrue(planShortcuts(dynamic(signedIn) + pinned(gone, enabled = false), signedIn, stamp, catalog).isEmpty)
    }

    @Test
    fun aDisabledGameThatIsBackIsEnabledAndRefreshed() {
        val target = ShortcutTarget.PinnedGame(slope)
        val changes = planShortcuts(dynamic(signedIn) + pinned(target.id, enabled = false), signedIn, stamp, catalog)

        assertEquals(listOf<ShortcutTarget>(target), changes.enable)
        assertEquals(listOf<ShortcutTarget>(target), changes.update)
    }

    @Test
    fun pinnedGamesAreLeftAloneWithoutACatalog() {
        val published = dynamic(signedIn) + pinned(ShortcutTarget.gameId(slope.url), stampValue = "old")

        assertTrue(planShortcuts(published, signedIn, stamp, games = null).isEmpty)
    }

    @Test
    fun aStalePinnedTagIsUpdated() {
        val changes = planShortcuts(dynamic(signedIn) + pinned(ShortcutTarget.tagId("photography"), stampValue = "old"), signedIn, stamp, null)

        assertEquals(listOf<ShortcutTarget>(ShortcutTarget.PinnedTag("photography")), changes.update)
        assertNull(changes.publish)
    }

    @Test
    fun pinnedShortcutsAreRefreshedWhenNoMenuExistsAsAfterARestore() {
        val changes = planShortcuts(listOf(pinned(ShortcutTarget.tagId("photography"))), signedIn, stamp, null)

        assertEquals(listOf<ShortcutTarget>(ShortcutTarget.PinnedTag("photography")), changes.update)
        assertEquals(signedIn, changes.publish)
    }

    @Test
    fun aPinnedMenuShortcutThatIsRepublishedIsNotUpdatedTwice() {
        val published = dynamic(signedIn, stampValue = "old").map { if (it.id == LauncherShortcut.NEW_POST.id) it.copy(pinned = true) else it }

        val changes = planShortcuts(published, signedIn, stamp, null)

        assertEquals(signedIn, changes.publish)
        assertTrue(changes.update.isEmpty())
    }

    @Test
    fun aPinnedMenuShortcutDisabledByAnIconSwitchIsEnabledBeforeRepublishing() {
        val published = listOf(pinned(LauncherShortcut.NEW_POST.id, stampValue = "old", enabled = false))

        val changes = planShortcuts(published, signedIn, stamp, null)

        assertEquals(listOf<ShortcutTarget>(ShortcutTarget.Menu(LauncherShortcut.NEW_POST)), changes.enable)
        assertEquals(signedIn, changes.publish)
        assertTrue(changes.update.isEmpty())
    }

    @Test
    fun aPinnedMenuShortcutOutsideTheCurrentMenuIsUpdatedOnItsOwn() {
        val signedOut = LauncherShortcut.forSession(signedIn = false)
        val published = dynamic(signedOut) + pinned(LauncherShortcut.NEW_POST.id, stampValue = "old")

        val changes = planShortcuts(published, signedOut, stamp, null)

        assertNull(changes.publish)
        assertEquals(listOf<ShortcutTarget>(ShortcutTarget.Menu(LauncherShortcut.NEW_POST)), changes.update)
    }

    @Test
    fun unknownIdsAreNeverTouched() {
        val published = dynamic(signedIn) + pinned("someone_elses", stampValue = null, enabled = false)

        assertTrue(planShortcuts(published, signedIn, stamp, catalog).isEmpty)
    }

    @Test
    fun theCatalogIsNeededOnlyForPinnedGames() {
        assertFalse(needsGameCatalog(dynamic(signedIn) + pinned(ShortcutTarget.tagId("photography"))))
        assertTrue(needsGameCatalog(listOf(pinned(ShortcutTarget.gameId(slope.url)))))
    }

    @Test
    fun idsResolveToTheirTargets() {
        assertEquals(ResolvedShortcut.Known(ShortcutTarget.Menu(LauncherShortcut.SEARCH)), resolveShortcut("search", null))
        assertEquals(ResolvedShortcut.Known(ShortcutTarget.PinnedTag("a:b")), resolveShortcut("tag:a:b", null))
        assertEquals(ResolvedShortcut.Known(ShortcutTarget.PinnedGame(slope)), resolveShortcut(ShortcutTarget.gameId(slope.url), catalog))
        assertEquals(ResolvedShortcut.GameGone, resolveShortcut("game:https://elsewhere.example/", catalog))
        assertEquals(ResolvedShortcut.Unknown, resolveShortcut("tag:", null))
        assertEquals(ResolvedShortcut.Unknown, resolveShortcut("game:", catalog))
        assertEquals(ResolvedShortcut.Unknown, resolveShortcut("game:${slope.url}", null))
    }
}

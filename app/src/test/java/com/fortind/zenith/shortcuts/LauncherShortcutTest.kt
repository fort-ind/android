package com.fortind.zenith.shortcuts

import com.fortind.zenith.ui.nav.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShortcutTest {
    @Test
    fun signedInMenuMirrorsTheWidget() {
        assertEquals(
            listOf(LauncherShortcut.NEW_POST, LauncherShortcut.SEARCH, LauncherShortcut.NOTIFICATIONS, LauncherShortcut.PROFILE),
            LauncherShortcut.forSession(signedIn = true),
        )
    }

    @Test
    fun signedOutMenuOffersSearchGamesAndSignIn() {
        assertEquals(
            listOf(LauncherShortcut.SEARCH, LauncherShortcut.GAMES, LauncherShortcut.SIGN_IN),
            LauncherShortcut.forSession(signedIn = false),
        )
    }

    @Test
    fun noSessionPublishesMoreThanFourShortcuts() {
        assertTrue(LauncherShortcut.forSession(signedIn = true).size <= 4)
        assertTrue(LauncherShortcut.forSession(signedIn = false).size <= 4)
    }

    @Test
    fun everyIdRoundTrips() {
        LauncherShortcut.entries.forEach { assertEquals(it, LauncherShortcut.fromId(it.id)) }
        assertNull(LauncherShortcut.fromId("NEW_POST"))
    }

    @Test
    fun onlyTabsWithAShortcutReportOne() {
        assertEquals(LauncherShortcut.NOTIFICATIONS, LauncherShortcut.forDestination(AppDestination.NOTIFICATIONS))
        assertEquals(LauncherShortcut.PROFILE, LauncherShortcut.forDestination(AppDestination.PROFILE))
        assertEquals(LauncherShortcut.GAMES, LauncherShortcut.forDestination(AppDestination.GAMES))
        assertNull(LauncherShortcut.forDestination(AppDestination.HOME))
        assertNull(LauncherShortcut.forDestination(AppDestination.SETTINGS))
    }
}

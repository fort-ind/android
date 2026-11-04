package com.fortind.zenith.ui.nav

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppShortcutsTest {

    @Test
    fun ctrlDigitsFollowTheNavigationOrder() {
        AppDestination.entries.forEach { destination ->
            assertEquals(AppShortcut.Destination(destination), shortcutFor(destinationKeyCode(destination), command = true))
        }
    }

    @Test
    fun plainDigitsAndLettersAreLeftForTyping() {
        assertNull(shortcutFor(KeyEvent.KEYCODE_1, command = false))
        assertNull(shortcutFor(KeyEvent.KEYCODE_N, command = false))
        assertNull(shortcutFor(KeyEvent.KEYCODE_R, command = false))
        assertNull(shortcutFor(KeyEvent.KEYCODE_F, command = false))
        assertEquals(AppShortcut.Find, shortcutFor(KeyEvent.KEYCODE_F, command = true))
    }

    @Test
    fun commandShortcuts() {
        assertEquals(AppShortcut.NewPost, shortcutFor(KeyEvent.KEYCODE_N, command = true))
        assertEquals(AppShortcut.Refresh, shortcutFor(KeyEvent.KEYCODE_R, command = true))
        assertEquals(AppShortcut.Refresh, shortcutFor(KeyEvent.KEYCODE_F5, command = false))
        assertEquals(AppShortcut.Dismiss, shortcutFor(KeyEvent.KEYCODE_ESCAPE, command = false))
    }

    @Test
    fun ctrlTabMovesBetweenPageTabs() {
        assertEquals(AppShortcut.MoveTab(1), shortcutFor(KeyEvent.KEYCODE_TAB, command = true))
        assertEquals(AppShortcut.MoveTab(-1), shortcutFor(KeyEvent.KEYCODE_TAB, command = true, shift = true))
        assertNull(shortcutFor(KeyEvent.KEYCODE_TAB, command = false))
    }

    @Test
    fun otherCommandKeysAreNotClaimed() {
        assertNull(shortcutFor(KeyEvent.KEYCODE_C, command = true))
        assertNull(shortcutFor(KeyEvent.KEYCODE_6, command = true))
        assertNull(shortcutFor(KeyEvent.KEYCODE_ESCAPE, command = true))
    }
}

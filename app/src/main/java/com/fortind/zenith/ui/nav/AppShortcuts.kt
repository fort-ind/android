package com.fortind.zenith.ui.nav

import android.view.KeyEvent

sealed interface AppShortcut {
    data class Destination(val destination: AppDestination) : AppShortcut

    data object NewPost : AppShortcut

    data object Find : AppShortcut

    data object Refresh : AppShortcut

    data object Dismiss : AppShortcut

    data class MoveTab(val step: Int) : AppShortcut
}

private val DestinationKeyCodes = listOf(
    KeyEvent.KEYCODE_1,
    KeyEvent.KEYCODE_2,
    KeyEvent.KEYCODE_3,
    KeyEvent.KEYCODE_4,
    KeyEvent.KEYCODE_5,
)

fun destinationKeyCode(destination: AppDestination): Int = DestinationKeyCodes[destination.ordinal]

fun shortcutFor(keyCode: Int, command: Boolean, shift: Boolean = false): AppShortcut? = when {
    keyCode == KeyEvent.KEYCODE_ESCAPE && !command -> AppShortcut.Dismiss
    keyCode == KeyEvent.KEYCODE_F5 && !command -> AppShortcut.Refresh
    !command -> null
    keyCode == KeyEvent.KEYCODE_N -> AppShortcut.NewPost
    keyCode == KeyEvent.KEYCODE_R -> AppShortcut.Refresh
    keyCode == KeyEvent.KEYCODE_F -> AppShortcut.Find
    keyCode == KeyEvent.KEYCODE_TAB -> AppShortcut.MoveTab(if (shift) -1 else 1)
    else -> DestinationKeyCodes.indexOf(keyCode).takeIf { it >= 0 }?.let { AppShortcut.Destination(AppDestination.entries[it]) }
}

class AppShortcutDispatcher {
    @Volatile
    var handler: ((AppShortcut) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val shortcut = shortcutFor(event.keyCode, event.isCtrlPressed || event.isMetaPressed, event.isShiftPressed) ?: return false
        return handler?.invoke(shortcut) == true
    }
}

package com.fortind.zenith.shortcuts

interface ShortcutStore {
    fun published(): List<PublishedShortcut>
    fun enable(targets: List<ShortcutTarget>, stamp: ShortcutStamp)
    fun publish(shortcuts: List<LauncherShortcut>, stamp: ShortcutStamp): Boolean
    fun update(targets: List<ShortcutTarget>, stamp: ShortcutStamp): Boolean
    fun disableGames(ids: List<String>)
    fun reportUsed(id: String)
    fun canPin(): Boolean
    fun requestPin(target: ShortcutTarget, stamp: ShortcutStamp): Boolean
}

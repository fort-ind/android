package com.fortind.zenith.shortcuts

import com.fortind.zenith.games.Game
import com.fortind.zenith.settings.AppIcon

const val SHORTCUT_REVISION = 1

data class ShortcutStamp(val appIcon: AppIcon, val locales: String) {
    val value: String get() = "$SHORTCUT_REVISION|$locales|${appIcon.name}"
}

data class PublishedShortcut(
    val id: String,
    val rank: Int,
    val stamp: String?,
    val dynamic: Boolean,
    val pinned: Boolean,
    val enabled: Boolean,
)

data class ShortcutChanges(
    val enable: List<ShortcutTarget> = emptyList(),
    val publish: List<LauncherShortcut>? = null,
    val update: List<ShortcutTarget> = emptyList(),
    val disableGames: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = enable.isEmpty() && publish == null && update.isEmpty() && disableGames.isEmpty()
}

fun needsGameCatalog(published: List<PublishedShortcut>): Boolean =
    published.any { it.pinned && ShortcutTarget.isGameId(it.id) }

fun planShortcuts(
    published: List<PublishedShortcut>,
    wanted: List<LauncherShortcut>,
    stamp: ShortcutStamp,
    games: Map<String, Game>?,
): ShortcutChanges {
    val dynamic = published.filter { it.dynamic }.sortedBy { it.rank }
    val dynamicCurrent = dynamic.map { it.id } == wanted.map { it.id } && dynamic.all { it.stamp == stamp.value }
    val iconsMayBeLost = dynamic.isEmpty()
    val republished = if (dynamicCurrent) emptySet() else wanted.map { it.id }.toSet()
    val enable = mutableListOf<ShortcutTarget>()
    val update = mutableListOf<ShortcutTarget>()
    val disable = mutableListOf<String>()
    published.filter { it.pinned }.forEach { shortcut ->
        when (val resolved = resolveShortcut(shortcut.id, games)) {
            is ResolvedShortcut.Known -> {
                if (!shortcut.enabled) enable += resolved.target
                val stale = !shortcut.enabled || iconsMayBeLost || shortcut.stamp != stamp.value
                if (stale && shortcut.id !in republished) update += resolved.target
            }
            ResolvedShortcut.GameGone -> if (shortcut.enabled) disable += shortcut.id
            ResolvedShortcut.Unknown -> Unit
        }
    }
    return ShortcutChanges(
        enable = enable,
        publish = wanted.takeUnless { dynamicCurrent },
        update = update,
        disableGames = disable,
    )
}

package com.fortind.zenith.shortcuts

import com.fortind.zenith.games.Game

private const val GAME_PREFIX = "game:"
private const val TAG_PREFIX = "tag:"

sealed interface ShortcutTarget {
    val id: String

    data class Menu(val shortcut: LauncherShortcut) : ShortcutTarget {
        override val id: String get() = shortcut.id
    }

    data class PinnedGame(val game: Game) : ShortcutTarget {
        override val id: String get() = gameId(game.url)
    }

    data class PinnedTag(val tag: String) : ShortcutTarget {
        override val id: String get() = tagId(tag)
    }

    companion object {
        fun gameId(url: String): String = GAME_PREFIX + url

        fun tagId(tag: String): String = TAG_PREFIX + tag

        fun isGameId(id: String): Boolean = id.startsWith(GAME_PREFIX)
    }
}

sealed interface ResolvedShortcut {
    data class Known(val target: ShortcutTarget) : ResolvedShortcut
    data object GameGone : ResolvedShortcut
    data object Unknown : ResolvedShortcut
}

fun resolveShortcut(id: String, games: Map<String, Game>?): ResolvedShortcut {
    LauncherShortcut.fromId(id)?.let { return ResolvedShortcut.Known(ShortcutTarget.Menu(it)) }
    if (id.startsWith(TAG_PREFIX)) {
        val tag = id.removePrefix(TAG_PREFIX)
        return if (tag.isEmpty()) ResolvedShortcut.Unknown else ResolvedShortcut.Known(ShortcutTarget.PinnedTag(tag))
    }
    if (ShortcutTarget.isGameId(id)) {
        val url = id.removePrefix(GAME_PREFIX)
        if (url.isEmpty() || games == null) return ResolvedShortcut.Unknown
        val game = games[url] ?: return ResolvedShortcut.GameGone
        return ResolvedShortcut.Known(ShortcutTarget.PinnedGame(game))
    }
    return ResolvedShortcut.Unknown
}

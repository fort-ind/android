package com.fortind.zenith.shortcuts

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fortind.zenith.R
import com.fortind.zenith.ui.nav.AppDestination

enum class LauncherShortcut(
    val id: String,
    @param:StringRes val shortLabel: Int,
    @param:StringRes val longLabel: Int,
    @param:DrawableRes val icon: Int,
) {
    NEW_POST("new_post", R.string.shortcut_new_post_short, R.string.shortcut_new_post_long, R.drawable.ic_shortcut_post),
    SEARCH("search", R.string.shortcut_search_short, R.string.shortcut_search_long, R.drawable.ic_shortcut_search),
    NOTIFICATIONS("notifications", R.string.shortcut_notifications_short, R.string.shortcut_notifications_long, R.drawable.ic_shortcut_notifications),
    PROFILE("profile", R.string.shortcut_profile_short, R.string.shortcut_profile_long, R.drawable.ic_shortcut_profile),
    GAMES("games", R.string.shortcut_games_short, R.string.shortcut_games_long, R.drawable.ic_shortcut_games),
    SIGN_IN("sign_in", R.string.shortcut_sign_in_short, R.string.shortcut_sign_in_long, R.drawable.ic_shortcut_sign_in),
    ;

    companion object {
        private val SignedIn = listOf(NEW_POST, SEARCH, NOTIFICATIONS, PROFILE)
        private val SignedOut = listOf(SEARCH, GAMES, SIGN_IN)

        fun forSession(signedIn: Boolean): List<LauncherShortcut> = if (signedIn) SignedIn else SignedOut

        fun forDestination(destination: AppDestination): LauncherShortcut? = when (destination) {
            AppDestination.NOTIFICATIONS -> NOTIFICATIONS
            AppDestination.PROFILE -> PROFILE
            AppDestination.GAMES -> GAMES
            AppDestination.HOME, AppDestination.SETTINGS -> null
        }

        fun fromId(id: String): LauncherShortcut? = entries.firstOrNull { it.id == id }
    }
}

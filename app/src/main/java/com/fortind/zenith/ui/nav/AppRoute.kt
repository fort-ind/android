package com.fortind.zenith.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.fortind.zenith.R
import com.fortind.zenith.people.FollowListKind
import com.fortind.zenith.ui.icons.GamesIcon

enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Default.Home),
    GAMES(R.string.nav_games, GamesIcon),
    NOTIFICATIONS(R.string.nav_notifications, Icons.Default.Notifications),
    PROFILE(R.string.nav_profile, Icons.Default.AccountCircle),
    SETTINGS(R.string.nav_settings, Icons.Default.Settings),
    ;

    val root: AppRoute
        get() = when (this) {
            HOME -> AppRoute.Home
            GAMES -> AppRoute.Games
            NOTIFICATIONS -> AppRoute.Notifications
            PROFILE -> AppRoute.Profile
            SETTINGS -> AppRoute.Settings()
        }
}

sealed interface AppRoute {
    fun encode(): String

    val isDetailPage: Boolean
        get() = this is Thread || this is Person || this is PersonHandle || this is FollowList || this is Tag || this is Search || this is Hashtags

    val isListPane: Boolean
        get() = this is Home || this is Notifications || this is Profile || this is Person || this is PersonHandle || this is Tag || this is Search

    data object Home : AppRoute {
        override fun encode() = "home"
    }

    data object Games : AppRoute {
        override fun encode() = "games"
    }

    data object Notifications : AppRoute {
        override fun encode() = "notifications"
    }

    data object Profile : AppRoute {
        override fun encode() = "profile"
    }

    data class Settings(val openEditProfile: Boolean = false) : AppRoute {
        override fun encode() = if (openEditProfile) "settings:edit" else "settings"
    }

    data class Thread(val noteId: String) : AppRoute {
        override fun encode() = "$THREAD_PREFIX$noteId"
    }

    data class Person(val userId: String) : AppRoute {
        override fun encode() = "$PERSON_PREFIX$userId"
    }

    data class PersonHandle(val username: String, val host: String?) : AppRoute {
        override fun encode() = "$HANDLE_PREFIX$username@${host.orEmpty()}"
    }

    data class FollowList(val userId: String, val kind: FollowListKind) : AppRoute {
        override fun encode() = "$FOLLOWS_PREFIX${kind.name}:$userId"
    }

    data class Tag(val tag: String) : AppRoute {
        override fun encode() = "$TAG_PREFIX$tag"
    }

    data class Search(val query: String) : AppRoute {
        override fun encode() = "$SEARCH_PREFIX$query"
    }

    data object Hashtags : AppRoute {
        override fun encode() = "hashtags"
    }

    companion object {
        private const val TAG_PREFIX = "tag:"
        private const val SEARCH_PREFIX = "search:"
        private const val THREAD_PREFIX = "thread:"
        private const val PERSON_PREFIX = "person:"
        private const val HANDLE_PREFIX = "handle:"
        private const val FOLLOWS_PREFIX = "follows:"
        private val Handle = Regex("([A-Za-z0-9_.-]{1,128})@([A-Za-z0-9.-]{0,253})")
        private val NoteId = Regex("[A-Za-z0-9]{1,64}")

        fun isValidNoteId(noteId: String): Boolean = NoteId.matches(noteId)

        fun decode(encoded: String): AppRoute? = when {
            encoded == "home" -> Home
            encoded == "games" -> Games
            encoded == "notifications" -> Notifications
            encoded == "profile" -> Profile
            encoded == "settings" -> Settings()
            encoded == "settings:edit" -> Settings(openEditProfile = true)
            encoded == "hashtags" -> Hashtags
            encoded.startsWith(TAG_PREFIX) -> encoded.removePrefix(TAG_PREFIX).takeIf(String::isNotEmpty)?.let(::Tag)
            encoded.startsWith(SEARCH_PREFIX) -> encoded.removePrefix(SEARCH_PREFIX).takeIf(String::isNotEmpty)?.let(::Search)
            encoded.startsWith(THREAD_PREFIX) -> encoded.removePrefix(THREAD_PREFIX).takeIf(::isValidNoteId)?.let(::Thread)
            encoded.startsWith(PERSON_PREFIX) -> encoded.removePrefix(PERSON_PREFIX).takeIf(::isValidNoteId)?.let(::Person)
            encoded.startsWith(HANDLE_PREFIX) -> Handle.matchEntire(encoded.removePrefix(HANDLE_PREFIX))?.let { match ->
                PersonHandle(match.groupValues[1], match.groupValues[2].takeIf(String::isNotEmpty))
            }
            encoded.startsWith(FOLLOWS_PREFIX) -> decodeFollowList(encoded.removePrefix(FOLLOWS_PREFIX))
            else -> null
        }

        private fun decodeFollowList(encoded: String): FollowList? {
            val kind = FollowListKind.entries.firstOrNull { it.name == encoded.substringBefore(':') } ?: return null
            val userId = encoded.substringAfter(':').takeIf(::isValidNoteId) ?: return null
            return FollowList(userId, kind)
        }
    }
}

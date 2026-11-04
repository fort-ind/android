package com.fortind.zenith.ui.people

import java.net.URLDecoder
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.people.UserDetail
import com.fortind.zenith.timeline.NoteUser

class PeopleActions(
    val toggleFollow: (UserDetail) -> Unit,
    val moderate: (UserDetail, ModerationAction) -> Unit,
    val report: (UserDetail) -> Unit,
    val addToList: (NoteUser) -> Unit,
    val openInBrowser: (String) -> Unit,
    val copyLink: (NoteUser) -> Unit,
    val share: (NoteUser) -> Unit,
)

class PageKeyTarget(val onRefresh: () -> Unit, val onMoveTab: (Int) -> Unit)

@Stable
class PageKeys {
    var target: PageKeyTarget? by mutableStateOf(null)
    var bookmarksRequested: Boolean by mutableStateOf(false)
}

data class ProfileHandle(val username: String, val host: String?)

private val ProfileLink = Regex("https://([A-Za-z0-9.-]+)/@([A-Za-z0-9_.-]{1,128})(?:@([A-Za-z0-9.-]{1,253}))?/?")

private val TagLink = Regex("https://([A-Za-z0-9.-]+)/tags/([^/?#]+)/?")

fun parseTagLink(uri: String): String? {
    val match = TagLink.matchEntire(uri) ?: return null
    val (linkHost, tag) = match.destructured
    if (!linkHost.equals(FortInstance.HOST, ignoreCase = true)) return null
    return try {
        URLDecoder.decode(tag, "UTF-8").takeIf(String::isNotEmpty)
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun parseProfileLink(uri: String): ProfileHandle? {
    val match = ProfileLink.matchEntire(uri) ?: return null
    val (linkHost, username, handleHost) = match.destructured
    if (!linkHost.equals(FortInstance.HOST, ignoreCase = true)) return null
    return ProfileHandle(username, handleHost.takeIf { it.isNotEmpty() && !it.equals(FortInstance.HOST, ignoreCase = true) })
}

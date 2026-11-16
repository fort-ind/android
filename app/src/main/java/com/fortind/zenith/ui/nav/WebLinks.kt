package com.fortind.zenith.ui.nav

import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.ui.people.parseProfileLink
import com.fortind.zenith.ui.people.parseTagLink
import java.net.URLEncoder

private val NoteLink = Regex("https://([A-Za-z0-9.-]+)/notes/([A-Za-z0-9]+)/?")

fun parseNoteLink(uri: String): String? {
    val match = NoteLink.matchEntire(uri) ?: return null
    val (host, noteId) = match.destructured
    if (!host.equals(FortInstance.HOST, ignoreCase = true)) return null
    return noteId.takeIf(AppRoute::isValidNoteId)
}

fun appRouteForWebLink(uri: String): AppRoute? {
    val clean = uri.substringBefore('#').substringBefore('?')
    parseNoteLink(clean)?.let { return AppRoute.Thread(it) }
    parseTagLink(clean)?.let { return AppRoute.Tag(it) }
    parseProfileLink(clean)?.let { return AppRoute.PersonHandle(it.username, it.host) }
    return null
}

fun webUrlFor(route: AppRoute?): String? {
    val base = "https://${FortInstance.HOST}"
    return when (route) {
        is AppRoute.Thread -> "$base/notes/${route.noteId}"
        is AppRoute.PersonHandle -> if (route.host == null) "$base/@${route.username}" else "$base/@${route.username}@${route.host}"
        is AppRoute.Tag -> "$base/tags/${encodePart(route.tag)}"
        is AppRoute.Search -> "$base/search?q=${encodePart(route.query)}"
        AppRoute.Hashtags -> "$base/explore"
        AppRoute.Notifications -> "$base/my/notifications"
        is AppRoute.Settings -> "$base/settings"
        AppRoute.Games -> null
        else -> base
    }
}

private fun encodePart(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

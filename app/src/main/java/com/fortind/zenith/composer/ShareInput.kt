package com.fortind.zenith.composer

private const val MAX_SHARED_TEXT = 20_000
private const val CONTENT_SCHEME = "content"
private val WebLink = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

data class SharedContent(
    val text: String,
    val caretAtStart: Boolean,
    val uris: List<String>,
    val refusedFiles: Boolean = false,
)

data class SharedUri(val value: String, val scheme: String?, val authority: String?)

fun sharedContent(text: String?, subject: String?, uris: List<SharedUri>, ownPackage: String): SharedContent {
    val body = text.normalized()
    val title = subject.normalized()
    val accepted = uris.filter { isAcceptableShareUri(it, ownPackage) }.map(SharedUri::value).distinct().take(MAX_ATTACHMENTS + 1)
    val refused = uris.any { !isAcceptableShareUri(it, ownPackage) }
    return when {
        WebLink.matches(body) -> {
            val heading = title.takeIf { it.isNotEmpty() && it != body }
            SharedContent(listOfNotNull("", heading, body).joinToString("\n"), caretAtStart = true, uris = accepted, refusedFiles = refused)
        }
        body.isNotEmpty() -> SharedContent(body, caretAtStart = false, uris = accepted, refusedFiles = refused)
        else -> SharedContent(title, caretAtStart = false, uris = accepted, refusedFiles = refused)
    }
}

fun isAcceptableShareUri(uri: SharedUri, ownPackage: String): Boolean {
    if (uri.scheme != CONTENT_SCHEME) return false
    val authority = uri.authority?.substringAfterLast('@').orEmpty()
    return authority.isNotEmpty() && authority != ownPackage && !authority.startsWith("$ownPackage.")
}

fun isPostableMime(mimeType: String): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("video/")

private fun String?.normalized(): String =
    orEmpty().replace("\r\n", "\n").replace('\r', '\n').trim().take(MAX_SHARED_TEXT)

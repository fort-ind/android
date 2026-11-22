package com.fortind.zenith.ui.mfm

import androidx.compose.runtime.staticCompositionLocalOf
import java.net.URI
import java.net.URISyntaxException

data class DeceptiveLink(val url: String, val shownHost: String, val realHost: String)

val LocalDeceptiveLinks = staticCompositionLocalOf<((DeceptiveLink) -> Unit)?> { null }

private val AddressLikeLabel =
    Regex("""^(?:https?://)?((?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,63})\.?(?::\d{1,5})?(?:[/?#]\S*)?$""", RegexOption.IGNORE_CASE)

fun deceptiveLink(label: String, url: String): DeceptiveLink? {
    val shown = shownHost(label) ?: return null
    val real = hostOf(url) ?: return null
    if (sameSite(shown, real)) return null
    return DeceptiveLink(url, shown, real)
}

internal fun shownHost(label: String): String? {
    val trimmed = label.trim()
    if (trimmed.length > MAX_LABEL_LENGTH) return null
    return AddressLikeLabel.matchEntire(trimmed)?.groupValues?.get(1)?.lowercase()?.removePrefix(WWW)
}

internal fun hostOf(url: String): String? = try {
    URI(url).host?.lowercase()?.trimEnd('.')?.removePrefix(WWW)?.takeIf { it.isNotEmpty() }
} catch (_: URISyntaxException) {
    null
}

private fun sameSite(shown: String, real: String): Boolean =
    shown == real || real.endsWith(".$shown") || shown.endsWith(".$real")

private const val WWW = "www."
private const val MAX_LABEL_LENGTH = 2_048

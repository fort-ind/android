package com.fortind.zenith.timeline

import com.fortind.zenith.mfm.MfmContainer
import com.fortind.zenith.mfm.MfmLink
import com.fortind.zenith.mfm.MfmNode
import com.fortind.zenith.mfm.MfmUrl
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.optStringOrNull
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

data class LinkPreview(
    val url: String,
    val title: String,
    val description: String?,
    val siteName: String?,
    val thumbnailUrl: String?,
    val sensitive: Boolean,
)

sealed interface LinkPreviewState {
    data object Unknown : LinkPreviewState

    data object None : LinkPreviewState

    data class Ready(val preview: LinkPreview) : LinkPreviewState
}

private fun String.isWebUrl(): Boolean = startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

private fun hostOf(url: String): String? = try {
    URI(url).host
} catch (_: URISyntaxException) {
    null
}

fun firstPreviewLink(nodes: List<MfmNode>): String? {
    nodes.forEach { node ->
        when (node) {
            is MfmUrl -> if (node.url.isWebUrl()) return node.url
            is MfmLink -> if (!node.silent && node.url.isWebUrl()) return node.url
            is MfmContainer -> firstPreviewLink(node.children)?.let { return it }
            else -> Unit
        }
    }
    return null
}

fun parseLinkPreview(json: JSONObject, requestedUrl: String): LinkPreview? {
    val title = json.optStringOrNull("title")?.takeIf { it.isNotBlank() } ?: return null
    val url = json.optStringOrNull("url")?.takeIf { it.isWebUrl() } ?: requestedUrl
    return LinkPreview(
        url = url,
        title = title,
        description = json.optStringOrNull("description")?.takeIf { it.isNotBlank() },
        siteName = json.optStringOrNull("sitename")?.takeIf { it.isNotBlank() } ?: hostOf(url),
        thumbnailUrl = json.optStringOrNull("thumbnail")?.takeIf { it.startsWith("https://", ignoreCase = true) },
        sensitive = json.optBoolean("sensitive", false),
    )
}

interface LinkPreviewApi {
    suspend fun fetch(url: String): LinkPreview?
}

class HttpLinkPreviewApi(private val http: FortHttp, private val language: () -> String) : LinkPreviewApi {
    override suspend fun fetch(url: String): LinkPreview? =
        parseLinkPreview(http.getJson("/url", listOf("url" to url, "lang" to language())), url)
}

interface LinkPreviewSource {
    fun peek(url: String): LinkPreviewState

    suspend fun load(url: String): LinkPreviewState
}

class CachingLinkPreviews(
    private val api: LinkPreviewApi,
    private val scope: CoroutineScope,
    private val cacheSize: Int = CACHE_SIZE,
    concurrency: Int = CONCURRENCY,
) : LinkPreviewSource {
    private val lock = Any()
    private val settled = LinkedHashMap<String, LinkPreviewState>()
    private val inFlight = HashMap<String, Deferred<LinkPreviewState>>()
    private val gate = Semaphore(concurrency)

    override fun peek(url: String): LinkPreviewState = synchronized(lock) { settled[url] } ?: LinkPreviewState.Unknown

    override suspend fun load(url: String): LinkPreviewState {
        val request = synchronized(lock) {
            settled[url]?.let { return it }
            inFlight.getOrPut(url) { scope.async(start = CoroutineStart.LAZY) { fetch(url) } }
        }
        return request.await()
    }

    private suspend fun fetch(url: String): LinkPreviewState {
        val result = gate.withPermit { request(url) }
        synchronized(lock) {
            inFlight.remove(url)
            if (result != LinkPreviewState.Unknown) {
                settled[url] = result
                if (settled.size > cacheSize) settled.remove(settled.keys.first())
            }
        }
        return result
    }

    private suspend fun request(url: String): LinkPreviewState = try {
        api.fetch(url)?.let { LinkPreviewState.Ready(it) } ?: LinkPreviewState.None
    } catch (e: HttpStatusException) {
        if (e.code in FINAL_REFUSALS) LinkPreviewState.None else LinkPreviewState.Unknown
    } catch (_: IOException) {
        LinkPreviewState.Unknown
    }

    private companion object {
        const val CACHE_SIZE = 200
        const val CONCURRENCY = 4
        val FINAL_REFUSALS = setOf(400, 404, 422)
    }
}

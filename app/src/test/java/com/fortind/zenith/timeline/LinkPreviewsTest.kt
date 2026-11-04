package com.fortind.zenith.timeline

import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.net.HttpStatusException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewsTest {
    private fun preview(url: String) = LinkPreview(url, "title", null, null, null, false)

    private class ScriptedApi(val answer: suspend (String) -> LinkPreview?) : LinkPreviewApi {
        val calls = AtomicInteger()

        override suspend fun fetch(url: String): LinkPreview? {
            calls.incrementAndGet()
            return answer(url)
        }
    }

    private fun links(text: String) = firstPreviewLink(MfmParser.parse(text))

    @Test
    fun theLiveResponseShapeParses() {
        val json = JSONObject(
            """{"title":"Android","description":"Docs","thumbnail":"https://social.fort1nd.com/proxy/preview.webp?x=1",
            "player":{"url":null},"sitename":"Android Developers","sensitive":false,"url":"https://developer.android.com/"}""",
        )

        assertEquals(
            LinkPreview("https://developer.android.com/", "Android", "Docs", "Android Developers", "https://social.fort1nd.com/proxy/preview.webp?x=1", false),
            parseLinkPreview(json, "https://d.android.com"),
        )
    }

    @Test
    fun noTitleMeansNoCard() {
        assertNull(parseLinkPreview(JSONObject("""{"title":null,"url":"https://a.example/"}"""), "https://a.example/"))
        assertNull(parseLinkPreview(JSONObject("""{"title":"  "}"""), "https://a.example/"))
    }

    @Test
    fun aNonWebCanonicalUrlAndThumbnailAreIgnored() {
        val parsed = parseLinkPreview(
            JSONObject("""{"title":"T","url":"javascript:alert(1)","thumbnail":"http://plain.example/a.png"}"""),
            "https://a.example/page",
        )

        assertEquals("https://a.example/page", parsed?.url)
        assertEquals("a.example", parsed?.siteName)
        assertNull(parsed?.thumbnailUrl)
    }

    @Test
    fun theFirstNonSilentLinkIsPreviewed() {
        assertEquals("https://a.example/", links("see https://a.example/ and https://b.example/"))
        assertEquals("https://b.example/", links("?[quiet](https://a.example/) then [loud](https://b.example/)"))
        assertEquals("https://c.example/", links("**bold https://c.example/**"))
        assertNull(links("?[quiet](https://a.example/) only"))
        assertNull(links("no links, just @someone and #tag"))
    }

    @Test
    fun rowsWithTheSameLinkShareOneRequest() = runTest {
        val release = CompletableDeferred<Unit>()
        val api = ScriptedApi { url ->
            release.await()
            preview(url)
        }
        val previews = CachingLinkPreviews(api, backgroundScope)

        val rows = List(3) { async { previews.load("https://a.example/") } }
        yield()
        release.complete(Unit)

        assertTrue(rows.awaitAll().all { it == LinkPreviewState.Ready(preview("https://a.example/")) })
        assertEquals(1, api.calls.get())
        assertEquals(LinkPreviewState.Ready(preview("https://a.example/")), previews.peek("https://a.example/"))
    }

    @Test
    fun aRefusalIsRememberedAsNoPreview() = runTest {
        val api = ScriptedApi { throw HttpStatusException(422, "URL_PREVIEW_FAILED") }
        val previews = CachingLinkPreviews(api, backgroundScope)

        assertEquals(LinkPreviewState.None, previews.load("https://a.example/"))
        assertEquals(LinkPreviewState.None, previews.load("https://a.example/"))
        assertEquals(1, api.calls.get())
    }

    @Test
    fun aNetworkFailureIsForgottenSoTheNextRowRetries() = runTest {
        var fail = true
        val api = ScriptedApi { url ->
            if (fail) throw IOException("offline")
            preview(url)
        }
        val previews = CachingLinkPreviews(api, backgroundScope)

        assertEquals(LinkPreviewState.Unknown, previews.load("https://a.example/"))
        assertEquals(LinkPreviewState.Unknown, previews.peek("https://a.example/"))
        fail = false
        assertEquals(LinkPreviewState.Ready(preview("https://a.example/")), previews.load("https://a.example/"))
        assertEquals(2, api.calls.get())
    }

    @Test
    fun aRateLimitIsNotRememberedAsNoPreview() = runTest {
        val api = ScriptedApi { throw HttpStatusException(429, "RATE_LIMIT_EXCEEDED") }
        val previews = CachingLinkPreviews(api, backgroundScope)

        assertEquals(LinkPreviewState.Unknown, previews.load("https://a.example/"))
        assertEquals(LinkPreviewState.Unknown, previews.peek("https://a.example/"))
    }

    @Test
    fun theOldestEntryLeavesAFullCache() = runTest {
        val previews = CachingLinkPreviews(ScriptedApi { preview(it) }, backgroundScope, cacheSize = 2)

        previews.load("https://a.example/")
        previews.load("https://b.example/")
        previews.load("https://c.example/")

        assertEquals(LinkPreviewState.Unknown, previews.peek("https://a.example/"))
        assertEquals(LinkPreviewState.Ready(preview("https://c.example/")), previews.peek("https://c.example/"))
    }

    @Test
    fun onlyAFewRequestsRunAtOnce() = runTest {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val api = ScriptedApi { url ->
            peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            yield()
            running.decrementAndGet()
            preview(url)
        }
        val previews = CachingLinkPreviews(api, backgroundScope, concurrency = 2)

        List(6) { index -> async { previews.load("https://a.example/$index") } }.awaitAll()

        assertEquals(6, api.calls.get())
        assertTrue(peak.get() <= 2)
    }
}

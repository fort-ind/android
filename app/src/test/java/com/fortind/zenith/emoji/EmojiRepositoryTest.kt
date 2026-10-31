package com.fortind.zenith.emoji

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EmojiRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeInstanceApi(var emojiNames: List<String> = listOf("neko")) : InstanceApi {
        val emojiCalls = AtomicInteger()
        var failure: IOException? = null

        override suspend fun emojis(): JSONObject {
            emojiCalls.incrementAndGet()
            failure?.let { throw it }
            val list = JSONArray()
            emojiNames.forEach { name ->
                list.put(
                    JSONObject()
                        .put("name", name)
                        .put("aliases", JSONArray().put("cat").put(""))
                        .put("category", "animals")
                        .put("url", "https://x/$name.webp"),
                )
            }
            return JSONObject().put("emojis", list)
        }

        override suspend fun meta(): JSONObject {
            failure?.let { throw it }
            return JSONObject()
                .put("defaultLike", "⭐")
                .put("policies", JSONObject().put("ltlAvailable", true).put("gtlAvailable", false))
        }
    }

    @Test
    fun fetchesAndCachesWhenNothingIsStored() = runTest {
        val api = FakeInstanceApi()
        val dir = folder.newFolder()
        val repository = EmojiRepository(api, dir, backgroundScope + UnconfinedTestDispatcher(testScheduler), clock = { 1_000L }, host = "h")

        repository.ensureFresh()
        repository.meta.first { it != null }

        assertEquals("https://x/neko.webp", repository.emojiUrl("neko"))
        assertEquals("⭐", repository.meta.value?.defaultLike)
        assertFalse(repository.meta.value!!.gtlAvailable)
        assertTrue(File(dir, "custom-emoji.json").exists())
    }

    @Test
    fun freshCacheIsUsedWithoutTheNetwork() = runTest {
        val dir = folder.newFolder()
        val first = FakeInstanceApi()
        EmojiRepository(first, dir, backgroundScope + UnconfinedTestDispatcher(testScheduler), clock = { 1_000L }, host = "h").apply {
            ensureFresh()
            meta.first { it != null }
        }
        File(dir, "custom-emoji.json").setLastModified(1_000L)
        File(dir, "instance-meta.json").setLastModified(1_000L)

        val second = FakeInstanceApi(emojiNames = listOf("other"))
        val repository = EmojiRepository(second, dir, backgroundScope + UnconfinedTestDispatcher(testScheduler), clock = { 2_000L }, host = "h")
        repository.ensureFresh()
        repository.emojis.first { it != null }

        assertEquals(0, second.emojiCalls.get())
        assertEquals(setOf("neko"), repository.emojis.value?.names)
    }

    @Test
    fun staleCacheIsKeptWhenTheRefreshFails() = runTest {
        val dir = folder.newFolder()
        EmojiRepository(FakeInstanceApi(), dir, backgroundScope + UnconfinedTestDispatcher(testScheduler), clock = { 1_000L }, host = "h").apply {
            ensureFresh()
            meta.first { it != null }
        }
        File(dir, "custom-emoji.json").setLastModified(1_000L)

        val failing = FakeInstanceApi().apply { failure = IOException("offline") }
        val later = 1_000L + 7 * 60 * 60 * 1000L
        val repository = EmojiRepository(failing, dir, backgroundScope + UnconfinedTestDispatcher(testScheduler), clock = { later }, host = "h")
        repository.ensureFresh()
        repository.meta.first { it != null }

        assertEquals(1, failing.emojiCalls.get())
        assertEquals(setOf("neko"), repository.emojis.value?.names)
    }

    @Test
    fun unknownEmojiFallBackToTheServerRoute() = runTest {
        val repository = EmojiRepository(FakeInstanceApi(), folder.newFolder(), backgroundScope, host = "social.example")

        assertEquals("https://social.example/emoji/blob.webp", repository.emojiUrl("blob"))
    }

    @Test
    fun searchRanksPrefixMatchesFirstAndUsesAliases() {
        val index = EmojiIndex(
            listOf(
                CustomEmoji("blobcat", listOf("kitty"), "blob", "u", false, false),
                CustomEmoji("catblob", emptyList(), "blob", "u", false, false),
                CustomEmoji("dog", listOf("catdog"), null, "u", false, false),
            ),
        )

        assertEquals(listOf("catblob", "dog", "blobcat"), index.search(":cat").map { it.name })
        assertEquals(listOf("blobcat"), index.search("kit").map { it.name })
        assertTrue(index.search(" ").isEmpty())
        assertNull(index.categories.last().first)
    }

    @Test
    fun recentReactionsMoveToTheFrontAndAreCapped() {
        val start = (1..MAX_RECENT_REACTIONS).map { "e$it" }

        val updated = recordRecent(start, "e5")
        assertEquals("e5", updated.first())
        assertEquals(MAX_RECENT_REACTIONS, updated.size)

        val added = recordRecent(start, "new")
        assertEquals("new", added.first())
        assertFalse("e$MAX_RECENT_REACTIONS" in added)
    }

    @Test
    fun unicodeEmojiParseAndSearch() {
        val json = """[{"group":"Smileys","emoji":[["😀","grinning face"],["😺","grinning cat"]]},{"group":"Animals","emoji":[["🐈","cat"]]}]"""
        val groups = parseUnicodeEmoji(ByteArrayInputStream(json.toByteArray()))

        assertEquals(listOf("Smileys", "Animals"), groups.map { it.name })
        assertEquals(listOf("😺", "🐈"), groups.search("cat").map { it.emoji })
        assertEquals(listOf("😀", "😺"), groups.search("grin").map { it.emoji })
    }
}

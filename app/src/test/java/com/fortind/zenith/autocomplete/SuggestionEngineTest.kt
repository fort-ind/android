package com.fortind.zenith.autocomplete

import com.fortind.zenith.emoji.CustomEmoji
import com.fortind.zenith.emoji.EmojiIndex
import com.fortind.zenith.emoji.UnicodeEmoji
import com.fortind.zenith.emoji.UnicodeEmojiGroup
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.testUser
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private class FakeApi : SuggestionsApi {
        var users = emptyList<NoteUser>()
        var frequent = emptyList<NoteUser>()
        var tags = emptyList<String>()
        var failure: IOException? = null
        val userQueries = CopyOnWriteArrayList<String>()
        var frequentCalls = 0

        override suspend fun users(token: String?, query: String, limit: Int): List<NoteUser> {
            failure?.let { throw it }
            userQueries += query
            return users
        }

        override suspend fun frequentUsers(token: String, userId: String, limit: Int): List<NoteUser> {
            failure?.let { throw it }
            frequentCalls++
            return frequent
        }

        override suspend fun hashtags(token: String?, query: String, limit: Int): List<String> {
            failure?.let { throw it }
            return tags
        }
    }

    private val neko = CustomEmoji("neko", emptyList(), null, "https://e/neko.png", isSensitive = false, restricted = false)
    private val nekoSmile = CustomEmoji("neko_smile", listOf("catgrin"), null, "https://e/ns.png", isSensitive = false, restricted = false)
    private val unicode = listOf(
        UnicodeEmojiGroup("Smileys", listOf(UnicodeEmoji("😄", "grinning face with smiling eyes"), UnicodeEmoji("🐱", "cat face"))),
    )

    private fun engine(api: FakeApi, emoji: EmojiIndex? = EmojiIndex(listOf(neko, nekoSmile))) =
        SuggestionEngine(api, { emoji }, { unicode })

    private fun mention(query: String) = Trigger(TriggerKind.Mention, query, 0, query.length + 1)

    @Test
    fun mentionQueriesSearchPeopleAndLeaveYouOut() = runTest {
        val api = FakeApi().apply { users = listOf(testUser("u1", "alice"), testUser("me", "me")) }

        val result = engine(api).suggest(mention("al"), token = "t", myUserId = "me")

        assertEquals(listOf("alice"), result.map { (it as UserSuggestion).user.username })
        assertEquals(listOf("al"), api.userQueries.toList())
    }

    @Test
    fun anEmptyMentionOffersPeopleYouReplyToMost() = runTest {
        val api = FakeApi().apply { frequent = listOf(testUser("u1", "alice"), testUser("u2", "bob")) }

        val result = engine(api).suggest(mention(""), token = "t", myUserId = "me")

        assertEquals(listOf("alice", "bob"), result.map { (it as UserSuggestion).user.username })
    }

    @Test
    fun frequentPeopleAreFetchedOncePerSession() = runTest {
        val api = FakeApi().apply { frequent = listOf(testUser("u1", "alice")) }
        val engine = engine(api)

        engine.suggest(mention(""), "t", "me")
        engine.suggest(mention(""), "t", "me")

        assertEquals(1, api.frequentCalls)
        engine.forget()
        engine.suggest(mention(""), "t", "me")
        assertEquals(2, api.frequentCalls)
    }

    @Test
    fun anEmptyMentionOffersNothingToGuests() = runTest {
        val api = FakeApi().apply { frequent = listOf(testUser("u1", "alice")) }

        assertTrue(engine(api).suggest(mention(""), token = null, myUserId = null).isEmpty())
        assertEquals(0, api.frequentCalls)
    }

    @Test
    fun networkFailuresQuietlyMeanNoSuggestions() = runTest {
        val api = FakeApi().apply { failure = IOException("offline") }

        assertTrue(engine(api).suggest(mention("al"), "t", "me").isEmpty())
        assertTrue(engine(api).suggest(Trigger(TriggerKind.Hashtag, "ph", 0, 3), "t", "me").isEmpty())
    }

    @Test
    fun hashtagSuggestionsComeFromTheServer() = runTest {
        val api = FakeApi().apply { tags = listOf("photography", "photo") }

        val result = engine(api).suggest(Trigger(TriggerKind.Hashtag, "pho", 0, 4), "t", "me")

        assertEquals(listOf("#photography ", "#photo "), result.map { it.insertion })
    }

    @Test
    fun emojiSuggestionsListCustomEmojiBeforeStandardOnesWithoutTheNetwork() = runTest {
        val api = FakeApi().apply { failure = IOException("must not be called") }

        val result = engine(api).suggest(Trigger(TriggerKind.Emoji, "neko", 0, 5), null, null)

        assertEquals(listOf(":neko: ", ":neko_smile: "), result.map { it.insertion })
        val standard = engine(api).suggest(Trigger(TriggerKind.Emoji, "cat", 0, 4), null, null)
        assertEquals(listOf(":neko_smile: ", "🐱 "), standard.map { it.insertion })
    }

    @Test
    fun emojiStillWorkBeforeTheInstanceEmojiHaveLoaded() = runTest {
        val result = engine(FakeApi(), emoji = null).suggest(Trigger(TriggerKind.Emoji, "grin", 0, 5), null, null)

        assertEquals(listOf("😄 "), result.map { it.insertion })
    }

    @Test
    fun insertionsMatchWhatEachSuggestionRepresents() {
        assertEquals("@alice ", UserSuggestion(testUser("u1", "alice")).insertion)
        assertEquals("@alice@remote.example ", UserSuggestion(testUser("u1", "alice", host = "remote.example")).insertion)
        assertEquals("#tag ", HashtagSuggestion("tag").insertion)
    }
}

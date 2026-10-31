package com.fortind.zenith.autocomplete

import com.fortind.zenith.emoji.EmojiIndex
import com.fortind.zenith.emoji.UnicodeEmojiGroup
import com.fortind.zenith.emoji.search
import java.io.IOException

class SuggestionEngine(
    private val api: SuggestionsApi,
    private val customEmoji: () -> EmojiIndex?,
    private val unicodeEmoji: suspend () -> List<UnicodeEmojiGroup>,
) {
    private var frequent: Pair<String, List<UserSuggestion>>? = null

    suspend fun suggest(trigger: Trigger, token: String?, myUserId: String?): List<Suggestion> = when (trigger.kind) {
        TriggerKind.Mention -> mentions(trigger.query, token, myUserId)
        TriggerKind.Hashtag -> hashtags(trigger.query, token)
        TriggerKind.Emoji -> emoji(trigger.query)
    }

    fun forget() {
        frequent = null
    }

    private suspend fun mentions(query: String, token: String?, myUserId: String?): List<Suggestion> {
        if (query.isEmpty()) return frequentUsers(token, myUserId)
        return quietly { api.users(token, query, MAX_USERS).filter { it.id != myUserId }.map(::UserSuggestion) }
    }

    private suspend fun frequentUsers(token: String?, myUserId: String?): List<Suggestion> {
        if (token == null || myUserId == null) return emptyList()
        frequent?.takeIf { it.first == myUserId }?.let { return it.second }
        val users = quietly { api.frequentUsers(token, myUserId, MAX_USERS).map(::UserSuggestion) }
        if (users.isNotEmpty()) frequent = myUserId to users
        return users
    }

    private suspend fun hashtags(query: String, token: String?): List<Suggestion> =
        quietly { api.hashtags(token, query, MAX_HASHTAGS).map(::HashtagSuggestion) }

    private suspend fun emoji(query: String): List<Suggestion> {
        val custom = customEmoji()?.search(query, MAX_EMOJI)?.map(::CustomEmojiSuggestion).orEmpty()
        val standard = quietly { unicodeEmoji().search(query, MAX_EMOJI).map(::UnicodeEmojiSuggestion) }
        return (custom + standard).take(MAX_EMOJI)
    }

    private suspend fun <T> quietly(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (_: IOException) {
        emptyList()
    }

    private companion object {
        const val MAX_USERS = 8
        const val MAX_HASHTAGS = 8
        const val MAX_EMOJI = 12
    }
}

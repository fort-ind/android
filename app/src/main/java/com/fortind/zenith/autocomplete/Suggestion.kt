package com.fortind.zenith.autocomplete

import com.fortind.zenith.emoji.CustomEmoji
import com.fortind.zenith.emoji.UnicodeEmoji
import com.fortind.zenith.timeline.NoteUser

sealed interface Suggestion {
    val key: String

    val insertion: String
}

data class UserSuggestion(val user: NoteUser) : Suggestion {
    override val key: String get() = "user:${user.id}"

    override val insertion: String get() = user.handle + " "
}

data class HashtagSuggestion(val tag: String) : Suggestion {
    override val key: String get() = "tag:$tag"

    override val insertion: String get() = "#$tag "
}

data class CustomEmojiSuggestion(val emoji: CustomEmoji) : Suggestion {
    override val key: String get() = "custom:${emoji.name}"

    override val insertion: String get() = ":${emoji.name}: "
}

data class UnicodeEmojiSuggestion(val emoji: UnicodeEmoji) : Suggestion {
    override val key: String get() = "unicode:${emoji.emoji}"

    override val insertion: String get() = emoji.emoji + " "
}

data class SuggestionsState(
    val trigger: Trigger,
    val items: List<Suggestion>,
    val selected: Int = 0,
)

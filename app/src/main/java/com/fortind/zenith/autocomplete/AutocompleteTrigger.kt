package com.fortind.zenith.autocomplete

enum class TriggerKind { Mention, Hashtag, Emoji }

data class Trigger(val kind: TriggerKind, val query: String, val start: Int, val end: Int)

private const val MAX_TOKEN_LENGTH = 64
private const val MIN_EMOJI_QUERY = 2
private const val OPENING_PUNCTUATION = "([{\"'"

fun findTrigger(text: CharSequence, cursor: Int): Trigger? {
    if (cursor <= 0 || cursor > text.length) return null
    var tokenStart = cursor
    while (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) {
        tokenStart--
        if (cursor - tokenStart > MAX_TOKEN_LENGTH) return null
    }
    while (tokenStart < cursor && text[tokenStart] in OPENING_PUNCTUATION) tokenStart++
    if (tokenStart >= cursor) return null
    val marker = text[tokenStart]
    val query = text.substring(tokenStart + 1, cursor)
    val kind = when (marker) {
        '@' -> TriggerKind.Mention.takeIf { query.isValidMention() }
        '#' -> TriggerKind.Hashtag.takeIf { query.isNotEmpty() && query.all { it.isLetterOrDigit() || it == '_' } }
        ':' -> TriggerKind.Emoji.takeIf { query.length >= MIN_EMOJI_QUERY && query.all { it.isLetterOrDigit() || it in EMOJI_NAME_SYMBOLS } }
        else -> null
    } ?: return null
    return Trigger(kind, query, tokenStart, cursor)
}

private const val EMOJI_NAME_SYMBOLS = "_+-"

private fun String.isValidMention(): Boolean =
    count { it == '@' } <= 1 && all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' || it == '@' }

fun splitHandleQuery(query: String): Pair<String, String?> {
    val at = query.indexOf('@')
    if (at < 0) return query to null
    return query.substring(0, at) to query.substring(at + 1).ifEmpty { null }
}

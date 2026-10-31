package com.fortind.zenith.timeline

private const val VARIATION_SELECTOR = "️"
private const val ZERO_WIDTH_JOINER = "‍"

object Reactions {
    fun isCustom(key: String): Boolean = key.length > 2 && key.startsWith(':') && key.endsWith(':')

    fun customName(key: String): String? {
        if (!isCustom(key)) return null
        return key.substring(1, key.length - 1).substringBefore('@')
    }

    fun customHost(key: String): String? {
        if (!isCustom(key)) return null
        val inner = key.substring(1, key.length - 1)
        val host = inner.substringAfter('@', "")
        return host.takeIf { it.isNotEmpty() && it != "." }
    }

    fun isLocalCustom(key: String): Boolean = isCustom(key) && customHost(key) == null

    fun normalizeUnicode(reaction: String): String =
        if (reaction.contains(ZERO_WIDTH_JOINER)) reaction else reaction.replace(VARIATION_SELECTOR, "")

    fun keyFor(reaction: String): String = when {
        !isCustom(reaction) -> normalizeUnicode(reaction)
        customHost(reaction) == null -> ":${customName(reaction)}@.:"
        else -> reaction
    }

    fun toApi(key: String, localEmojiNames: Set<String>?): String? = when {
        !isCustom(key) -> normalizeUnicode(key)
        customHost(key) == null -> ":${customName(key)}:"
        localEmojiNames != null && customName(key) in localEmojiNames -> ":${customName(key)}:"
        else -> null
    }

    fun emojiUrl(key: String, reactionEmojis: Map<String, String>, localUrl: (String) -> String): String? {
        val name = customName(key) ?: return null
        val host = customHost(key) ?: return localUrl(name)
        return reactionEmojis["$name@$host"]
    }

    fun sameReaction(a: String?, b: String?): Boolean =
        if (a == null || b == null) a == b else keyFor(a) == keyFor(b)
}

fun Note.withMyReaction(reaction: String?): Note {
    val from = myReaction?.let(Reactions::keyFor)
    val to = reaction?.let(Reactions::keyFor)
    if (from == to) return this
    val counts = LinkedHashMap(reactions)
    if (from != null) {
        val remaining = (counts[from] ?: 0) - 1
        if (remaining > 0) counts[from] = remaining else counts.remove(from)
    }
    if (to != null) counts[to] = (counts[to] ?: 0) + 1
    return copy(reactions = counts, myReaction = to)
}

fun Note.withReactionCount(reaction: String, delta: Int, emojiUrl: String? = null): Note {
    val key = Reactions.keyFor(reaction)
    val counts = LinkedHashMap(reactions)
    val next = (counts[key] ?: 0) + delta
    if (next > 0) counts[key] = next else counts.remove(key)
    val emojis = if (emojiUrl != null && Reactions.customHost(key) != null) {
        reactionEmojis + (key.substring(1, key.length - 1) to emojiUrl)
    } else {
        reactionEmojis
    }
    return copy(reactions = counts, reactionEmojis = emojis)
}

fun Note.acceptsOnlyLike(): Boolean = when (reactionAcceptance) {
    ReactionAcceptance.LikeOnly -> true
    ReactionAcceptance.LikeOnlyForRemote, ReactionAcceptance.NonSensitiveOnlyForLocalLikeOnlyForRemote -> user.host != null
    else -> false
}

fun Note.acceptsSensitiveReactions(): Boolean =
    reactionAcceptance != ReactionAcceptance.NonSensitiveOnly &&
        reactionAcceptance != ReactionAcceptance.NonSensitiveOnlyForLocalLikeOnlyForRemote

fun Note.canRenote(myUserId: String?): Boolean = myUserId != null && !deleted && when (visibility) {
    Visibility.Public, Visibility.Home -> true
    Visibility.Followers, Visibility.Specified -> user.id == myUserId
}

fun Note.isRenoteOf(noteId: String, userId: String): Boolean = isPureRenote && user.id == userId && renote?.id == noteId

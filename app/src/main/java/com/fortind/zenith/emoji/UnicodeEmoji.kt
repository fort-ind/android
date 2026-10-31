package com.fortind.zenith.emoji

import android.content.res.AssetManager
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class UnicodeEmoji(val emoji: String, val name: String)

data class UnicodeEmojiGroup(val name: String, val emoji: List<UnicodeEmoji>)

fun parseUnicodeEmoji(input: InputStream): List<UnicodeEmojiGroup> {
    val groups = JSONArray(input.bufferedReader().use { it.readText() })
    return (0 until groups.length()).mapNotNull { index ->
        val group = groups.optJSONObject(index) ?: return@mapNotNull null
        val entries = group.optJSONArray("emoji") ?: return@mapNotNull null
        UnicodeEmojiGroup(
            name = group.optString("group"),
            emoji = (0 until entries.length()).mapNotNull { entryIndex ->
                val entry = entries.optJSONArray(entryIndex) ?: return@mapNotNull null
                UnicodeEmoji(entry.optString(0), entry.optString(1))
            },
        )
    }
}

fun List<UnicodeEmojiGroup>.search(query: String, limit: Int = 60): List<UnicodeEmoji> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    val all = asSequence().flatMap { it.emoji.asSequence() }
    val prefix = all.filter { emoji -> emoji.name.split(' ').any { it.startsWith(needle) } }.toList()
    val partial = all.filter { needle in it.name && it !in prefix }.toList()
    return (prefix + partial).take(limit)
}

interface UnicodeEmojiSource {
    suspend fun groups(): List<UnicodeEmojiGroup>
}

class AssetUnicodeEmojiSource(private val assets: AssetManager) : UnicodeEmojiSource {
    private val mutex = Mutex()
    private var cached: List<UnicodeEmojiGroup>? = null

    override suspend fun groups(): List<UnicodeEmojiGroup> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { assets.open(ASSET).use(::parseUnicodeEmoji) }.also { cached = it }
    }

    private companion object {
        const val ASSET = "emoji/unicode-emoji.json"
    }
}

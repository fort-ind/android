package com.fortind.zenith.emoji

import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optIntOrNull
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.strings
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

data class CustomEmoji(
    val name: String,
    val aliases: List<String>,
    val category: String?,
    val url: String,
    val isSensitive: Boolean,
    val restricted: Boolean,
)

data class InstanceMeta(
    val defaultLike: String,
    val ltlAvailable: Boolean,
    val gtlAvailable: Boolean,
    val maxNoteTextLength: Int = DEFAULT_MAX_NOTE_TEXT_LENGTH,
    val maxCwLength: Int = DEFAULT_MAX_CW_LENGTH,
    val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
    val maxAltTextLength: Int = DEFAULT_MAX_ALT_TEXT_LENGTH,
)

const val DEFAULT_MAX_NOTE_TEXT_LENGTH = 3000
const val DEFAULT_MAX_CW_LENGTH = 500
const val DEFAULT_MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024
const val DEFAULT_MAX_ALT_TEXT_LENGTH = 1500

class EmojiIndex(val emojis: List<CustomEmoji>) {
    private val byName: Map<String, CustomEmoji> = emojis.associateBy { it.name }

    val names: Set<String> get() = byName.keys

    val categories: List<Pair<String?, List<CustomEmoji>>> =
        emojis.groupBy { it.category }.toList().sortedWith(compareBy({ it.first == null }, { it.first?.lowercase() }))

    operator fun get(name: String): CustomEmoji? = byName[name]

    fun search(query: String, limit: Int = 60): List<CustomEmoji> {
        val needle = query.trim().trim(':').lowercase()
        if (needle.isEmpty()) return emptyList()
        val prefix = mutableListOf<CustomEmoji>()
        val partial = mutableListOf<CustomEmoji>()
        emojis.forEach { emoji ->
            val names = sequenceOf(emoji.name) + emoji.aliases.asSequence()
            when {
                names.any { it.lowercase().startsWith(needle) } -> prefix += emoji
                names.any { needle in it.lowercase() } -> partial += emoji
            }
        }
        return (prefix + partial).take(limit)
    }
}

interface InstanceApi {
    suspend fun emojis(): JSONObject

    suspend fun meta(): JSONObject
}

class HttpInstanceApi(private val http: FortHttp) : InstanceApi {
    override suspend fun emojis(): JSONObject = http.postJson("/api/emojis", JSONObject())

    override suspend fun meta(): JSONObject = http.postJson("/api/meta", JSONObject().put("detail", true))
}

fun parseEmojiIndex(json: JSONObject): EmojiIndex = EmojiIndex(
    json.optJSONArray("emojis")?.objects()?.mapNotNull { emoji ->
        val name = emoji.optStringOrNull("name") ?: return@mapNotNull null
        val url = emoji.optStringOrNull("url") ?: return@mapNotNull null
        CustomEmoji(
            name = name,
            aliases = emoji.optJSONArray("aliases")?.strings().orEmpty().filter { it.isNotBlank() },
            category = emoji.optStringOrNull("category"),
            url = url,
            isSensitive = emoji.optBoolean("isSensitive"),
            restricted = (emoji.optJSONArray("roleIdsThatCanBeUsedThisEmojiAsReaction")?.length() ?: 0) > 0,
        )
    }.orEmpty(),
)

fun parseInstanceMeta(json: JSONObject): InstanceMeta {
    val policies = json.optJSONObject("policies")
    return InstanceMeta(
        defaultLike = json.optStringOrNull("defaultLike") ?: DEFAULT_LIKE,
        ltlAvailable = policies?.optBoolean("ltlAvailable", true) ?: true,
        gtlAvailable = policies?.optBoolean("gtlAvailable", true) ?: true,
        maxNoteTextLength = json.optIntOrNull("maxNoteTextLength")?.takeIf { it > 0 } ?: DEFAULT_MAX_NOTE_TEXT_LENGTH,
        maxCwLength = json.optIntOrNull("maxCwLength")?.takeIf { it > 0 } ?: DEFAULT_MAX_CW_LENGTH,
        maxFileSizeBytes = policies?.optIntOrNull("maxFileSizeMb")?.takeIf { it > 0 }?.let { it * 1024L * 1024L }
            ?: DEFAULT_MAX_FILE_SIZE_BYTES,
        maxAltTextLength = json.optIntOrNull("maxAltTextLength")?.takeIf { it > 0 } ?: DEFAULT_MAX_ALT_TEXT_LENGTH,
    )
}

const val DEFAULT_LIKE = "❤️"

interface InstanceInfo {
    val emojis: StateFlow<EmojiIndex?>
    val meta: StateFlow<InstanceMeta?>

    fun ensureFresh()

    fun emojiUrl(name: String): String
}

class EmojiRepository(
    private val api: InstanceApi,
    private val cacheDir: File,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val host: String = FortInstance.HOST,
) : InstanceInfo {
    private val _emojis = MutableStateFlow<EmojiIndex?>(null)
    override val emojis: StateFlow<EmojiIndex?> = _emojis.asStateFlow()

    private val _meta = MutableStateFlow<InstanceMeta?>(null)
    override val meta: StateFlow<InstanceMeta?> = _meta.asStateFlow()

    private val refreshing = AtomicBoolean(false)

    override fun ensureFresh() {
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                refresh(EMOJI_FILE, api::emojis) { _emojis.value = parseEmojiIndex(it) }
                refresh(META_FILE, api::meta) { _meta.value = parseInstanceMeta(it) }
            } finally {
                refreshing.set(false)
            }
        }
    }

    override fun emojiUrl(name: String): String = _emojis.value?.get(name)?.url ?: "https://$host/emoji/$name.webp"

    private suspend fun refresh(fileName: String, fetch: suspend () -> JSONObject, apply: (JSONObject) -> Unit) {
        val file = File(cacheDir, fileName)
        val cached = withContext(Dispatchers.IO) { read(file) }
        if (cached != null) apply(cached)
        val stale = cached == null || clock() - file.lastModified() > TTL_MS
        if (!stale) return
        val fresh = try {
            fetch()
        } catch (_: IOException) {
            return
        }
        withContext(Dispatchers.IO) { write(file, fresh) }
        apply(fresh)
    }

    private fun read(file: File): JSONObject? = try {
        if (file.exists()) JSONObject(file.readText()) else null
    } catch (_: IOException) {
        null
    } catch (_: JSONException) {
        null
    }

    private fun write(file: File, json: JSONObject) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            file.setLastModified(clock())
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val EMOJI_FILE = "custom-emoji.json"
        const val META_FILE = "instance-meta.json"
        const val TTL_MS = 6 * 60 * 60 * 1000L
    }
}

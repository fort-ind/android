package com.fortind.zenith.composer

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optIntOrNull
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.timeline.Visibility
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class ComposerDraft(
    val text: String = "",
    val cw: String? = null,
    val visibility: Visibility = Visibility.Public,
    val localOnly: Boolean = false,
    val attachments: List<DraftAttachment> = emptyList(),
) {
    val isBlank: Boolean get() = text.isBlank() && cw.isNullOrBlank() && attachments.isEmpty()
}

data class StoredDraft(val draft: ComposerDraft, val updatedAtMillis: Long)

interface DraftStore {
    suspend fun load(key: String): ComposerDraft?

    suspend fun save(key: String, draft: ComposerDraft)

    suspend fun clear(key: String)

    suspend fun adopt(userId: String)

    suspend fun forget(userId: String)
}

const val MAX_STORED_DRAFTS = 24

private const val ACCOUNT_SEPARATOR = '/'

fun draftStorageKey(userId: String?, slot: String): String = if (userId == null) slot else "$userId$ACCOUNT_SEPARATOR$slot"

fun adoptDrafts(drafts: Map<String, StoredDraft>, userId: String): Map<String, StoredDraft> {
    if (drafts.keys.all { ACCOUNT_SEPARATOR in it }) return drafts
    val (unowned, owned) = drafts.entries.partition { ACCOUNT_SEPARATOR !in it.key }
    val adopted = unowned.associate { draftStorageKey(userId, it.key) to it.value }
    return adopted + owned.associate { it.key to it.value }
}

fun forgetDrafts(drafts: Map<String, StoredDraft>, userId: String): Map<String, StoredDraft> =
    drafts.filterKeys { ACCOUNT_SEPARATOR in it && !it.startsWith(draftStorageKey(userId, "")) }

fun putDraft(drafts: Map<String, StoredDraft>, key: String, draft: ComposerDraft, nowMillis: Long): Map<String, StoredDraft> {
    if (draft.isBlank) return drafts - key
    val updated = drafts + (key to StoredDraft(draft, nowMillis))
    if (updated.size <= MAX_STORED_DRAFTS) return updated
    val keep = updated.entries.sortedByDescending { it.value.updatedAtMillis }.take(MAX_STORED_DRAFTS).map { it.key }.toSet()
    return updated.filterKeys { it in keep }
}

fun encodeDrafts(drafts: Map<String, StoredDraft>): String {
    val root = JSONObject()
    drafts.forEach { (key, stored) ->
        root.put(
            key,
            JSONObject()
                .put("text", stored.draft.text)
                .put("cw", stored.draft.cw ?: JSONObject.NULL)
                .put("visibility", stored.draft.visibility.apiName)
                .put("localOnly", stored.draft.localOnly)
                .put("attachments", JSONArray(stored.draft.attachments.map(::encodeAttachment)))
                .put("updatedAt", stored.updatedAtMillis),
        )
    }
    return root.toString()
}

private fun encodeAttachment(attachment: DraftAttachment): JSONObject {
    val file = attachment.file
    return JSONObject()
        .put("id", file.id)
        .put("name", file.name ?: JSONObject.NULL)
        .put("type", file.mimeType)
        .put("url", file.url)
        .put("thumbnailUrl", file.thumbnailUrl ?: JSONObject.NULL)
        .put("blurhash", file.blurhash ?: JSONObject.NULL)
        .put("width", file.width ?: JSONObject.NULL)
        .put("height", file.height ?: JSONObject.NULL)
        .put("serverAlt", file.altText ?: JSONObject.NULL)
        .put("serverSensitive", file.isSensitive)
        .put("alt", attachment.altText)
        .put("sensitive", attachment.isSensitive)
}

private fun decodeAttachment(json: JSONObject): DraftAttachment? {
    val id = json.optStringOrNull("id") ?: return null
    val url = json.optStringOrNull("url") ?: return null
    val file = NoteFile(
        id = id,
        name = json.optStringOrNull("name"),
        mimeType = json.optStringOrNull("type") ?: "application/octet-stream",
        url = url,
        thumbnailUrl = json.optStringOrNull("thumbnailUrl"),
        blurhash = json.optStringOrNull("blurhash"),
        isSensitive = json.optBoolean("serverSensitive"),
        altText = json.optStringOrNull("serverAlt"),
        width = json.optIntOrNull("width"),
        height = json.optIntOrNull("height"),
    )
    return DraftAttachment(file, altText = json.optString("alt"), isSensitive = json.optBoolean("sensitive"))
}

fun decodeDrafts(raw: String?): Map<String, StoredDraft> {
    if (raw.isNullOrBlank()) return emptyMap()
    return try {
        val root = JSONObject(raw)
        buildMap {
            root.keys().forEach { key ->
                val json = root.optJSONObject(key) ?: return@forEach
                put(
                    key,
                    StoredDraft(
                        draft = ComposerDraft(
                            text = json.optString("text"),
                            cw = if (json.isNull("cw")) null else json.optString("cw"),
                            visibility = Visibility.fromApiName(json.optStringOrNull("visibility")),
                            localOnly = json.optBoolean("localOnly"),
                            attachments = json.optJSONArray("attachments")?.objects()?.mapNotNull(::decodeAttachment).orEmpty(),
                        ),
                        updatedAtMillis = json.optLong("updatedAt"),
                    ),
                )
            }
        }
    } catch (_: JSONException) {
        emptyMap()
    }
}

private val Context.draftsDataStore by preferencesDataStore(
    name = "drafts",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreDraftStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : DraftStore {
    private val dataStore = context.applicationContext.draftsDataStore

    override suspend fun load(key: String): ComposerDraft? =
        decodeDrafts(dataStore.data.first()[DraftsKey])[key]?.draft

    override suspend fun save(key: String, draft: ComposerDraft) {
        dataStore.edit { prefs ->
            prefs[DraftsKey] = encodeDrafts(putDraft(decodeDrafts(prefs[DraftsKey]), key, draft, clock()))
        }
    }

    override suspend fun clear(key: String) {
        dataStore.edit { prefs ->
            prefs[DraftsKey] = encodeDrafts(decodeDrafts(prefs[DraftsKey]) - key)
        }
    }

    override suspend fun adopt(userId: String) {
        dataStore.edit { prefs ->
            val current = decodeDrafts(prefs[DraftsKey])
            val adopted = adoptDrafts(current, userId)
            if (adopted != current) prefs[DraftsKey] = encodeDrafts(adopted)
        }
    }

    override suspend fun forget(userId: String) {
        dataStore.edit { prefs ->
            prefs[DraftsKey] = encodeDrafts(forgetDrafts(decodeDrafts(prefs[DraftsKey]), userId))
        }
    }

    private companion object {
        val DraftsKey = stringPreferencesKey("drafts")
    }
}

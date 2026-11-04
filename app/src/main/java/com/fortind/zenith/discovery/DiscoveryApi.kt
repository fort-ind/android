package com.fortind.zenith.discovery

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fortind.zenith.mfm.MfmNode
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.net.objects
import com.fortind.zenith.net.optIntOrNull
import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.translatingAuthErrors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject

const val POPULAR_HASHTAG_LIMIT = 20
const val MAX_DISMISSED_ANNOUNCEMENTS = 50
private const val ANNOUNCEMENT_LIMIT = 20

data class Hashtag(val tag: String, val usedBy: Int)

enum class AnnouncementKind { Info, Warning, Error, Success }

data class Announcement(
    val id: String,
    val title: String,
    val textMfm: List<MfmNode>,
    val kind: AnnouncementKind,
    val isRead: Boolean,
)

fun parseHashtag(json: JSONObject): Hashtag? {
    val tag = json.optStringOrNull("tag")?.takeIf(String::isNotEmpty) ?: return null
    val count = json.optIntOrNull("mentionedUsersCount") ?: 0
    return if (count > 0) Hashtag(tag, count) else null
}

fun parseAnnouncement(json: JSONObject): Announcement? {
    val id = json.optStringOrNull("id") ?: return null
    return Announcement(
        id = id,
        title = json.optStringOrNull("title").orEmpty(),
        textMfm = MfmParser.parse(json.optStringOrNull("text").orEmpty()),
        kind = when (json.optStringOrNull("icon")) {
            "warning" -> AnnouncementKind.Warning
            "error" -> AnnouncementKind.Error
            "success" -> AnnouncementKind.Success
            else -> AnnouncementKind.Info
        },
        isRead = json.optBoolean("isRead"),
    )
}

fun unreadAnnouncements(all: List<Announcement>, dismissed: List<String>): List<Announcement> =
    all.filter { !it.isRead && it.id !in dismissed }

fun rememberDismissed(dismissed: List<String>, id: String): List<String> =
    (listOf(id) + dismissed.filterNot { it == id }).take(MAX_DISMISSED_ANNOUNCEMENTS)

interface DiscoveryApi {
    suspend fun popularHashtags(): List<Hashtag>

    suspend fun announcements(token: String?): List<Announcement>

    suspend fun markAnnouncementRead(token: String, id: String)
}

class HttpDiscoveryApi(private val http: FortHttp) : DiscoveryApi {
    override suspend fun popularHashtags(): List<Hashtag> {
        val body = JSONObject().put("sort", "+mentionedUsers").put("limit", POPULAR_HASHTAG_LIMIT)
        val array = http.postJsonArray("/api/hashtags/list", body)
        return withContext(Dispatchers.Default) { array.objects().mapNotNull(::parseHashtag) }
    }

    override suspend fun announcements(token: String?): List<Announcement> {
        val body = JSONObject().put("limit", ANNOUNCEMENT_LIMIT).put("isActive", true)
        token?.let { body.put("i", it) }
        val array = http.postJsonArray("/api/announcements", body)
        return withContext(Dispatchers.Default) { array.objects().mapNotNull(::parseAnnouncement) }
    }

    override suspend fun markAnnouncementRead(token: String, id: String) {
        translatingAuthErrors {
            http.postDiscardingResponse("/api/i/read-announcement", JSONObject().put("i", token).put("announcementId", id))
        }
    }
}

interface DismissedAnnouncementsStore {
    val dismissed: Flow<List<String>>

    suspend fun dismiss(id: String)
}

class InMemoryDismissedAnnouncementsStore : DismissedAnnouncementsStore {
    private val state = MutableStateFlow<List<String>>(emptyList())

    override val dismissed: Flow<List<String>> = state

    override suspend fun dismiss(id: String) = state.update { rememberDismissed(it, id) }
}

private val Context.announcementsDataStore by preferencesDataStore(
    name = "announcements",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreDismissedAnnouncementsStore(context: Context) : DismissedAnnouncementsStore {
    private val dataStore = context.applicationContext.announcementsDataStore

    override val dismissed: Flow<List<String>> = dataStore.data.map { prefs -> prefs[DismissedKey].toIds() }

    override suspend fun dismiss(id: String) {
        dataStore.edit { prefs -> prefs[DismissedKey] = rememberDismissed(prefs[DismissedKey].toIds(), id).joinToString(SEPARATOR) }
    }

    private fun String?.toIds(): List<String> = orEmpty().split(SEPARATOR).filter(String::isNotEmpty)

    private companion object {
        const val SEPARATOR = ","
        val DismissedKey = stringPreferencesKey("dismissed")
    }
}

sealed interface SearchIntent {
    data class Tag(val tag: String) : SearchIntent

    data class Person(val username: String, val host: String?) : SearchIntent

    data class Notes(val query: String) : SearchIntent

    data object SignInNeeded : SearchIntent
}

private val TagQuery = Regex("#([^\\s#]{1,128})")
private val HandleQuery = Regex("@([A-Za-z0-9_.-]{1,128})(?:@([A-Za-z0-9.-]{1,253}))?")

fun searchIntent(text: String, signedIn: Boolean): SearchIntent? {
    val query = text.trim()
    if (query.isEmpty()) return null
    TagQuery.matchEntire(query)?.let { return SearchIntent.Tag(it.groupValues[1]) }
    HandleQuery.matchEntire(query)?.let { match ->
        return SearchIntent.Person(match.groupValues[1], match.groupValues[2].takeIf(String::isNotEmpty))
    }
    return if (signedIn) SearchIntent.Notes(query) else SearchIntent.SignInNeeded
}

package com.fortind.zenith.emoji

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface RecentReactionsStore {
    val recent: Flow<List<String>>

    suspend fun record(reaction: String)
}

const val MAX_RECENT_REACTIONS = 24

fun recordRecent(recent: List<String>, reaction: String): List<String> =
    (listOf(reaction) + recent.filter { it != reaction }).take(MAX_RECENT_REACTIONS)

private val Context.reactionsDataStore by preferencesDataStore(
    name = "reactions",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreRecentReactionsStore(context: Context) : RecentReactionsStore {
    private val dataStore = context.applicationContext.reactionsDataStore

    override val recent: Flow<List<String>> = dataStore.data.map { prefs -> prefs[RecentKey].toReactions() }

    override suspend fun record(reaction: String) {
        dataStore.edit { prefs ->
            prefs[RecentKey] = recordRecent(prefs[RecentKey].toReactions(), reaction).joinToString(SEPARATOR)
        }
    }

    private fun String?.toReactions(): List<String> = orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

    private companion object {
        const val SEPARATOR = "\n"
        val RecentKey = stringPreferencesKey("recent")
    }
}

package com.fortind.zenith.auth

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class ScopeStamp(
    val granted: Set<String>? = null,
    val bannerDismissedFor: Set<String> = emptySet(),
) {
    fun covers(scopes: Collection<String>): Boolean = granted?.containsAll(scopes) == true

    fun bannerWanted(userId: String, scopes: Collection<String>): Boolean = !covers(scopes) && userId !in bannerDismissedFor
}

interface ScopeStampStore {
    val stamp: Flow<ScopeStamp>

    suspend fun grant(scopes: Set<String>)

    suspend fun markStale()

    suspend fun dismissBanner(userId: String)
}

class InMemoryScopeStampStore(initial: ScopeStamp = ScopeStamp()) : ScopeStampStore {
    private val state = MutableStateFlow(initial)

    override val stamp: Flow<ScopeStamp> = state

    override suspend fun grant(scopes: Set<String>) = state.update { it.copy(granted = scopes) }

    override suspend fun markStale() = state.update { it.copy(granted = null) }

    override suspend fun dismissBanner(userId: String) = state.update { it.copy(bannerDismissedFor = it.bannerDismissedFor + userId) }
}

private val Context.scopeStampDataStore by preferencesDataStore(
    name = "scope_stamp",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreScopeStampStore(context: Context) : ScopeStampStore {
    private val dataStore = context.applicationContext.scopeStampDataStore

    override val stamp: Flow<ScopeStamp> = dataStore.data.map { prefs ->
        ScopeStamp(granted = prefs[GrantedKey], bannerDismissedFor = prefs[DismissedKey].orEmpty())
    }

    override suspend fun grant(scopes: Set<String>) {
        dataStore.edit { it[GrantedKey] = scopes }
    }

    override suspend fun markStale() {
        dataStore.edit { it.remove(GrantedKey) }
    }

    override suspend fun dismissBanner(userId: String) {
        dataStore.edit { it[DismissedKey] = it[DismissedKey].orEmpty() + userId }
    }

    private companion object {
        val GrantedKey = stringSetPreferencesKey("granted")
        val DismissedKey = stringSetPreferencesKey("banner_dismissed_for")
    }
}

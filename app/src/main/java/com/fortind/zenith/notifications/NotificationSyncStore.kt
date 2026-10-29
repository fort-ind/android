package com.fortind.zenith.notifications

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

interface NotificationSyncStore {
    suspend fun lastSeenMillis(): Long
    suspend fun advanceLastSeenMillis(millis: Long)
    suspend fun clear()
}

private val Context.notificationSyncDataStore by preferencesDataStore(
    name = "notification_sync",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreNotificationSyncStore(context: Context) : NotificationSyncStore {
    private val dataStore = context.applicationContext.notificationSyncDataStore

    override suspend fun lastSeenMillis(): Long = dataStore.data.first()[LastSeenKey] ?: 0L

    override suspend fun advanceLastSeenMillis(millis: Long) {
        dataStore.edit { prefs ->
            if (millis > (prefs[LastSeenKey] ?: 0L)) prefs[LastSeenKey] = millis
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(LastSeenKey) }
    }

    private companion object {
        val LastSeenKey = longPreferencesKey("last_seen_millis")
    }
}

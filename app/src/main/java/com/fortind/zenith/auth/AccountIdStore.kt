package com.fortind.zenith.auth

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

interface AccountIdStore {
    suspend fun read(): String?

    suspend fun write(userId: String)

    suspend fun clear()
}

class InMemoryAccountIdStore(private var userId: String? = null) : AccountIdStore {
    override suspend fun read(): String? = userId

    override suspend fun write(userId: String) {
        this.userId = userId
    }

    override suspend fun clear() {
        userId = null
    }
}

private val Context.accountIdDataStore by preferencesDataStore(
    name = "account_id",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreAccountIdStore(context: Context) : AccountIdStore {
    private val dataStore = context.applicationContext.accountIdDataStore

    override suspend fun read(): String? = dataStore.data.first()[UserIdKey]

    override suspend fun write(userId: String) {
        dataStore.edit { it[UserIdKey] = userId }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(UserIdKey) }
    }

    private companion object {
        val UserIdKey = stringPreferencesKey("user_id")
    }
}

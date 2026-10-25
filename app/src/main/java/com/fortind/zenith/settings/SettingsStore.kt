package com.fortind.zenith.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
}

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    // Without this, a corrupt file would throw on every launch.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[ThemeModeKey]
                ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[DynamicColorKey] ?: true,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[ThemeModeKey] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DynamicColorKey] = enabled }
    }

    private companion object {
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val DynamicColorKey = booleanPreferencesKey("dynamic_color")
    }
}

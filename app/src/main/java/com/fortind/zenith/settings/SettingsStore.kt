package com.fortind.zenith.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fortind.zenith.notifications.DefaultNotificationCategories
import com.fortind.zenith.notifications.NotificationCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val showHomeFavorites: Boolean = true,
    val homeFavoritesExpanded: Boolean = true,
    val systemNotifications: Boolean = false,
    val notificationCategories: Set<NotificationCategory> = DefaultNotificationCategories,
)

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setShowHomeFavorites(show: Boolean)
    suspend fun setHomeFavoritesExpanded(expanded: Boolean)
    suspend fun setSystemNotifications(enabled: Boolean)
    suspend fun setNotificationCategory(category: NotificationCategory, enabled: Boolean)
}

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
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
            showHomeFavorites = prefs[ShowHomeFavoritesKey] ?: true,
            homeFavoritesExpanded = prefs[HomeFavoritesExpandedKey] ?: true,
            systemNotifications = prefs[SystemNotificationsKey] ?: false,
            notificationCategories = prefs[NotificationCategoriesKey]
                ?.mapNotNullTo(mutableSetOf()) { saved -> NotificationCategory.entries.firstOrNull { it.name == saved } }
                ?: DefaultNotificationCategories,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[ThemeModeKey] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DynamicColorKey] = enabled }
    }

    override suspend fun setShowHomeFavorites(show: Boolean) {
        dataStore.edit { it[ShowHomeFavoritesKey] = show }
    }

    override suspend fun setHomeFavoritesExpanded(expanded: Boolean) {
        dataStore.edit { it[HomeFavoritesExpandedKey] = expanded }
    }

    override suspend fun setSystemNotifications(enabled: Boolean) {
        dataStore.edit { it[SystemNotificationsKey] = enabled }
    }

    override suspend fun setNotificationCategory(category: NotificationCategory, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[NotificationCategoriesKey] ?: DefaultNotificationCategories.map { it.name }.toSet()
            prefs[NotificationCategoriesKey] = if (enabled) current + category.name else current - category.name
        }
    }

    private companion object {
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val DynamicColorKey = booleanPreferencesKey("dynamic_color")
        val ShowHomeFavoritesKey = booleanPreferencesKey("show_home_favorites")
        val HomeFavoritesExpandedKey = booleanPreferencesKey("home_favorites_expanded")
        val SystemNotificationsKey = booleanPreferencesKey("system_notifications")
        val NotificationCategoriesKey = stringSetPreferencesKey("notification_categories")
    }
}

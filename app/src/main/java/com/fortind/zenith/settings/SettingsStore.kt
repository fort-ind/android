package com.fortind.zenith.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fortind.zenith.notifications.DefaultNotificationCategories
import com.fortind.zenith.notifications.NotificationCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class WindowSizeDp(val widthDp: Int, val heightDp: Int)

data class WindowBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun encode(): String = "$left,$top,$right,$bottom"

    companion object {
        fun decode(encoded: String): WindowBounds? {
            val parts = encoded.split(',').map { it.toIntOrNull() ?: return null }
            if (parts.size != 4 || parts[2] <= parts[0] || parts[3] <= parts[1]) return null
            return WindowBounds(parts[0], parts[1], parts[2], parts[3])
        }
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val showHomeFavorites: Boolean = true,
    val homeFavoritesExpanded: Boolean = true,
    val systemNotifications: Boolean = false,
    val notificationCategories: Set<NotificationCategory> = DefaultNotificationCategories,
    val compressUploads: Boolean = false,
    val railCollapsed: Boolean = false,
    val composerWindowSize: WindowSizeDp? = null,
    val popOutBounds: Map<String, WindowBounds> = emptyMap(),
)

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setShowHomeFavorites(show: Boolean)
    suspend fun setHomeFavoritesExpanded(expanded: Boolean)
    suspend fun setSystemNotifications(enabled: Boolean)
    suspend fun setNotificationCategory(category: NotificationCategory, enabled: Boolean)
    suspend fun setCompressUploads(enabled: Boolean)
    suspend fun setRailCollapsed(collapsed: Boolean)
    suspend fun setComposerWindowSize(size: WindowSizeDp)
    suspend fun setPopOutBounds(tab: String, bounds: WindowBounds)
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
            compressUploads = prefs[CompressUploadsKey] ?: false,
            railCollapsed = prefs[RailCollapsedKey] ?: false,
            composerWindowSize = prefs[ComposerWindowWidthKey]?.let { width ->
                prefs[ComposerWindowHeightKey]?.let { height -> WindowSizeDp(width, height) }
            },
            popOutBounds = prefs[PopOutBoundsKey].orEmpty().mapNotNull { entry ->
                val tab = entry.substringBefore('=')
                WindowBounds.decode(entry.substringAfter('='))?.let { tab to it }
            }.toMap(),
        )
    }

    override suspend fun setCompressUploads(enabled: Boolean) {
        dataStore.edit { it[CompressUploadsKey] = enabled }
    }

    override suspend fun setRailCollapsed(collapsed: Boolean) {
        dataStore.edit { it[RailCollapsedKey] = collapsed }
    }

    override suspend fun setComposerWindowSize(size: WindowSizeDp) {
        dataStore.edit {
            it[ComposerWindowWidthKey] = size.widthDp
            it[ComposerWindowHeightKey] = size.heightDp
        }
    }

    override suspend fun setPopOutBounds(tab: String, bounds: WindowBounds) {
        dataStore.edit { prefs ->
            val others = prefs[PopOutBoundsKey].orEmpty().filterNot { it.substringBefore('=') == tab }
            prefs[PopOutBoundsKey] = others.toSet() + "$tab=${bounds.encode()}"
        }
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
        val CompressUploadsKey = booleanPreferencesKey("compress_uploads")
        val RailCollapsedKey = booleanPreferencesKey("rail_collapsed")
        val ComposerWindowWidthKey = intPreferencesKey("composer_window_width_dp")
        val ComposerWindowHeightKey = intPreferencesKey("composer_window_height_dp")
        val PopOutBoundsKey = stringSetPreferencesKey("pop_out_bounds")
    }
}

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
import com.fortind.zenith.ink.SheetBackground
import com.fortind.zenith.ink.SheetShape
import com.fortind.zenith.media.CaptureMode
import com.fortind.zenith.media.VideoOptions
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
    val cameraMode: CaptureMode = CaptureMode.Auto,
    val cameraVideoOptions: VideoOptions = VideoOptions(),
    val railCollapsed: Boolean = false,
    val listPaneAnchor: Int? = null,
    val composerWindowSize: WindowSizeDp? = null,
    val popOutBounds: Map<String, WindowBounds> = emptyMap(),
    val inkDrawWithMouse: Boolean = true,
    val inkTouchStopsAfterPen: Boolean = true,
    val drawingShape: SheetShape = SheetShape.Landscape,
    val drawingBackground: SheetBackground = SheetBackground.White,
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
    suspend fun setCameraMode(mode: CaptureMode)
    suspend fun setCameraVideoOptions(options: VideoOptions)
    suspend fun setRailCollapsed(collapsed: Boolean)
    suspend fun setListPaneAnchor(index: Int)
    suspend fun setComposerWindowSize(size: WindowSizeDp)
    suspend fun setPopOutBounds(tab: String, bounds: WindowBounds)
    suspend fun setInkDrawWithMouse(enabled: Boolean)
    suspend fun setInkTouchStopsAfterPen(enabled: Boolean)
    suspend fun setDrawingShape(shape: SheetShape)
    suspend fun setDrawingBackground(background: SheetBackground)
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
            cameraMode = prefs[CameraModeKey]
                ?.let { saved -> CaptureMode.entries.firstOrNull { it.name == saved } }
                ?: CaptureMode.Auto,
            cameraVideoOptions = VideoOptions(
                fps60 = prefs[CameraFps60Key] ?: false,
                stabilize = prefs[CameraStabilizeKey] ?: false,
                lowLight = prefs[CameraLowLightKey] ?: false,
            ),
            railCollapsed = prefs[RailCollapsedKey] ?: false,
            listPaneAnchor = prefs[ListPaneAnchorKey],
            composerWindowSize = prefs[ComposerWindowWidthKey]?.let { width ->
                prefs[ComposerWindowHeightKey]?.let { height -> WindowSizeDp(width, height) }
            },
            popOutBounds = prefs[PopOutBoundsKey].orEmpty().mapNotNull { entry ->
                val tab = entry.substringBefore('=')
                WindowBounds.decode(entry.substringAfter('='))?.let { tab to it }
            }.toMap(),
            inkDrawWithMouse = prefs[InkDrawWithMouseKey] ?: true,
            inkTouchStopsAfterPen = prefs[InkTouchStopsAfterPenKey] ?: true,
            drawingShape = prefs[DrawingShapeKey]
                ?.let { saved -> SheetShape.entries.firstOrNull { it.name == saved } }
                ?: SheetShape.Landscape,
            drawingBackground = prefs[DrawingBackgroundKey]
                ?.let { saved -> SheetBackground.entries.firstOrNull { it.name == saved } }
                ?: SheetBackground.White,
        )
    }

    override suspend fun setInkDrawWithMouse(enabled: Boolean) {
        dataStore.edit { it[InkDrawWithMouseKey] = enabled }
    }

    override suspend fun setInkTouchStopsAfterPen(enabled: Boolean) {
        dataStore.edit { it[InkTouchStopsAfterPenKey] = enabled }
    }

    override suspend fun setDrawingShape(shape: SheetShape) {
        dataStore.edit { it[DrawingShapeKey] = shape.name }
    }

    override suspend fun setDrawingBackground(background: SheetBackground) {
        dataStore.edit { it[DrawingBackgroundKey] = background.name }
    }

    override suspend fun setCompressUploads(enabled: Boolean) {
        dataStore.edit { it[CompressUploadsKey] = enabled }
    }

    override suspend fun setCameraMode(mode: CaptureMode) {
        dataStore.edit { it[CameraModeKey] = mode.name }
    }

    override suspend fun setCameraVideoOptions(options: VideoOptions) {
        dataStore.edit {
            it[CameraFps60Key] = options.fps60
            it[CameraStabilizeKey] = options.stabilize
            it[CameraLowLightKey] = options.lowLight
        }
    }

    override suspend fun setRailCollapsed(collapsed: Boolean) {
        dataStore.edit { it[RailCollapsedKey] = collapsed }
    }

    override suspend fun setListPaneAnchor(index: Int) {
        dataStore.edit { it[ListPaneAnchorKey] = index }
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
        val CameraModeKey = stringPreferencesKey("camera_mode")
        val CameraFps60Key = booleanPreferencesKey("camera_fps60")
        val CameraStabilizeKey = booleanPreferencesKey("camera_stabilize")
        val CameraLowLightKey = booleanPreferencesKey("camera_low_light")
        val RailCollapsedKey = booleanPreferencesKey("rail_collapsed")
        val ListPaneAnchorKey = intPreferencesKey("list_pane_anchor")
        val ComposerWindowWidthKey = intPreferencesKey("composer_window_width_dp")
        val ComposerWindowHeightKey = intPreferencesKey("composer_window_height_dp")
        val PopOutBoundsKey = stringSetPreferencesKey("pop_out_bounds")
        val InkDrawWithMouseKey = booleanPreferencesKey("ink_draw_with_mouse")
        val InkTouchStopsAfterPenKey = booleanPreferencesKey("ink_touch_stops_after_pen")
        val DrawingShapeKey = stringPreferencesKey("drawing_shape")
        val DrawingBackgroundKey = stringPreferencesKey("drawing_background")
    }
}

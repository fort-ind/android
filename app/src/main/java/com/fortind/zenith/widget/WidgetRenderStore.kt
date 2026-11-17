package com.fortind.zenith.widget

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fortind.zenith.settings.AppIcon
import kotlinx.coroutines.flow.first

data class WidgetRenderStamp(val signedIn: Boolean, val icon: AppIcon, val appVersion: Int)

fun needsRender(rendered: WidgetRenderStamp?, current: WidgetRenderStamp): Boolean = rendered != current

interface WidgetRenderStore {
    suspend fun rendered(): WidgetRenderStamp?

    suspend fun remember(stamp: WidgetRenderStamp)
}

private val Context.widgetRenderDataStore by preferencesDataStore(
    name = "widget_render",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreWidgetRenderStore(context: Context) : WidgetRenderStore {
    private val dataStore = context.applicationContext.widgetRenderDataStore

    override suspend fun rendered(): WidgetRenderStamp? {
        val prefs = dataStore.data.first()
        val signedIn = prefs[SignedInKey] ?: return null
        val icon = AppIcon.entries.firstOrNull { it.name == prefs[IconKey] } ?: return null
        val appVersion = prefs[AppVersionKey] ?: return null
        return WidgetRenderStamp(signedIn, icon, appVersion)
    }

    override suspend fun remember(stamp: WidgetRenderStamp) {
        dataStore.edit { prefs ->
            prefs[SignedInKey] = stamp.signedIn
            prefs[IconKey] = stamp.icon.name
            prefs[AppVersionKey] = stamp.appVersion
        }
    }

    private companion object {
        val SignedInKey = booleanPreferencesKey("signed_in")
        val IconKey = stringPreferencesKey("icon")
        val AppVersionKey = intPreferencesKey("app_version")
    }
}

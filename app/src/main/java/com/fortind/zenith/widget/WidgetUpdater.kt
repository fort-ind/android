package com.fortind.zenith.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.setWidgetPreviews
import androidx.glance.appwidget.updateAll
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.settings.AppIconSwitcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "WidgetUpdater"

class WidgetUpdater(
    context: Context,
    private val session: UserSessionService,
    private val appIcons: AppIconSwitcher,
) {
    private val context = context.applicationContext

    fun start(scope: CoroutineScope) {
        scope.launch { renderInputs().collect { refresh() } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            scope.launch { publishPreview() }
        }
    }

    private fun renderInputs() = combine(
        session.state.filter { it != SessionState.Restoring }.map { it is SessionState.SignedIn }.distinctUntilChanged(),
        appIcons.selected,
    ) { signedIn, icon -> signedIn to icon }.distinctUntilChanged()

    private suspend fun refresh() {
        try {
            ToolbarWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not refresh the widget", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun publishPreview() {
        try {
            val provider = AppWidgetManager.getInstance(context)
                .getInstalledProvidersForPackage(context.packageName, null)
                .firstOrNull { it.provider.className == ToolbarWidgetReceiver::class.java.name }
                ?: return
            if (provider.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0) return
            val result = GlanceAppWidgetManager(context).setWidgetPreviews<ToolbarWidgetReceiver>()
            if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED) {
                Log.w(TAG, "Preview not published, rate limited")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not publish the widget preview", e)
        }
    }
}

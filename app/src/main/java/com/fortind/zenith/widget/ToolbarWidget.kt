package com.fortind.zenith.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.fortind.zenith.AppContainer
import com.fortind.zenith.R
import com.fortind.zenith.ZenithApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ToolbarWidget"

private val PreviewSizes = setOf(
    DpSize(120.dp, 115.dp),
    DpSize(128.dp, 128.dp),
    DpSize(128.dp, 188.dp),
    DpSize(240.dp, 188.dp),
)

class ToolbarWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(PreviewSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ZenithApplication).container
        val initial = load(container)
        provideContent {
            var load by remember { mutableStateOf(initial) }
            val scope = rememberCoroutineScope()
            val session by container.sessionService.state.collectAsState()
            val appIcon by container.appIconSwitcher.selected.collectAsState()
            WidgetTheme(appIcon) {
                val signedIn = signedInFrom(session, load)
                if (signedIn == null) {
                    WidgetLoadError(
                        labels = errorLabels(context),
                        onRetry = action { scope.launch { load = load(container) } },
                        onOpenApp = actionStartActivity(WidgetIntents.openApp(context)),
                    )
                } else {
                    val composerOptions = remember(load) {
                        WidgetIntents.composerOptions(context, (load as? WidgetLoad.Ready)?.composerSize)
                    }
                    ToolbarWidgetContent(signedIn, toolbarLabels(context), toolbarActions(context, composerOptions))
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val container = (context.applicationContext as ZenithApplication).container
        val appIcon = container.appIconSwitcher.current()
        provideContent {
            WidgetTheme(appIcon) {
                ToolbarWidgetContent(signedIn = true, toolbarLabels(context), toolbarActions(context, composerOptions = null))
            }
        }
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        Log.e(TAG, "Composition failed, showing the fallback layout", throwable)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            WidgetIntents.fallbackOpenApp(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val fallback = RemoteViews(context.packageName, R.layout.widget_toolbar_error)
        fallback.setOnClickPendingIntent(android.R.id.background, openApp)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
    }

    private suspend fun load(container: AppContainer): WidgetLoad {
        val result = loadWidget(
            readToken = { container.sessionService.token() },
            readComposerSize = { container.settingsStore.settings.first().composerWindowSize },
        )
        if (result is WidgetLoad.Failed) Log.w(TAG, "Could not read the sign-in state", result.cause)
        return result
    }
}

class ToolbarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ToolbarWidget()
}

internal fun toolbarLabels(context: Context) = ToolbarLabels(
    search = context.getString(R.string.search_placeholder),
    post = context.getString(R.string.widget_new_post),
    notifications = context.getString(R.string.nav_notifications),
    profile = context.getString(R.string.nav_profile),
    games = context.getString(R.string.nav_games),
    signIn = context.getString(R.string.widget_sign_in),
    signInDescription = context.getString(R.string.widget_sign_in_description),
)

internal fun errorLabels(context: Context) = ErrorLabels(
    title = context.getString(R.string.widget_error_title),
    retry = context.getString(R.string.feed_retry),
    retryDescription = context.getString(R.string.widget_error_retry_description),
    openApp = context.getString(R.string.widget_error_open_app),
)

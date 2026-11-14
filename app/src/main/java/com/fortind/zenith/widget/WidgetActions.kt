package com.fortind.zenith.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.view.Display
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.fortind.zenith.ComposerWindowActivity
import com.fortind.zenith.MainActivity
import com.fortind.zenith.settings.WindowSizeDp
import com.fortind.zenith.ui.nav.AppDestination
import com.fortind.zenith.ui.nav.ShellRequest
import com.fortind.zenith.window.AppWindows
import com.fortind.zenith.window.ComposerWindowTarget

private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
private const val OPEN_APP_REQUEST_CODE = 100

object WidgetIntents {
    const val ACTION_RETRY = "com.fortind.zenith.widget.action.RETRY"

    private const val FALLBACK_SCHEME = "zenith-widget"
    private const val FALLBACK_OPEN = "open"

    fun openApp(context: Context): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun fallbackOpenApp(context: Context): Intent =
        openApp(context).setData(Uri.Builder().scheme(FALLBACK_SCHEME).authority(FALLBACK_OPEN).build())

    fun shell(context: Context, request: ShellRequest): Intent =
        openApp(context).putExtra(MainActivity.EXTRA_SHELL_REQUEST, request.name)

    fun tab(context: Context, destination: AppDestination): Intent =
        openApp(context).putExtra(MainActivity.EXTRA_TAB, destination.name)

    fun retry(context: Context): Intent = Intent(context, ToolbarWidgetReceiver::class.java).setAction(ACTION_RETRY)

    fun composer(context: Context): Intent = Intent(context, ComposerWindowActivity::class.java)
        .setData(AppWindows.composerUri(ComposerWindowTarget.NewPost))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)

    fun composerOptions(context: Context, size: WindowSizeDp?): Bundle? {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val metrics = context.createDisplayContext(display).resources.displayMetrics
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return null
        return AppWindows.composerLaunchOptions(Rect(0, 0, metrics.widthPixels, metrics.heightPixels), metrics.density, size)
    }
}

class WidgetTap(private val glanceAction: () -> Action, private val pending: (Context) -> PendingIntent) {
    fun action(): Action = glanceAction()

    fun pendingIntent(context: Context): PendingIntent = pending(context)
}

@OptIn(ExperimentalGlanceApi::class)
fun activityTap(intent: Intent, requestCode: Int, options: Bundle? = null): WidgetTap = WidgetTap(
    glanceAction = { actionStartActivity(Intent(intent), activityOptions = options) },
    pending = { context -> PendingIntent.getActivity(context, requestCode, Intent(intent), PENDING_FLAGS, options) },
)

fun openAppTap(context: Context): WidgetTap = activityTap(WidgetIntents.openApp(context), OPEN_APP_REQUEST_CODE)

fun retryTap(context: Context, inSession: Action): WidgetTap = WidgetTap(
    glanceAction = { inSession },
    pending = { PendingIntent.getBroadcast(it, 0, WidgetIntents.retry(context), PENDING_FLAGS) },
)

fun toolbarActions(context: Context, composerOptions: Bundle?): ToolbarActions = ToolbarActions(
    search = activityTap(WidgetIntents.shell(context, ShellRequest.SEARCH), ToolbarItem.Search.ordinal),
    post = activityTap(WidgetIntents.composer(context), ToolbarItem.Post.ordinal, composerOptions),
    notifications = activityTap(WidgetIntents.tab(context, AppDestination.NOTIFICATIONS), ToolbarItem.Notifications.ordinal),
    profile = activityTap(WidgetIntents.tab(context, AppDestination.PROFILE), ToolbarItem.Profile.ordinal),
    games = activityTap(WidgetIntents.tab(context, AppDestination.GAMES), ToolbarItem.Games.ordinal),
    signIn = activityTap(WidgetIntents.shell(context, ShellRequest.SIGN_IN), ToolbarItem.SignIn.ordinal),
)

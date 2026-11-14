package com.fortind.zenith.widget

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.view.Display
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.action.actionStartActivity
import com.fortind.zenith.ComposerWindowActivity
import com.fortind.zenith.MainActivity
import com.fortind.zenith.settings.WindowSizeDp
import com.fortind.zenith.ui.nav.AppDestination
import com.fortind.zenith.ui.nav.ShellRequest
import com.fortind.zenith.window.AppWindows
import com.fortind.zenith.window.ComposerWindowTarget

object WidgetIntents {
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

@OptIn(ExperimentalGlanceApi::class)
fun toolbarActions(context: Context, composerOptions: Bundle?): ToolbarActions = ToolbarActions(
    search = actionStartActivity(WidgetIntents.shell(context, ShellRequest.SEARCH)),
    post = actionStartActivity(WidgetIntents.composer(context), activityOptions = composerOptions),
    notifications = actionStartActivity(WidgetIntents.tab(context, AppDestination.NOTIFICATIONS)),
    profile = actionStartActivity(WidgetIntents.tab(context, AppDestination.PROFILE)),
    games = actionStartActivity(WidgetIntents.tab(context, AppDestination.GAMES)),
    signIn = actionStartActivity(WidgetIntents.shell(context, ShellRequest.SIGN_IN)),
)

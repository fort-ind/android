package com.fortind.zenith.window

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.fortind.zenith.ComposerWindowActivity
import com.fortind.zenith.MainActivity
import com.fortind.zenith.ui.nav.AppDestination
import com.fortind.zenith.settings.WindowBounds
import com.fortind.zenith.settings.WindowSizeDp
import com.fortind.zenith.ui.nav.AppRoute
import kotlin.math.roundToInt

sealed interface ComposerWindowTarget {
    data object NewPost : ComposerWindowTarget

    data class Edit(val noteId: String) : ComposerWindowTarget
}

object AppWindows {
    const val EXTRA_OPENER_WINDOW_ID = "com.fortind.zenith.extra.OPENER_WINDOW_ID"

    private const val SCHEME = "zenith-window"
    private const val COMPOSE = "compose"
    private const val NEW_POST = "new"
    private const val EDIT = "edit"
    private const val TAB = "tab"
    private const val CASCADE_STEP_DP = 48

    private val DefaultComposerSize = WindowSizeDp(widthDp = 560, heightDp = 640)

    val PoppableTabs: List<AppDestination> = AppDestination.entries - AppDestination.SETTINGS

    fun composerUri(target: ComposerWindowTarget): Uri {
        val builder = Uri.Builder().scheme(SCHEME).authority(COMPOSE)
        when (target) {
            ComposerWindowTarget.NewPost -> builder.appendPath(NEW_POST)
            is ComposerWindowTarget.Edit -> builder.appendPath(EDIT).appendPath(target.noteId)
        }
        return builder.build()
    }

    fun composerTarget(uri: Uri?): ComposerWindowTarget? {
        if (uri?.scheme != SCHEME || uri.authority != COMPOSE) return null
        val segments = uri.pathSegments
        return when {
            segments == listOf(NEW_POST) -> ComposerWindowTarget.NewPost
            segments.size == 2 && segments[0] == EDIT && AppRoute.isValidNoteId(segments[1]) -> ComposerWindowTarget.Edit(segments[1])
            else -> null
        }
    }

    fun openComposer(opener: Activity, target: ComposerWindowTarget, openerWindowId: String, size: WindowSizeDp?) {
        val intent = Intent(opener, ComposerWindowActivity::class.java)
            .setData(composerUri(target))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            .putExtra(EXTRA_OPENER_WINDOW_ID, openerWindowId)
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            composerLaunchOptions(opener.windowManager.maximumWindowMetrics.bounds, opener.resources.displayMetrics.density, size)
        } else {
            null
        }
        opener.startActivity(intent, options)
    }

    fun composerLaunchOptions(display: Rect, density: Float, size: WindowSizeDp?): Bundle {
        val wanted = size ?: DefaultComposerSize
        val width = (wanted.widthDp * density).roundToInt().coerceAtMost(display.width())
        val height = (wanted.heightDp * density).roundToInt().coerceAtMost(display.height())
        val left = display.left + (display.width() - width) / 2
        val top = display.top + (display.height() - height) / 2
        return ActivityOptions.makeBasic().setLaunchBounds(Rect(left, top, left + width, top + height)).toBundle()
    }

    fun tabUri(destination: AppDestination): Uri =
        Uri.Builder().scheme(SCHEME).authority(TAB).appendPath(destination.name).build()

    fun poppedOutTab(uri: Uri?): AppDestination? {
        if (uri?.scheme != SCHEME || uri.authority != TAB) return null
        val name = uri.pathSegments.singleOrNull() ?: return null
        return PoppableTabs.firstOrNull { it.name == name }
    }

    fun openTab(opener: Activity, destination: AppDestination, route: AppRoute? = null, remembered: WindowBounds? = null) {
        val intent = Intent(opener, MainActivity::class.java)
            .setData(tabUri(destination))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        route?.let { intent.putExtra(MainActivity.EXTRA_ROUTE, it.encode()) }
        val placeNew = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !tabWindowExists(opener, destination)
        val options = if (placeNew) {
            ActivityOptions.makeBasic().setLaunchBounds(popOutBounds(opener, remembered)).toBundle()
        } else {
            null
        }
        opener.startActivity(intent, options)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun popOutBounds(opener: Activity, remembered: WindowBounds?): Rect {
        val display = opener.windowManager.maximumWindowMetrics.bounds
        remembered?.let { Rect(it.left, it.top, it.right, it.bottom) }?.takeIf { display.contains(it) }?.let { return it }
        val step = (CASCADE_STEP_DP * opener.resources.displayMetrics.density).roundToInt()
        val bounds = Rect(opener.windowManager.currentWindowMetrics.bounds)
        bounds.offset(step, step)
        if (bounds.right > display.right || bounds.bottom > display.bottom) bounds.offset(-2 * step, -2 * step)
        val width = minOf(bounds.width(), display.width())
        val height = minOf(bounds.height(), display.height())
        val left = bounds.left.coerceIn(display.left, display.right - width)
        val top = bounds.top.coerceIn(display.top, display.bottom - height)
        return Rect(left, top, left + width, top + height)
    }

    fun currentBounds(activity: Activity): WindowBounds? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !activity.isInMultiWindowMode) return null
        val bounds = activity.windowManager.currentWindowMetrics.bounds
        return WindowBounds(bounds.left, bounds.top, bounds.right, bounds.bottom).takeIf { bounds.width() > 0 && bounds.height() > 0 }
    }

    fun tabWindowExists(context: Context, destination: AppDestination): Boolean {
        val uri = tabUri(destination)
        val tasks = context.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty()
        return tasks.any { task ->
            try {
                task.taskInfo?.baseIntent?.data == uri
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }

    fun setWindowLabel(activity: Activity, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(label).build())
        }
    }
}

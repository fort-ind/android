package com.fortind.zenith.ui.media

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Stable
class WindowFullscreenState internal constructor(private val activity: Activity?, initiallyActive: Boolean = false) {
    var active by mutableStateOf(initiallyActive)
        private set

    internal var multiWindow by mutableStateOf(false)

    private var windows: List<Window> = emptyList()

    val canToggle: Boolean
        get() = active || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && multiWindow)

    fun toggle() {
        if (active) exit() else enter()
    }

    fun enter() {
        val host = activity ?: return
        if (active || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || !host.isInMultiWindowMode) return
        host.requestFullscreenMode(Activity.FULLSCREEN_MODE_REQUEST_ENTER, null)
        active = true
        applyBars()
    }

    fun exit() {
        if (!active) return
        active = false
        applyBars()
        val host = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) host.requestFullscreenMode(Activity.FULLSCREEN_MODE_REQUEST_EXIT, null)
    }

    internal fun attach(windows: List<Window>) {
        val changed = windows != this.windows
        this.windows = windows
        if (changed && active) applyBars()
    }

    private fun applyBars() {
        windows.forEach { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                if (active) {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    internal companion object {
        fun saver(activity: Activity?): Saver<WindowFullscreenState, Boolean> = Saver(
            save = { it.active },
            restore = { WindowFullscreenState(activity, initiallyActive = it) },
        )
    }
}

@Composable
fun rememberWindowFullscreenState(): WindowFullscreenState {
    val activity = LocalActivity.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val state = rememberSaveable(activity, saver = WindowFullscreenState.saver(activity)) { WindowFullscreenState(activity) }
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    val multiWindow = remember(activity, configuration) { activity?.isInMultiWindowMode == true }
    SideEffect {
        state.multiWindow = multiWindow
        state.attach(listOfNotNull(activity?.window, dialogWindow))
    }
    DisposableEffect(state) {
        onDispose { if (activity?.isChangingConfigurations != true) state.exit() }
    }
    return state
}

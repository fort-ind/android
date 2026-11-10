package com.fortind.zenith

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.ui.CustomTabUriHandler
import com.fortind.zenith.ui.ZoomableContent
import com.fortind.zenith.ui.login.LoginScreen
import com.fortind.zenith.ui.nav.AppDestination
import com.fortind.zenith.ui.nav.AppRoute
import com.fortind.zenith.ui.nav.AppShell
import com.fortind.zenith.ui.nav.AppShortcutDispatcher
import com.fortind.zenith.ui.nav.ShellWindow
import com.fortind.zenith.ui.nav.destinationKeyCode
import com.fortind.zenith.ui.theme.SettingsTheme
import com.fortind.zenith.ui.theme.applySystemBarStyle
import com.fortind.zenith.ui.theme.isThemeDark
import com.fortind.zenith.window.AppWindows
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestedDestination = mutableStateOf<AppDestination?>(null)
    private val requestedRoute = mutableStateOf<AppRoute?>(null)
    private val requestedThreadId = mutableStateOf<String?>(null)
    private val requestedReplyId = mutableStateOf<String?>(null)
    private val shortcuts = AppShortcutDispatcher()

    private val container get() = (application as ZenithApplication).container

    private val poppedOutTab: AppDestination? by lazy { AppWindows.poppedOutTab(intent.data) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        poppedOutTab?.let { tab ->
            AppWindows.setWindowLabel(this, getString(tab.labelRes))
            lifecycleScope.launch {
                container.sessionService.state.first { it == SessionState.SignedOut }
                finish()
            }
        }
        setContent {
            SettingsTheme(onDarkTheme = { applySystemBarStyle(it) }) {
                ZenithRoot(
                    window = ShellWindow(
                        poppedOutTab = poppedOutTab,
                        onLeaveTab = ::leaveTab,
                        onOpenTabWindow = { destination, remembered -> AppWindows.openTab(this, destination, remembered = remembered) },
                    ),
                    requestedDestination = requestedDestination.value,
                    onRequestedDestinationHandled = { requestedDestination.value = null },
                    requestedRoute = requestedRoute.value,
                    onRequestedRouteHandled = { requestedRoute.value = null },
                    requestedThreadId = requestedThreadId.value,
                    onRequestedThreadHandled = { requestedThreadId.value = null },
                    requestedReplyId = requestedReplyId.value,
                    onRequestedReplyHandled = { requestedReplyId.value = null },
                    shortcuts = shortcuts,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) rememberPopOutBounds()
    }

    private fun rememberPopOutBounds() {
        val tab = poppedOutTab ?: return
        val bounds = AppWindows.currentBounds(this) ?: return
        container.scope.launch { container.settingsStore.setPopOutBounds(tab.name, bounds) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = shortcuts.dispatch(event) || super.onKeyDown(keyCode, event)

    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        val shortcuts = buildList {
            AppDestination.entries.forEach { destination ->
                add(KeyboardShortcutInfo(getString(destination.labelRes), destinationKeyCode(destination), KeyEvent.META_CTRL_ON))
            }
            add(KeyboardShortcutInfo(getString(R.string.feed_compose), KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON))
            add(KeyboardShortcutInfo(getString(R.string.feed_refresh), KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON))
        }
        data.add(KeyboardShortcutGroup(getString(R.string.app_name), shortcuts))
    }

    override fun onStart() {
        super.onStart()
        container.foregroundTracker.windowStarted()
        container.systemNotifier.clearAll()
    }

    override fun onStop() {
        super.onStop()
        if (container.foregroundTracker.windowStopped()) {
            container.scope.launch { container.notificationSyncer.acknowledgeSeen() }
        }
    }

    private fun leaveTab(destination: AppDestination, route: AppRoute?) {
        if (destination in AppWindows.PoppableTabs && AppWindows.tabWindowExists(this, destination)) {
            AppWindows.openTab(this, destination, route)
            return
        }
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_TAB, destination.name)
        route?.let { intent.putExtra(EXTRA_ROUTE, it.encode()) }
        startActivity(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE)?.let(AppRoute::decode)
        val tab = poppedOutTab ?: intent?.getStringExtra(EXTRA_TAB)?.let { name -> AppDestination.entries.firstOrNull { it.name == name } }
        if (tab != null && (route != null || poppedOutTab == null)) {
            requestedRoute.value = route
            requestedDestination.value = tab
            return
        }
        if (poppedOutTab != null) return
        val noteId = intent?.getStringExtra(EXTRA_THREAD_NOTE_ID)?.takeIf(AppRoute::isValidNoteId)
        if (noteId != null) {
            requestedThreadId.value = noteId
            if (intent.getBooleanExtra(EXTRA_OPEN_REPLY, false)) requestedReplyId.value = noteId
        } else {
            destinationFrom(intent)?.let { requestedDestination.value = it }
        }
    }

    private fun destinationFrom(intent: Intent?): AppDestination? =
        when (intent?.getStringExtra(EXTRA_DESTINATION)) {
            DESTINATION_NOTIFICATIONS -> AppDestination.NOTIFICATIONS
            else -> null
        }

    companion object {
        const val EXTRA_DESTINATION = "com.fortind.zenith.extra.DESTINATION"
        const val EXTRA_THREAD_NOTE_ID = "com.fortind.zenith.extra.THREAD_NOTE_ID"
        const val EXTRA_OPEN_REPLY = "com.fortind.zenith.extra.OPEN_REPLY"
        const val EXTRA_TAB = "com.fortind.zenith.extra.TAB"
        const val EXTRA_ROUTE = "com.fortind.zenith.extra.ROUTE"
        const val DESTINATION_NOTIFICATIONS = "notifications"
    }
}

@Composable
fun ZenithRoot(
    window: ShellWindow,
    requestedDestination: AppDestination?,
    onRequestedDestinationHandled: () -> Unit,
    requestedRoute: AppRoute?,
    onRequestedRouteHandled: () -> Unit,
    requestedThreadId: String?,
    onRequestedThreadHandled: () -> Unit,
    requestedReplyId: String?,
    onRequestedReplyHandled: () -> Unit,
    shortcuts: AppShortcutDispatcher,
) {
    val app = LocalContext.current.applicationContext as ZenithApplication
    val session by app.container.sessionService.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val darkTheme = isThemeDark()
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val uriHandler = remember(context, darkTheme, toolbarColor) { CustomTabUriHandler(context, darkTheme, toolbarColor) }

    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        ZoomableContent(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.captionBar)
                .semantics { testTagsAsResourceId = true }
        ) {
            when (session) {
                SessionState.Restoring -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
                SessionState.SignedOut -> if (window.poppedOutTab == null) LoginScreen(
                    onBrowseWithoutSigningIn = app.container.sessionService::browseAsGuest
                )
                is SessionState.SignedIn, SessionState.Guest -> AppShell(
                    window = window,
                    requestedDestination = requestedDestination,
                    onRequestedDestinationHandled = onRequestedDestinationHandled,
                    requestedRoute = requestedRoute,
                    onRequestedRouteHandled = onRequestedRouteHandled,
                    requestedThreadId = requestedThreadId,
                    onRequestedThreadHandled = onRequestedThreadHandled,
                    requestedReplyId = requestedReplyId,
                    onRequestedReplyHandled = onRequestedReplyHandled,
                    shortcuts = shortcuts,
                )
            }
        }
    }
}

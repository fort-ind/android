package com.fortind.zenith

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import com.fortind.zenith.ui.nav.destinationKeyCode
import com.fortind.zenith.ui.theme.SettingsTheme
import com.fortind.zenith.ui.theme.isThemeDark
import kotlinx.coroutines.launch

private val DEFAULT_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DEFAULT_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    private val requestedDestination = mutableStateOf<AppDestination?>(null)
    private val requestedThreadId = mutableStateOf<String?>(null)
    private val requestedReplyId = mutableStateOf<String?>(null)
    private val shortcuts = AppShortcutDispatcher()

    private val container get() = (application as ZenithApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            SettingsTheme(onDarkTheme = ::applySystemBarStyle) {
                ZenithRoot(
                    requestedDestination = requestedDestination.value,
                    onRequestedDestinationHandled = { requestedDestination.value = null },
                    requestedThreadId = requestedThreadId.value,
                    onRequestedThreadHandled = { requestedThreadId.value = null },
                    requestedReplyId = requestedReplyId.value,
                    onRequestedReplyHandled = { requestedReplyId.value = null },
                    shortcuts = shortcuts,
                )
            }
        }
    }

    private fun applySystemBarStyle(darkTheme: Boolean) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        val navigationStyle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || darkTheme) {
            style
        } else {
            SystemBarStyle.auto(DEFAULT_LIGHT_SCRIM, DEFAULT_DARK_SCRIM) { darkTheme }
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = navigationStyle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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
        container.foregroundTracker.inForeground = true
        container.systemNotifier.clearAll()
    }

    override fun onStop() {
        super.onStop()
        container.foregroundTracker.inForeground = false
        container.scope.launch { container.notificationSyncer.acknowledgeSeen() }
    }

    private fun handleIntent(intent: Intent?) {
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
        const val DESTINATION_NOTIFICATIONS = "notifications"
    }
}

@Composable
fun ZenithRoot(
    requestedDestination: AppDestination?,
    onRequestedDestinationHandled: () -> Unit,
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
                SessionState.SignedOut -> LoginScreen(
                    onBrowseWithoutSigningIn = app.container.sessionService::browseAsGuest
                )
                is SessionState.SignedIn, SessionState.Guest -> AppShell(
                    requestedDestination = requestedDestination,
                    onRequestedDestinationHandled = onRequestedDestinationHandled,
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

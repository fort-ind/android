package com.fortind.zenith

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.ThemeMode
import com.fortind.zenith.ui.games.GamesScreen
import com.fortind.zenith.ui.games.GamesViewModel
import com.fortind.zenith.ui.home.HomeScreen
import com.fortind.zenith.ui.icons.GamesIcon
import com.fortind.zenith.ui.login.LoginScreen
import com.fortind.zenith.ui.notifications.NotificationsScreen
import com.fortind.zenith.ui.notifications.NotificationsViewModel
import com.fortind.zenith.ui.profile.ProfileScreen
import com.fortind.zenith.ui.CustomTabUriHandler
import com.fortind.zenith.ui.ZoomableContent
import com.fortind.zenith.ui.settings.SettingsScreen
import com.fortind.zenith.ui.theme.ZenithTheme
import com.fortind.zenith.ui.theme.isThemeDark
import kotlinx.coroutines.launch

private const val MAX_BADGE_COUNT = 99
private val DEFAULT_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DEFAULT_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    HOME(R.string.nav_home, Icons.Default.Home),
    GAMES(R.string.nav_games, GamesIcon),
    NOTIFICATIONS(R.string.nav_notifications, Icons.Default.Notifications),
    PROFILE(R.string.nav_profile, Icons.Default.AccountCircle),
    SETTINGS(R.string.nav_settings, Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    private val requestedDestination = mutableStateOf<AppDestination?>(null)
    private val requestedThreadId = mutableStateOf<String?>(null)

    private val container get() = (application as ZenithApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val app = LocalContext.current.applicationContext as ZenithApplication
            val settings by app.container.settingsStore.settings
                .collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
            val appIcon by app.container.appIconSwitcher.selected.collectAsStateWithLifecycle()
            val current = settings
            if (current == null) {
                Box(Modifier.fillMaxSize())
            } else {
                val systemDark = isSystemInDarkTheme()
                val darkTheme = when (current.themeMode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                DisposableEffect(darkTheme) {
                    applySystemBarStyle(darkTheme)
                    onDispose {}
                }
                ZenithTheme(
                    darkTheme = darkTheme,
                    dynamicColor = current.dynamicColor,
                    appIcon = appIcon,
                ) {
                    ZenithRoot(
                        requestedDestination = requestedDestination.value,
                        onRequestedDestinationHandled = { requestedDestination.value = null },
                        requestedThreadId = requestedThreadId.value,
                        onRequestedThreadHandled = { requestedThreadId.value = null },
                    )
                }
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
        val noteId = intent?.getStringExtra(EXTRA_THREAD_NOTE_ID)
        if (noteId != null) {
            requestedThreadId.value = noteId
            requestedDestination.value = AppDestination.HOME
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
        const val DESTINATION_NOTIFICATIONS = "notifications"
    }
}

@Composable
fun ZenithRoot(
    requestedDestination: AppDestination?,
    onRequestedDestinationHandled: () -> Unit,
    requestedThreadId: String?,
    onRequestedThreadHandled: () -> Unit,
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
                is SessionState.SignedIn, SessionState.Guest -> ZenithApp(
                    requestedDestination = requestedDestination,
                    onRequestedDestinationHandled = onRequestedDestinationHandled,
                    pendingThreadId = requestedThreadId,
                    onPendingThreadHandled = onRequestedThreadHandled,
                )
            }
        }
    }
}

@Composable
fun ZenithApp(
    requestedDestination: AppDestination?,
    onRequestedDestinationHandled: () -> Unit,
    pendingThreadId: String?,
    onPendingThreadHandled: () -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    var requestedThreadId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentDestination) { if (currentDestination != AppDestination.PROFILE) bookmarksOpen = false }
    val destinationState = rememberSaveableStateHolder()
    val navigationState = rememberNavigationSuiteScaffoldState()
    var immersive by remember { mutableStateOf(false) }
    LaunchedEffect(immersive) { if (immersive) navigationState.hide() else navigationState.show() }
    val gamesViewModel: GamesViewModel = viewModel(factory = GamesViewModel.Factory)
    val notificationsViewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory)
    val unreadCount by notificationsViewModel.unreadCount.collectAsStateWithLifecycle()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    LaunchedEffect(requestedDestination) {
        if (requestedDestination != null) {
            currentDestination = requestedDestination
            onRequestedDestinationHandled()
        }
    }

    LaunchedEffect(pendingThreadId) {
        if (pendingThreadId != null) {
            requestedThreadId = pendingThreadId
            onPendingThreadHandled()
        }
    }

    LifecycleStartEffect(notificationsViewModel) {
        notificationsViewModel.setForeground(true)
        onStopOrDispose { notificationsViewModel.setForeground(false) }
    }

    val layoutType = if (
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        )
    ) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        state = navigationState,
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                val labelRes = if (destination == AppDestination.NOTIFICATIONS) {
                    R.string.nav_notifications_short
                } else {
                    destination.labelRes
                }
                item(
                    selected = currentDestination == destination,
                    onClick = { currentDestination = destination },
                    icon = {
                        if (destination == AppDestination.NOTIFICATIONS && unreadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(
                                            if (unreadCount > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else unreadCount.toString()
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = pluralStringResource(R.plurals.nav_notifications_unread, unreadCount, unreadCount)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes)
                            )
                        }
                    },
                    label = { Text(text = stringResource(labelRes)) }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            destinationState.SaveableStateProvider(currentDestination.name) {
                when (currentDestination) {
                    AppDestination.HOME -> HomeScreen(
                        onBrowseGames = { currentDestination = AppDestination.GAMES },
                        onSeeAllFavorites = {
                            gamesViewModel.showFavorites()
                            currentDestination = AppDestination.GAMES
                        },
                        onImmersiveChange = { immersive = it },
                        modifier = Modifier.padding(innerPadding),
                        requestedThreadId = requestedThreadId,
                        onRequestedThreadHandled = { requestedThreadId = null },
                    )
                    AppDestination.GAMES -> GamesScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = gamesViewModel,
                    )
                    AppDestination.NOTIFICATIONS -> NotificationsScreen(
                        viewModel = notificationsViewModel,
                        onOpenNote = { noteId ->
                            requestedThreadId = noteId
                            currentDestination = AppDestination.HOME
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                    AppDestination.PROFILE -> if (bookmarksOpen) {
                        HomeScreen(
                            onBrowseGames = { currentDestination = AppDestination.GAMES },
                            onSeeAllFavorites = {
                                gamesViewModel.showFavorites()
                                currentDestination = AppDestination.GAMES
                            },
                            onImmersiveChange = { immersive = it },
                            modifier = Modifier.padding(innerPadding),
                            showBookmarks = true,
                            onCloseBookmarks = { bookmarksOpen = false },
                        )
                    } else {
                        ProfileScreen(onOpenBookmarks = { bookmarksOpen = true }, contentPadding = innerPadding)
                    }
                    AppDestination.SETTINGS -> SettingsScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

package com.fortind.zenith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.ThemeMode
import com.fortind.zenith.ui.ComingSoonPage
import com.fortind.zenith.ui.games.GamesScreen
import com.fortind.zenith.ui.games.GamesViewModel
import com.fortind.zenith.ui.home.HomeScreen
import com.fortind.zenith.ui.icons.GamesIcon
import com.fortind.zenith.ui.login.LoginScreen
import com.fortind.zenith.ui.profile.ProfileScreen
import com.fortind.zenith.ui.ZoomableContent
import com.fortind.zenith.ui.settings.SettingsScreen
import com.fortind.zenith.ui.theme.ZenithTheme

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = LocalContext.current.applicationContext as ZenithApplication
            val settings by app.container.settingsStore.settings
                .collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
            val current = settings
            if (current == null) {
                Box(Modifier.fillMaxSize())
            } else {
                val systemDark = isSystemInDarkTheme()
                ZenithTheme(
                    darkTheme = when (current.themeMode) {
                        ThemeMode.SYSTEM -> systemDark
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                    },
                    dynamicColor = current.dynamicColor,
                ) {
                    ZenithRoot()
                }
            }
        }
    }
}

@Composable
fun ZenithRoot() {
    val app = LocalContext.current.applicationContext as ZenithApplication
    val session by app.container.sessionService.state.collectAsStateWithLifecycle()

    ZoomableContent(Modifier.fillMaxSize()) {
        when (session) {
            SessionState.Restoring -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
            SessionState.SignedOut -> LoginScreen(
                onBrowseWithoutSigningIn = app.container.sessionService::browseAsGuest
            )
            is SessionState.SignedIn, SessionState.Guest -> ZenithApp()
        }
    }
}

@Composable
fun ZenithApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    val gamesViewModel: GamesViewModel = viewModel(factory = GamesViewModel.Factory)
    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    val layoutType = if (
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        )
    ) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }

    val isRail = layoutType == NavigationSuiteType.NavigationRail

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                val labelRes = if (isRail && destination == AppDestination.NOTIFICATIONS) {
                    R.string.nav_notifications_short
                } else {
                    destination.labelRes
                }
                item(
                    selected = currentDestination == destination,
                    onClick = { currentDestination = destination },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.labelRes)
                        )
                    },
                    label = { Text(text = stringResource(labelRes)) }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestination.HOME -> HomeScreen(
                    onBrowseGames = { currentDestination = AppDestination.GAMES },
                    onSeeAllFavorites = {
                        gamesViewModel.showFavorites()
                        currentDestination = AppDestination.GAMES
                    },
                    modifier = Modifier.padding(innerPadding),
                )
                AppDestination.GAMES -> GamesScreen(
                    modifier = Modifier.padding(innerPadding),
                    viewModel = gamesViewModel,
                )
                AppDestination.NOTIFICATIONS -> ComingSoonPage(
                    title = stringResource(AppDestination.NOTIFICATIONS.labelRes),
                    icon = AppDestination.NOTIFICATIONS.icon,
                    modifier = Modifier.padding(innerPadding),
                )
                AppDestination.PROFILE -> ProfileScreen(modifier = Modifier.padding(innerPadding))
                AppDestination.SETTINGS -> SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

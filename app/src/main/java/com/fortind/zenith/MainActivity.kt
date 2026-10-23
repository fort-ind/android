package com.fortind.zenith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
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
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.ui.games.GamesScreen
import com.fortind.zenith.ui.login.LoginScreen
import com.fortind.zenith.ui.profile.ProfileScreen
import com.fortind.zenith.ui.theme.ZenithTheme

enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    HOME(R.string.nav_home, Icons.Default.Home),
    GAMES(R.string.nav_games, Icons.Default.Favorite),
    SOCIAL(R.string.nav_social, Icons.Default.DateRange),
    PROFILE(R.string.nav_profile, Icons.Default.AccountCircle),
    SETTINGS(R.string.nav_settings, Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZenithTheme {
                ZenithRoot()
            }
        }
    }
}

@Composable
fun ZenithRoot() {
    val app = LocalContext.current.applicationContext as ZenithApplication
    val session by app.container.sessionService.state.collectAsStateWithLifecycle()

    when (session) {
        // Held on a blank background so a stored session doesn't flash the login screen.
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

@Composable
fun ZenithApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    // Small devices (phones / compact width < 600dp) -> NavigationBar
    // Larger screens (unfolded foldables, tablets, Chromebooks >= 600dp) -> NavigationRail
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
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination == destination,
                    onClick = { currentDestination = destination },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.labelRes)
                        )
                    },
                    label = { Text(text = stringResource(destination.labelRes)) }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestination.HOME -> BlankPage(modifier = Modifier.padding(innerPadding))
                AppDestination.GAMES -> GamesScreen(modifier = Modifier.padding(innerPadding))
                AppDestination.SOCIAL -> BlankPage(modifier = Modifier.padding(innerPadding))
                AppDestination.PROFILE -> ProfileScreen(modifier = Modifier.padding(innerPadding))
                AppDestination.SETTINGS -> BlankPage(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun BlankPage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize())
}

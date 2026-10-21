package com.fortind.zenith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.ui.theme.ZenithTheme

enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    PAGE_1(R.string.page_1, Icons.Default.Home),
    PAGE_2(R.string.page_2, Icons.Default.Favorite),
    PAGE_3(R.string.page_3, Icons.Default.DateRange),
    PAGE_4(R.string.page_4, Icons.Default.AccountCircle),
    PAGE_5(R.string.page_5, Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZenithTheme {
                ZenithApp()
            }
        }
    }
}

@Composable
fun ZenithApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.PAGE_1) }
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
                AppDestination.PAGE_1 -> BlankPage(modifier = Modifier.padding(innerPadding))
                AppDestination.PAGE_2 -> BlankPage(modifier = Modifier.padding(innerPadding))
                AppDestination.PAGE_3 -> BlankPage(modifier = Modifier.padding(innerPadding))
                AppDestination.PAGE_4 -> BlankPage(modifier = Modifier.padding(innerPadding))
                AppDestination.PAGE_5 -> BlankPage(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun BlankPage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize())
}

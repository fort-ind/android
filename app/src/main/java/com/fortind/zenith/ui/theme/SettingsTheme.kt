package com.fortind.zenith.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.ThemeMode

@Composable
fun SettingsTheme(onDarkTheme: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as ZenithApplication
    val settings by app.container.settingsStore.settings
        .collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val appIcon by app.container.appIconSwitcher.selected.collectAsStateWithLifecycle()
    val current = settings
    if (current == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (current.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    DisposableEffect(darkTheme) {
        onDarkTheme(darkTheme)
        onDispose {}
    }
    ZenithTheme(darkTheme = darkTheme, dynamicColor = current.dynamicColor, appIcon = appIcon, content = content)
}

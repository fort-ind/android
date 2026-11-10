package com.fortind.zenith.ui.theme

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

private val DEFAULT_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DEFAULT_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

fun ComponentActivity.applySystemBarStyle(darkTheme: Boolean) {
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

package com.fortind.zenith.ui.theme

import android.os.Build
import android.view.WindowInsetsController
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun SyncCaptionBar(darkTheme: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(window, darkTheme) {
        val controller = window.insetsController ?: return@LaunchedEffect
        val mask = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND or
            WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
        val appearance = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND or
            if (darkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
        controller.setSystemBarsAppearance(appearance, mask)
    }
}

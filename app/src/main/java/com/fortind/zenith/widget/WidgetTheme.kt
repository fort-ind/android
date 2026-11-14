package com.fortind.zenith.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import com.fortind.zenith.settings.AppIcon
import com.fortind.zenith.ui.theme.Contrast
import com.fortind.zenith.ui.theme.paletteFor

@Composable
fun WidgetTheme(appIcon: AppIcon, content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        val colors = remember(appIcon) {
            val palette = paletteFor(appIcon)
            ColorProviders(
                light = palette.scheme(darkTheme = false, contrast = Contrast.STANDARD),
                dark = palette.scheme(darkTheme = true, contrast = Contrast.STANDARD),
            )
        }
        GlanceTheme(colors = colors, content = content)
    }
}

package com.fortind.zenith.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import com.fortind.zenith.settings.AppIcon

enum class Contrast {
    STANDARD,
    MEDIUM,
    HIGH;

    companion object {
        private const val MEDIUM_THRESHOLD = 0.33f
        private const val HIGH_THRESHOLD = 0.66f

        fun fromSystem(level: Float): Contrast = when {
            level >= HIGH_THRESHOLD -> HIGH
            level >= MEDIUM_THRESHOLD -> MEDIUM
            else -> STANDARD
        }
    }
}

@Immutable
class ThemePalette(
    private val light: ColorScheme,
    private val dark: ColorScheme,
    private val lightMediumContrast: ColorScheme,
    private val lightHighContrast: ColorScheme,
    private val darkMediumContrast: ColorScheme,
    private val darkHighContrast: ColorScheme,
) {
    fun scheme(darkTheme: Boolean, contrast: Contrast): ColorScheme = when {
        darkTheme -> when (contrast) {
            Contrast.STANDARD -> dark
            Contrast.MEDIUM -> darkMediumContrast
            Contrast.HIGH -> darkHighContrast
        }
        else -> when (contrast) {
            Contrast.STANDARD -> light
            Contrast.MEDIUM -> lightMediumContrast
            Contrast.HIGH -> lightHighContrast
        }
    }
}

fun paletteFor(icon: AppIcon): ThemePalette = when (icon) {
    AppIcon.DEFAULT -> DefaultPalette
    AppIcon.FALL -> FallPalette
}

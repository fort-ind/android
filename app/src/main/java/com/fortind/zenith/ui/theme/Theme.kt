package com.fortind.zenith.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/** The theme actually in effect, which can differ from the system setting when the user overrides it. */
@Composable
fun isThemeDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private val DarkColorScheme = darkColorScheme(
    primary = FortDarkPrimary,
    onPrimary = FortDarkOnPrimary,
    primaryContainer = FortDarkPrimaryContainer,
    onPrimaryContainer = FortDarkOnPrimaryContainer,
    inversePrimary = FortDarkInversePrimary,
    secondary = FortDarkSecondary,
    onSecondary = FortDarkOnSecondary,
    secondaryContainer = FortDarkSecondaryContainer,
    onSecondaryContainer = FortDarkOnSecondaryContainer,
    tertiary = FortDarkTertiary,
    onTertiary = FortDarkOnTertiary,
    tertiaryContainer = FortDarkTertiaryContainer,
    onTertiaryContainer = FortDarkOnTertiaryContainer,
    background = FortDarkBackground,
    onBackground = FortDarkOnBackground,
    surface = FortDarkSurface,
    onSurface = FortDarkOnSurface,
    surfaceVariant = FortDarkSurfaceVariant,
    onSurfaceVariant = FortDarkOnSurfaceVariant,
    inverseSurface = FortDarkInverseSurface,
    inverseOnSurface = FortDarkInverseOnSurface,
    error = FortDarkError,
    onError = FortDarkOnError,
    errorContainer = FortDarkErrorContainer,
    onErrorContainer = FortDarkOnErrorContainer,
    outline = FortDarkOutline,
    outlineVariant = FortDarkOutlineVariant,
    surfaceDim = FortDarkSurfaceDim,
    surfaceBright = FortDarkSurfaceBright,
    surfaceContainerLowest = FortDarkSurfaceContainerLowest,
    surfaceContainerLow = FortDarkSurfaceContainerLow,
    surfaceContainer = FortDarkSurfaceContainer,
    surfaceContainerHigh = FortDarkSurfaceContainerHigh,
    surfaceContainerHighest = FortDarkSurfaceContainerHighest,
)

private val LightColorScheme = lightColorScheme(
    primary = FortLightPrimary,
    onPrimary = FortLightOnPrimary,
    primaryContainer = FortLightPrimaryContainer,
    onPrimaryContainer = FortLightOnPrimaryContainer,
    inversePrimary = FortLightInversePrimary,
    secondary = FortLightSecondary,
    onSecondary = FortLightOnSecondary,
    secondaryContainer = FortLightSecondaryContainer,
    onSecondaryContainer = FortLightOnSecondaryContainer,
    tertiary = FortLightTertiary,
    onTertiary = FortLightOnTertiary,
    tertiaryContainer = FortLightTertiaryContainer,
    onTertiaryContainer = FortLightOnTertiaryContainer,
    background = FortLightBackground,
    onBackground = FortLightOnBackground,
    surface = FortLightSurface,
    onSurface = FortLightOnSurface,
    surfaceVariant = FortLightSurfaceVariant,
    onSurfaceVariant = FortLightOnSurfaceVariant,
    inverseSurface = FortLightInverseSurface,
    inverseOnSurface = FortLightInverseOnSurface,
    error = FortLightError,
    onError = FortLightOnError,
    errorContainer = FortLightErrorContainer,
    onErrorContainer = FortLightOnErrorContainer,
    outline = FortLightOutline,
    outlineVariant = FortLightOutlineVariant,
    surfaceDim = FortLightSurfaceDim,
    surfaceBright = FortLightSurfaceBright,
    surfaceContainerLowest = FortLightSurfaceContainerLowest,
    surfaceContainerLow = FortLightSurfaceContainerLow,
    surfaceContainer = FortLightSurfaceContainer,
    surfaceContainerHigh = FortLightSurfaceContainerHigh,
    surfaceContainerHighest = FortLightSurfaceContainerHighest,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZenithTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content
    )
}

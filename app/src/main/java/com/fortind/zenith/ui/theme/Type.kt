package com.fortind.zenith.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

const val WIDE_TYPE_SCALE = 1.125f

val Typography = Typography()

val WideTypography = Typography.scaled(WIDE_TYPE_SCALE)

fun Typography.scaled(factor: Float): Typography = Typography(
    displayLarge = displayLarge.scaled(factor),
    displayMedium = displayMedium.scaled(factor),
    displaySmall = displaySmall.scaled(factor),
    headlineLarge = headlineLarge.scaled(factor),
    headlineMedium = headlineMedium.scaled(factor),
    headlineSmall = headlineSmall.scaled(factor),
    titleLarge = titleLarge.scaled(factor),
    titleMedium = titleMedium.scaled(factor),
    titleSmall = titleSmall.scaled(factor),
    bodyLarge = bodyLarge.scaled(factor),
    bodyMedium = bodyMedium.scaled(factor),
    bodySmall = bodySmall.scaled(factor),
    labelLarge = labelLarge.scaled(factor),
    labelMedium = labelMedium.scaled(factor),
    labelSmall = labelSmall.scaled(factor),
    displayLargeEmphasized = displayLargeEmphasized.scaled(factor),
    displayMediumEmphasized = displayMediumEmphasized.scaled(factor),
    displaySmallEmphasized = displaySmallEmphasized.scaled(factor),
    headlineLargeEmphasized = headlineLargeEmphasized.scaled(factor),
    headlineMediumEmphasized = headlineMediumEmphasized.scaled(factor),
    headlineSmallEmphasized = headlineSmallEmphasized.scaled(factor),
    titleLargeEmphasized = titleLargeEmphasized.scaled(factor),
    titleMediumEmphasized = titleMediumEmphasized.scaled(factor),
    titleSmallEmphasized = titleSmallEmphasized.scaled(factor),
    bodyLargeEmphasized = bodyLargeEmphasized.scaled(factor),
    bodyMediumEmphasized = bodyMediumEmphasized.scaled(factor),
    bodySmallEmphasized = bodySmallEmphasized.scaled(factor),
    labelLargeEmphasized = labelLargeEmphasized.scaled(factor),
    labelMediumEmphasized = labelMediumEmphasized.scaled(factor),
    labelSmallEmphasized = labelSmallEmphasized.scaled(factor),
)

fun TextStyle.scaled(factor: Float): TextStyle = copy(
    fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    letterSpacing = if (letterSpacing.isSpecified) letterSpacing * factor else letterSpacing,
)

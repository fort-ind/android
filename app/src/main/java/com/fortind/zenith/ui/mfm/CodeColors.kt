package com.fortind.zenith.ui.mfm

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.fortind.zenith.code.CodeTokenKind

internal class CodeColors(val byKind: Map<CodeTokenKind, Color>)

internal const val MIN_CODE_CONTRAST = 4.5f

private val DarkBase: Map<CodeTokenKind, Color> = mapOf(
    CodeTokenKind.Keyword to Color(0xFF569CD6),
    CodeTokenKind.StringLiteral to Color(0xFFCE9178),
    CodeTokenKind.Comment to Color(0xFF6A9955),
    CodeTokenKind.Number to Color(0xFFB5CEA8),
    CodeTokenKind.Constant to Color(0xFF4FC1FF),
    CodeTokenKind.Function to Color(0xFFDCDCAA),
    CodeTokenKind.Type to Color(0xFF4EC9B0),
    CodeTokenKind.Property to Color(0xFF9CDCFE),
    CodeTokenKind.Tag to Color(0xFF569CD6),
    CodeTokenKind.Meta to Color(0xFFC586C0),
    CodeTokenKind.Inserted to Color(0xFFB5CEA8),
    CodeTokenKind.Deleted to Color(0xFFF48771),
)

private val LightBase: Map<CodeTokenKind, Color> = mapOf(
    CodeTokenKind.Keyword to Color(0xFF0000FF),
    CodeTokenKind.StringLiteral to Color(0xFFA31515),
    CodeTokenKind.Comment to Color(0xFF008000),
    CodeTokenKind.Number to Color(0xFF098658),
    CodeTokenKind.Constant to Color(0xFF0070C1),
    CodeTokenKind.Function to Color(0xFF795E26),
    CodeTokenKind.Type to Color(0xFF267F99),
    CodeTokenKind.Property to Color(0xFF001080),
    CodeTokenKind.Tag to Color(0xFF800000),
    CodeTokenKind.Meta to Color(0xFFAF00DB),
    CodeTokenKind.Inserted to Color(0xFF098658),
    CodeTokenKind.Deleted to Color(0xFFA31515),
)

internal fun codeColors(background: Color): CodeColors {
    val base = if (background.luminance() < 0.5f) DarkBase else LightBase
    return CodeColors(base.mapValues { (_, color) -> readableOn(color, background, MIN_CODE_CONTRAST) })
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

internal fun readableOn(color: Color, background: Color, minRatio: Float): Color {
    if (contrastRatio(color, background) >= minRatio) return color
    val target = if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) Color.White else Color.Black
    for (step in 1..MIX_STEPS) {
        val mixed = lerp(color, target, step / MIX_STEPS.toFloat())
        if (contrastRatio(mixed, background) >= minRatio) return mixed
    }
    return target
}

private const val MIX_STEPS = 20

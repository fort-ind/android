package com.fortind.zenith.ui.mfm

import androidx.compose.ui.graphics.Color
import com.fortind.zenith.code.CodeTokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeColorsTest {

    private val backgrounds = listOf(
        "dark card" to Color(0xFF2B2B2B),
        "lighter dark card" to Color(0xFF484848),
        "light card" to Color(0xFFE6E0E9),
        "darker light card" to Color(0xFFD4D4D4),
        "tinted dynamic dark" to Color(0xFF3A2F4F),
        "tinted dynamic light" to Color(0xFFE8DEF8),
        "mid tone" to Color(0xFF808080),
        "pure black" to Color(0xFF000000),
        "pure white" to Color(0xFFFFFFFF),
    )

    @Test
    fun blackOnWhiteIsTheMaximumContrast() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun aColourThatIsAlreadyReadableIsLeftAlone() {
        val color = Color(0xFFFFFFFF)

        assertEquals(color, readableOn(color, Color(0xFF000000), MIN_CODE_CONTRAST))
    }

    @Test
    fun everyTokenColourIsReadableOnEveryBackground() {
        backgrounds.forEach { (name, background) ->
            val colors = codeColors(background)
            CodeTokenKind.entries.forEach { kind ->
                val ratio = contrastRatio(colors.byKind.getValue(kind), background)
                assertTrue("$kind on $name is only $ratio", ratio >= MIN_CODE_CONTRAST - 0.01f)
            }
        }
    }

    @Test
    fun aDarkCardGetsTheLightPaletteOfPastelsAndALightCardGetsDarkInk() {
        val onDark = codeColors(Color(0xFF2B2B2B)).byKind.getValue(CodeTokenKind.StringLiteral)
        val onLight = codeColors(Color(0xFFE6E0E9)).byKind.getValue(CodeTokenKind.StringLiteral)

        assertTrue(onDark.red > onDark.blue)
        assertTrue(contrastRatio(onDark, Color(0xFF2B2B2B)) > contrastRatio(onLight, Color(0xFF2B2B2B)))
    }

    @Test
    fun everyKindHasAColour() {
        assertEquals(CodeTokenKind.entries.toSet(), codeColors(Color(0xFF2B2B2B)).byKind.keys)
        assertEquals(CodeTokenKind.entries.toSet(), codeColors(Color(0xFFE6E0E9)).byKind.keys)
    }
}

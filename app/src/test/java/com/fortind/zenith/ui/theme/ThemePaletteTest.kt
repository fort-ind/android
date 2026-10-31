package com.fortind.zenith.ui.theme

import androidx.compose.ui.graphics.Color
import com.fortind.zenith.settings.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemePaletteTest {

    @Test
    fun systemContrastLevelsMapToTheThreeContrastSchemes() {
        assertEquals(Contrast.STANDARD, Contrast.fromSystem(-1f))
        assertEquals(Contrast.STANDARD, Contrast.fromSystem(0f))
        assertEquals(Contrast.MEDIUM, Contrast.fromSystem(0.5f))
        assertEquals(Contrast.HIGH, Contrast.fromSystem(1f))
    }

    @Test
    fun defaultIconUsesThePurplePalette() {
        assertEquals(Color(0xFF6726DE), paletteFor(AppIcon.DEFAULT).scheme(darkTheme = false, contrast = Contrast.STANDARD).primary)
    }

    @Test
    fun fallIconUsesTheFallPalette() {
        assertEquals(Color(0xFF350300), paletteFor(AppIcon.FALL).scheme(darkTheme = false, contrast = Contrast.STANDARD).primary)
    }

    @Test
    fun everyCombinationOfThemeAndContrastIsDistinctPerPalette() {
        AppIcon.entries.forEach { icon ->
            val palette = paletteFor(icon)
            val schemes = listOf(false, true).flatMap { dark ->
                Contrast.entries.map { contrast -> palette.scheme(dark, contrast) }
            }
            assertEquals(schemes.size, schemes.toSet().size)
        }
    }

    @Test
    fun darkSchemesHaveDarkSurfacesAndLightSchemesLightOnes() {
        AppIcon.entries.forEach { icon ->
            Contrast.entries.forEach { contrast ->
                val palette = paletteFor(icon)
                assertNotEquals(
                    palette.scheme(false, contrast).surface,
                    palette.scheme(true, contrast).surface,
                )
            }
        }
    }
}

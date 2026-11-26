package com.fortind.zenith.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeScaleTest {
    @Test
    fun sizesAndLineHeightsGrowTogether() {
        val scaled = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp).scaled(WIDE_TYPE_SCALE)
        assertEquals(18.sp, scaled.fontSize)
        assertEquals(27.sp, scaled.lineHeight)
        assertEquals(0.5625.sp, scaled.letterSpacing)
    }

    @Test
    fun unspecifiedValuesStayUnspecified() {
        val scaled = TextStyle().scaled(WIDE_TYPE_SCALE)
        assertEquals(TextUnit.Unspecified, scaled.fontSize)
        assertEquals(TextUnit.Unspecified, scaled.lineHeight)
    }

    @Test
    fun theWideScaleCoversEveryRoleIncludingEmphasized() {
        assertEquals(Typography.bodyLarge.fontSize * WIDE_TYPE_SCALE, WideTypography.bodyLarge.fontSize)
        assertEquals(Typography.labelSmallEmphasized.fontSize * WIDE_TYPE_SCALE, WideTypography.labelSmallEmphasized.fontSize)
        assertEquals(Typography.displayLarge.lineHeight * WIDE_TYPE_SCALE, WideTypography.displayLarge.lineHeight)
    }
}

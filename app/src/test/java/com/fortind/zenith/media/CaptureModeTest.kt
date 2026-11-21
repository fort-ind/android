package com.fortind.zenith.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureModeTest {

    @Test
    fun aCameraWithNoExtensionsOffersNoModesAtAll() {
        assertTrue(offeredCaptureModes { false }.isEmpty())
    }

    @Test
    fun offIsFirstAndTheRestKeepTheDisplayOrder() {
        val offered = offeredCaptureModes { it in setOf(CaptureMode.Night, CaptureMode.Auto, CaptureMode.FaceRetouch) }

        assertEquals(listOf(CaptureMode.Off, CaptureMode.Auto, CaptureMode.Night, CaptureMode.FaceRetouch), offered)
    }

    @Test
    fun offIsNeverAskedAboutBecauseItNeedsNoExtension() {
        val asked = mutableListOf<CaptureMode>()

        offeredCaptureModes { mode -> asked += mode; true }

        assertTrue(CaptureMode.Off !in asked)
        assertEquals(CaptureMode.entries.size - 1, asked.size)
    }

    @Test
    fun theSavedModeIsUsedWhenTheCameraOffersIt() {
        val offered = listOf(CaptureMode.Off, CaptureMode.Auto, CaptureMode.Hdr)

        assertEquals(CaptureMode.Hdr, effectiveCaptureMode(CaptureMode.Hdr, offered))
    }

    @Test
    fun aSavedModeTheCameraLacksFallsBackToOff() {
        val offered = listOf(CaptureMode.Off, CaptureMode.Auto)

        assertEquals(CaptureMode.Off, effectiveCaptureMode(CaptureMode.Night, offered))
    }

    @Test
    fun withNothingOfferedTheModeIsOffWhateverWasSaved() {
        assertEquals(CaptureMode.Off, effectiveCaptureMode(CaptureMode.Auto, emptyList()))
    }
}

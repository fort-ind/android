package com.fortind.zenith.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraOptionsTest {

    private val everything = VideoSupport(
        fps60 = true,
        stabilization = Stabilization.Preview,
        fps60WithStabilization = true,
        lowLight = true,
    )
    private val noCombination = everything.copy(fps60WithStabilization = false)

    @Test
    fun aRealFlashOffersAllThreeChoices() {
        assertEquals(FlashChoice.entries, flashChoices(hasFlashUnit = true, screenFlash = true))
    }

    @Test
    fun theScreenFlashHasNoAuto() {
        assertEquals(listOf(FlashChoice.Off, FlashChoice.On), flashChoices(hasFlashUnit = false, screenFlash = true))
    }

    @Test
    fun noFlashOfEitherKindOffersNothing() {
        assertTrue(flashChoices(hasFlashUnit = false, screenFlash = false).isEmpty())
    }

    @Test
    fun aSavedAutoFallsBackToOffOnTheScreenFlashWithoutBeingLost() {
        val screen = flashChoices(hasFlashUnit = false, screenFlash = true)

        assertEquals(FlashChoice.Off, effectiveFlash(FlashChoice.Auto, screen))
        assertEquals(FlashChoice.Auto, effectiveFlash(FlashChoice.Auto, FlashChoice.entries))
    }

    @Test
    fun flashCyclesThroughWhatTheCameraOffers() {
        val screen = listOf(FlashChoice.Off, FlashChoice.On)

        assertEquals(FlashChoice.Auto, nextFlash(FlashChoice.Off, FlashChoice.entries))
        assertEquals(FlashChoice.Off, nextFlash(FlashChoice.On, FlashChoice.entries))
        assertEquals(FlashChoice.On, nextFlash(FlashChoice.Off, screen))
        assertEquals(FlashChoice.Off, nextFlash(FlashChoice.Auto, screen))
    }

    @Test
    fun sixtyFpsAndLowLightTurnEachOtherOff() {
        val lowLight = VideoOptions(lowLight = true)

        val smooth = lowLight.with(VideoOption.Fps60, true, everything)
        assertTrue(smooth.fps60)
        assertFalse(smooth.lowLight)

        val bright = smooth.with(VideoOption.LowLight, true, everything)
        assertTrue(bright.lowLight)
        assertFalse(bright.fps60)
    }

    @Test
    fun stabilizationAndSixtyFpsOnlyClashWhenTheCameraCannotCombineThem() {
        val stabilized = VideoOptions(stabilize = true)

        assertEquals(VideoOptions(fps60 = true, stabilize = true), stabilized.with(VideoOption.Fps60, true, everything))
        assertEquals(VideoOptions(fps60 = true), stabilized.with(VideoOption.Fps60, true, noCombination))
        assertEquals(
            VideoOptions(stabilize = true),
            VideoOptions(fps60 = true).with(VideoOption.Stabilize, true, noCombination),
        )
    }

    @Test
    fun turningSomethingOffNeverTurnsAnythingElseOff() {
        val all = VideoOptions(fps60 = true, stabilize = true)

        assertEquals(VideoOptions(stabilize = true), all.with(VideoOption.Fps60, false, noCombination))
    }

    @Test
    fun theSwitchSaysWhatItWouldTurnOff() {
        val current = VideoOptions(stabilize = true, lowLight = true)

        assertEquals(
            listOf(VideoOption.Stabilize, VideoOption.LowLight),
            current.turnedOffBy(VideoOption.Fps60, noCombination),
        )
        assertEquals(listOf(VideoOption.LowLight), current.turnedOffBy(VideoOption.Fps60, everything))
        assertTrue(current.turnedOffBy(VideoOption.Stabilize, noCombination).isEmpty())
    }

    @Test
    fun savedOptionsTheCameraLacksAreDroppedOnlyForThisCamera() {
        val saved = VideoOptions(fps60 = true, stabilize = true, lowLight = false)

        assertEquals(VideoOptions(), saved.effectiveFor(VideoSupport()))
        assertEquals(saved, saved.effectiveFor(everything))
    }

    @Test
    fun anImpossibleSavedComboKeepsStabilization() {
        val saved = VideoOptions(fps60 = true, stabilize = true)

        assertEquals(VideoOptions(stabilize = true), saved.effectiveFor(noCombination))
    }

    @Test
    fun lowLightNeverRunsAlongsideSixtyFps() {
        val saved = VideoOptions(fps60 = true, lowLight = true)

        assertEquals(VideoOptions(fps60 = true), saved.effectiveFor(everything))
        assertEquals(VideoOptions(lowLight = true), saved.effectiveFor(everything.copy(fps60 = false)))
    }

    @Test
    fun supportAnswersPerOption() {
        assertTrue(everything.offers(VideoOption.Stabilize))
        assertFalse(VideoSupport().offers(VideoOption.Stabilize))
        assertFalse(VideoSupport().offers(VideoOption.LowLight))
    }

    @Test
    fun timerChoicesCountDownInSeconds() {
        assertEquals(listOf(0, 3, 10), CaptureTimer.entries.map { it.seconds })
    }
}

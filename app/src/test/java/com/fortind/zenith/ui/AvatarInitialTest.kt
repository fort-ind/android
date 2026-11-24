package com.fortind.zenith.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarInitialTest {

    @Test
    fun aPlainNameGivesItsFirstLetterUppercased() {
        assertEquals("M", avatarInitial("mario"))
    }

    @Test
    fun leadingEmojiAreSkippedForTheFirstLetter() {
        assertEquals("H", avatarInitial("🫶🪻Hasan Dyker🪻"))
        assertEquals("T", avatarInitial("🇯🇵 Taro"))
    }

    @Test
    fun customEmojiShortcodesAreSkipped() {
        assertEquals("A", avatarInitial(":blobcat: Amber"))
        assertEquals("A", avatarInitial(":blobcat@misskey.io: Amber"))
        assertEquals("K", avatarInitial("kopper :ω:"))
        assertEquals("K", avatarInitial(":ω: kopper"))
    }

    @Test
    fun aNameWithoutLettersFallsBackToItsFirstWholeEmoji() {
        assertEquals("🌸", avatarInitial("🌸🌸"))
        assertEquals("🇯🇵", avatarInitial("🇯🇵"))
    }

    @Test
    fun accentsAndScriptsWithoutCaseStayWhole() {
        assertEquals("É", avatarInitial("élan"))
        assertEquals("山", avatarInitial("山田"))
        assertEquals("कि", avatarInitial("किरण"))
    }

    @Test
    fun nothingDrawableGivesAnEmptyInitial() {
        assertEquals("", avatarInitial(""))
        assertEquals("", avatarInitial(":blobcat:"))
        assertEquals("", avatarInitial("   "))
    }

    @Test
    fun aKeycapDigitKeepsItsKeycap() {
        assertEquals("1️⃣", avatarInitial("1️⃣ fan"))
    }
}

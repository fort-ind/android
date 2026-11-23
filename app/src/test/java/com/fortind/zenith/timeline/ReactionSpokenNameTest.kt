package com.fortind.zenith.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionSpokenNameTest {

    @Test
    fun customEmojiReadAsWordsWithoutColonsOrHost() {
        assertEquals("blobcat happy", Reactions.spokenName(":blobcat_happy@.:"))
        assertEquals("neocat heart", Reactions.spokenName(":neocat-heart@misskey.io:"))
        assertEquals("party", Reactions.spokenName(":party:"))
    }

    @Test
    fun unicodeEmojiIsLeftForTheScreenReader() {
        assertEquals("👍", Reactions.spokenName("👍"))
    }

    @Test
    fun separatorsCollapseAndOddNamesSurvive() {
        assertEquals("a b", Reactions.spokenEmojiName("a__b_"))
        assertEquals("+1", Reactions.spokenEmojiName("+1"))
        assertEquals("_", Reactions.spokenEmojiName("_"))
    }
}

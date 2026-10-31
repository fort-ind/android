package com.fortind.zenith.autocomplete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutocompleteTriggerTest {

    private fun trigger(text: String, cursor: Int = text.length) = findTrigger(text, cursor)

    @Test
    fun aMentionAtTheStartIsFound() {
        assertEquals(Trigger(TriggerKind.Mention, "ali", 0, 4), trigger("@ali"))
    }

    @Test
    fun aBareAtSignOpensMentionsWithAnEmptyQuery() {
        assertEquals(Trigger(TriggerKind.Mention, "", 6, 7), trigger("hello @"))
    }

    @Test
    fun aMentionAfterTextAndAnOpeningBracketIsFound() {
        assertEquals(Trigger(TriggerKind.Mention, "bo", 6, 9), trigger("hello @bo"))
        assertEquals(Trigger(TriggerKind.Mention, "bo", 1, 4), trigger("(@bo"))
    }

    @Test
    fun aRemoteHandleKeepsItsHostInTheQuery() {
        assertEquals("alice@exa", trigger("@alice@exa")!!.query)
    }

    @Test
    fun emailsAreNotMentions() {
        assertNull(trigger("write to a@b.com"))
    }

    @Test
    fun aSecondAtSignInTheQueryIsNotAHandle() {
        assertNull(trigger("@a@b@c"))
    }

    @Test
    fun onlyTheWordUnderTheCursorMatters() {
        assertEquals(Trigger(TriggerKind.Mention, "al", 0, 3), trigger("@alice bob", cursor = 3))
        assertNull(trigger("@alice bob", cursor = 8))
    }

    @Test
    fun nothingIsSuggestedRightAfterWhitespace() {
        assertNull(trigger("@alice "))
        assertNull(trigger("hello "))
    }

    @Test
    fun hashtagsNeedAtLeastOneCharacter() {
        assertNull(trigger("#"))
        assertEquals(Trigger(TriggerKind.Hashtag, "ph", 0, 3), trigger("#ph"))
    }

    @Test
    fun hashtagsStopAtPunctuation() {
        assertNull(trigger("#photo-graphy"))
        assertEquals("日本語_1", trigger("#日本語_1")!!.query)
    }

    @Test
    fun urlFragmentsAndTimesAreNotTriggers() {
        assertNull(trigger("https://example.com/#frag"))
        assertNull(trigger("see you at 10:30"))
    }

    @Test
    fun emojiNeedTwoCharactersAndAnOpenColon() {
        assertNull(trigger(":s"))
        assertEquals(Trigger(TriggerKind.Emoji, "sm", 0, 3), trigger(":sm"))
        assertNull(trigger(":smile:"))
    }

    @Test
    fun emojiNamesMayContainSymbols() {
        assertEquals("blob_cat-2+", trigger("yay :blob_cat-2+")!!.query)
        assertEquals(TriggerKind.Emoji, trigger("yay :blob_cat-2")!!.kind)
    }

    @Test
    fun cursorsOutOfRangeFindNothing() {
        assertNull(findTrigger("@a", 0))
        assertNull(findTrigger("@a", 9))
    }

    @Test
    fun veryLongTokensAreIgnored() {
        assertNull(trigger("@" + "a".repeat(200)))
    }

    @Test
    fun handleQueriesSplitIntoNameAndHost() {
        assertEquals("alice" to null, splitHandleQuery("alice"))
        assertEquals("alice" to "exa", splitHandleQuery("alice@exa"))
        assertEquals("alice" to null, splitHandleQuery("alice@"))
        assertEquals("" to "example.com", splitHandleQuery("@example.com"))
    }
}

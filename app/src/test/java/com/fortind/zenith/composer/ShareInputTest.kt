package com.fortind.zenith.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareInputTest {
    private val own = "com.fortind.zenith.debug"

    private fun share(text: String? = null, subject: String? = null, uris: List<String> = emptyList()) =
        sharedContent(text, subject, uris, own)

    @Test
    fun aLinkLandsUnderAnEmptyLineWithTheCaretAtTheStart() {
        val shared = share(text = "https://example.com/a", subject = "Page title")

        assertEquals("\nPage title\nhttps://example.com/a", shared.text)
        assertTrue(shared.caretAtStart)
    }

    @Test
    fun aTitleThatIsJustTheLinkIsLeftOut() {
        assertEquals("\nhttps://example.com/a", share(text = "https://example.com/a", subject = "https://example.com/a").text)
        assertEquals("\nhttps://example.com/a", share(text = " https://example.com/a\n").text)
    }

    @Test
    fun wordsAroundALinkMakeItPlainText() {
        val shared = share(text = "look at https://example.com/a", subject = "ignored")

        assertEquals("look at https://example.com/a", shared.text)
        assertFalse(shared.caretAtStart)
    }

    @Test
    fun theSubjectStandsInWhenThereIsNoText() {
        assertEquals("Only a subject", share(subject = "Only a subject").text)
    }

    @Test
    fun lineBreaksAreNormalizedAndHugeTextIsBounded() {
        assertEquals("a\nb\nc", share(text = "a\r\nb\rc").text)
        assertEquals(20_000, share(text = "x".repeat(50_000)).text.length)
    }

    @Test
    fun onlyOtherAppsContentUrisAreAccepted() {
        assertTrue(isAcceptableShareUri("content://media/external/images/1", own))
        assertFalse(isAcceptableShareUri("file:///data/data/$own/files/secret", own))
        assertFalse(isAcceptableShareUri("https://example.com/a.png", own))
        assertFalse(isAcceptableShareUri("content://$own/files/secret", own))
        assertFalse(isAcceptableShareUri("content://$own.androidx-startup/x", own))
        assertFalse(isAcceptableShareUri("content:///no-authority", own))
    }

    @Test
    fun repeatedAndExcessUrisAreDropped() {
        val many = (1..40).map { "content://media/$it" }

        val shared = share(uris = many + many.first() + "file:///etc/hosts")

        assertEquals(MAX_ATTACHMENTS + 1, shared.uris.size)
        assertTrue(shared.uris.all { it.startsWith("content://media/") })
    }

    @Test
    fun aRefusedFileIsReportedEvenWhenNothingElseCame() {
        assertTrue(share(uris = listOf("file:///etc/hosts")).refusedFiles)
        assertTrue(share(text = "words", uris = listOf("content://media/1", "file:///etc/hosts")).refusedFiles)
        assertFalse(share(uris = listOf("content://media/1")).refusedFiles)
    }

    @Test
    fun onlyImagesAndVideoCanBePosted() {
        assertTrue(isPostableMime("image/png"))
        assertTrue(isPostableMime("video/mp4"))
        assertFalse(isPostableMime("application/pdf"))
        assertFalse(isPostableMime("audio/mpeg"))
    }
}

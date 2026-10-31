package com.fortind.zenith.composer

import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.timeline.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftStoreTest {

    private val draft = ComposerDraft(text = "hello\nworld", cw = "spoilers", visibility = Visibility.Followers, localOnly = true)

    @Test
    fun draftsSurviveAnEncodeDecodeRoundTrip() {
        val stored = mapOf("reply:n1" to StoredDraft(draft, 42L), "new" to StoredDraft(ComposerDraft(text = "hi"), 7L))

        assertEquals(stored, decodeDrafts(encodeDrafts(stored)))
    }

    @Test
    fun nullContentWarningStaysNullAfterDecoding() {
        val stored = mapOf("new" to StoredDraft(ComposerDraft(text = "hi"), 1L))

        assertNull(decodeDrafts(encodeDrafts(stored)).getValue("new").draft.cw)
    }

    @Test
    fun anEnabledButEmptyContentWarningSurvivesARoundTrip() {
        val stored = mapOf("new" to StoredDraft(ComposerDraft(text = "hi", cw = ""), 1L))

        assertEquals("", decodeDrafts(encodeDrafts(stored)).getValue("new").draft.cw)
    }

    @Test
    fun uploadedAttachmentsSurviveARoundTripWithTheirEdits() {
        val file = NoteFile("f1", "cat.png", "image/png", "https://x/cat.png", "https://x/cat-thumb.png", "LEHV6n", true, "server alt", 640, 480)
        val stored = mapOf(
            "new" to StoredDraft(ComposerDraft(text = "", attachments = listOf(DraftAttachment(file, "my alt", false))), 1L),
        )

        val decoded = decodeDrafts(encodeDrafts(stored)).getValue("new").draft.attachments.single()

        assertEquals(file, decoded.file)
        assertEquals("my alt", decoded.altText)
        assertEquals(false, decoded.isSensitive)
    }

    @Test
    fun aDraftWithOnlyAttachmentsIsNotBlank() {
        val file = NoteFile("f1", null, "image/png", "https://x/a.png", null, null, false, null, null, null)

        assertTrue(!ComposerDraft(attachments = listOf(DraftAttachment(file, "", false))).isBlank)
    }

    @Test
    fun corruptOrMissingDataDecodesToNothing() {
        assertTrue(decodeDrafts(null).isEmpty())
        assertTrue(decodeDrafts("").isEmpty())
        assertTrue(decodeDrafts("{not json").isEmpty())
    }

    @Test
    fun unknownVisibilityFallsBackToPublic() {
        val raw = """{"new":{"text":"hi","visibility":"mystery","updatedAt":1}}"""

        assertEquals(Visibility.Public, decodeDrafts(raw).getValue("new").draft.visibility)
    }

    @Test
    fun savingABlankDraftRemovesTheEntry() {
        val existing = mapOf("new" to StoredDraft(draft, 1L))

        assertTrue(putDraft(existing, "new", ComposerDraft(text = "   "), nowMillis = 2L).isEmpty())
    }

    @Test
    fun savingReplacesAnExistingDraftAndStampsTheTime() {
        val existing = mapOf("new" to StoredDraft(ComposerDraft(text = "old"), 1L))

        val updated = putDraft(existing, "new", ComposerDraft(text = "newer"), nowMillis = 9L)

        assertEquals(StoredDraft(ComposerDraft(text = "newer"), 9L), updated.getValue("new"))
    }

    @Test
    fun theOldestDraftsAreDroppedBeyondTheCap() {
        var drafts = emptyMap<String, StoredDraft>()
        repeat(MAX_STORED_DRAFTS + 3) { index ->
            drafts = putDraft(drafts, "reply:$index", ComposerDraft(text = "t$index"), nowMillis = index.toLong())
        }

        assertEquals(MAX_STORED_DRAFTS, drafts.size)
        assertTrue("reply:0" !in drafts && "reply:1" !in drafts && "reply:2" !in drafts)
        assertTrue("reply:${MAX_STORED_DRAFTS + 2}" in drafts)
    }

    @Test
    fun aDraftWithOnlyAContentWarningIsNotBlank() {
        assertTrue(ComposerDraft(text = "").isBlank)
        assertTrue(!ComposerDraft(text = "", cw = "warning").isBlank)
    }
}

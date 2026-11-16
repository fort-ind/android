package com.fortind.zenith.ui.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaExportTest {

    @Test
    fun keepsANameThatHasAnExtension() {
        assertEquals("cat.png", suggestedFileName("cat.png", "abc", "image/png"))
    }

    @Test
    fun addsTheExtensionForTheType() {
        assertEquals("cat.jpg", suggestedFileName("cat", "abc", "image/jpeg"))
    }

    @Test
    fun fallsBackToTheFileId() {
        assertEquals("abc.webp", suggestedFileName(null, "abc", "image/webp"))
        assertEquals("abc.mp4", suggestedFileName("  ", "abc", "video/mp4"))
    }

    @Test
    fun replacesCharactersFilePickersRefuse() {
        assertEquals("a_b_c.gif", suggestedFileName("a/b:c.gif", "abc", "image/gif"))
    }
}

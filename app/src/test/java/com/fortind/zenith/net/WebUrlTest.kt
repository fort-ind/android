package com.fortind.zenith.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUrlTest {

    @Test
    fun onlyWebSchemesCount() {
        assertTrue(isWebUrl("https://social.fort1nd.com/files/a.webp"))
        assertTrue(isWebUrl("HTTP://example.com"))
        assertFalse(isWebUrl("file:///data/data/com.fortind.zenith/shared_prefs/fort_session.xml"))
        assertFalse(isWebUrl("content://com.fortind.zenith.remotemedia/media/x"))
        assertFalse(isWebUrl("android.resource://com.fortind.zenith/raw/x"))
        assertFalse(isWebUrl("https://"))
        assertFalse(isWebUrl("data:image/png;base64,AAAA"))
    }
}

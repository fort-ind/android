package com.fortind.zenith.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTabTest {

    @Test
    fun onlyWebLinksLeaveTheApp() {
        assertTrue(isWebLink("https://social.fort1nd.com/notes/1"))
        assertTrue(isWebLink("HTTP://example.com"))
        assertFalse(isWebLink("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isWebLink("content://com.fortind.zenith.androidx-startup/x"))
        assertFalse(isWebLink("file:///data/data/com.fortind.zenith/shared_prefs/fort_session.xml"))
        assertFalse(isWebLink("javascript:alert(1)"))
        assertFalse(isWebLink("market://details?id=x"))
        assertFalse(isWebLink("https://"))
        assertFalse(isWebLink(""))
    }
}

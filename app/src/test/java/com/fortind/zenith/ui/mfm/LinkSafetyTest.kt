package com.fortind.zenith.ui.mfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkSafetyTest {

    @Test
    fun aLabelShowingAnotherHostIsFlagged() {
        assertEquals(
            DeceptiveLink("https://evil.example/login", "bank.example", "evil.example"),
            deceptiveLink("https://bank.example", "https://evil.example/login"),
        )
        assertEquals("bank.example", deceptiveLink("www.bank.example/account", "https://evil.example")?.shownHost)
    }

    @Test
    fun theSameSiteOrASubdomainIsNotFlagged() {
        assertNull(deceptiveLink("example.com", "https://www.example.com/page"))
        assertNull(deceptiveLink("https://example.com", "https://blog.example.com/"))
        assertNull(deceptiveLink("login.example.com", "https://EXAMPLE.com"))
    }

    @Test
    fun ordinaryWordsAreNotTreatedAsAddresses() {
        assertNull(deceptiveLink("click here", "https://evil.example"))
        assertNull(deceptiveLink("version 2.0", "https://evil.example"))
        assertNull(deceptiveLink("see bank.example for more", "https://evil.example"))
    }

    @Test
    fun anUnparseableTargetIsLeftAlone() {
        assertNull(deceptiveLink("bank.example", "https://exa mple.com"))
    }
}

package com.fortind.zenith.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLinksTest {

    @Test
    fun noteLinksOpenTheThread() {
        assertEquals(AppRoute.Thread("9xyz1234ab"), appRouteForWebLink("https://social.fort1nd.com/notes/9xyz1234ab"))
        assertEquals(AppRoute.Thread("9xyz1234ab"), appRouteForWebLink("https://social.fort1nd.com/notes/9xyz1234ab?foo=1#bar"))
    }

    @Test
    fun profileAndTagLinksOpenTheirPages() {
        assertEquals(AppRoute.PersonHandle("alice", null), appRouteForWebLink("https://social.fort1nd.com/@alice"))
        assertEquals(AppRoute.PersonHandle("bob", "example.org"), appRouteForWebLink("https://social.fort1nd.com/@bob@example.org"))
        assertEquals(AppRoute.Tag("android"), appRouteForWebLink("https://social.fort1nd.com/tags/android"))
    }

    @Test
    fun otherHostsAndPathsStayInTheBrowser() {
        assertNull(appRouteForWebLink("https://example.org/notes/9xyz1234ab"))
        assertNull(appRouteForWebLink("https://social.fort1nd.com/about"))
        assertNull(parseNoteLink("https://social.fort1nd.com/notes/../evil"))
    }

    @Test
    fun pagesMapToTheirWebAddress() {
        assertEquals("https://social.fort1nd.com/notes/abc", webUrlFor(AppRoute.Thread("abc")))
        assertEquals("https://social.fort1nd.com/@bob@example.org", webUrlFor(AppRoute.PersonHandle("bob", "example.org")))
        assertEquals("https://social.fort1nd.com/tags/caf%C3%A9", webUrlFor(AppRoute.Tag("café")))
        assertEquals("https://social.fort1nd.com/search?q=two%20words", webUrlFor(AppRoute.Search("two words")))
        assertNull(webUrlFor(AppRoute.Games))
    }
}

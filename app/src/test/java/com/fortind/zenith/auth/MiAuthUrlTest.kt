package com.fortind.zenith.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MiAuthUrlTest {

    private val defaultScopes = "read:account,read:notifications,write:notifications,write:following,write:reactions," +
        "write:notes,write:drive,write:votes,read:favorites,write:favorites"

    @Test
    fun authorizeUrlTargetsTheFortInstanceWithAppNameAndScopes() {
        val url = MiAuthUrl.authorize(session = "7f3c1d2e-0000-4000-8000-000000000001")

        assertEquals(
            "https://social.fort1nd.com/miauth/7f3c1d2e-0000-4000-8000-000000000001" +
                "?name=fort.android" +
                "&permission=$defaultScopes",
            url,
        )
    }

    @Test
    fun defaultHostIsPinnedToSocialFort1ndCom() {
        assertEquals("social.fort1nd.com", FortInstance.HOST)
        assertEquals(
            "https://social.fort1nd.com/api/miauth/abc/check",
            MiAuthUrl.check(session = "abc"),
        )
    }

    @Test
    fun multipleScopesAreCommaSeparated() {
        val url = MiAuthUrl.authorize(session = "s", scopes = listOf("read:account", "write:notes"))

        assertEquals(
            "https://social.fort1nd.com/miauth/s?name=fort.android&permission=read:account,write:notes",
            url,
        )
    }

    @Test
    fun appNameIsPercentEncoded() {
        val url = MiAuthUrl.authorize(session = "s", appName = "fort & friends")

        assertEquals(
            "https://social.fort1nd.com/miauth/s?name=fort%20%26%20friends" +
                "&permission=$defaultScopes",
            url,
        )
    }

    @Test
    fun malformedScopesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MiAuthUrl.authorize(session = "s", scopes = listOf("read:account&admin=1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MiAuthUrl.authorize(session = "s", scopes = emptyList())
        }
    }
}

package com.fortind.zenith.auth

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ResponseParsingTest {

    @Test
    fun approvedCheckCarriesTheToken() {
        val result = parseCheckResponse(JSONObject("""{"ok":true,"token":"abc123","user":{"id":"u1"}}"""))

        assertEquals(CheckResult.Approved("abc123"), result)
    }

    @Test
    fun notYetApprovedIsPending() {
        assertEquals(CheckResult.Pending, parseCheckResponse(JSONObject("""{"ok":false}""")))
    }

    @Test
    fun okWithoutATokenIsNotTreatedAsApproved() {
        assertEquals(CheckResult.Pending, parseCheckResponse(JSONObject("""{"ok":true}""")))
    }

    @Test
    fun currentUserIsParsedFromApiI() {
        val user = parseCurrentUser(
            JSONObject(
                """{"id":"9abc","username":"ranvir","name":"Ranvir","avatarUrl":"https://x/a.png",
                    "bannerUrl":"https://x/b.png","host":null}"""
            )
        )

        assertEquals(CurrentUser("9abc", "ranvir", "Ranvir", "https://x/a.png", "https://x/b.png"), user)
    }

    @Test
    fun missingBannerIsNull() {
        val user = parseCurrentUser(JSONObject("""{"id":"1","username":"r","bannerUrl":null}"""))

        assertEquals(null, user.bannerUrl)
    }

    @Test
    fun handleIsFullyQualifiedToTheFortInstance() {
        val user = parseCurrentUser(JSONObject("""{"id":"1","username":"ranvir"}"""))

        assertEquals("@ranvir@social.fort1nd.com", user.handle)
    }

    @Test
    fun displayNameFallsBackToUsername() {
        val user = parseCurrentUser(JSONObject("""{"id":"1","username":"ranvir","name":null}"""))

        assertEquals("ranvir", user.displayName)
    }

    @Test
    fun userWithoutIdIsRejected() {
        assertThrows(IOException::class.java) { parseCurrentUser(JSONObject("""{"username":"x"}""")) }
    }
}

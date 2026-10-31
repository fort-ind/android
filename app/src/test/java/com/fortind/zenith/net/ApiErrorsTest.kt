package com.fortind.zenith.net

import com.fortind.zenith.auth.TokenRejectedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorsTest {

    @Test
    fun errorCodeIsReadFromTheMisskeyErrorBody() {
        assertEquals(
            "LTL_DISABLED",
            parseApiErrorCode("""{"error":{"message":"Local timeline has been disabled.","code":"LTL_DISABLED","id":"45a6"}}"""),
        )
    }

    @Test
    fun malformedOrEmptyBodiesHaveNoCode() {
        assertNull(parseApiErrorCode(null))
        assertNull(parseApiErrorCode(""))
        assertNull(parseApiErrorCode("<html>"))
        assertNull(parseApiErrorCode("""{"error":{}}"""))
    }

    @Test
    fun unauthorizedMeansTheTokenWasRejected() {
        assertTrue(translateAuthError(HttpStatusException(401)) is TokenRejectedException)
    }

    @Test
    fun forbiddenWithoutACodeOrWithPermissionDeniedMeansAMissingScope() {
        assertTrue(translateAuthError(HttpStatusException(403)) is MissingPermissionException)
        assertTrue(translateAuthError(HttpStatusException(403, "PERMISSION_DENIED")) is MissingPermissionException)
    }

    @Test
    fun otherForbiddenErrorsAreKept() {
        val error = HttpStatusException(403, "YOUR_ACCOUNT_SUSPENDED")
        assertSame(error, translateAuthError(error))
    }

    @Test
    fun rateLimitsAreRecognised() {
        assertTrue(HttpStatusException(429).isRateLimited)
        assertTrue(HttpStatusException(400, "RATE_LIMIT_EXCEEDED").isRateLimited)
        assertFalse(HttpStatusException(400, "NO_SUCH_NOTE").isRateLimited)
    }
}

package com.fortind.zenith.notifications

import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowRequestRefusalTest {

    @Test
    fun clientErrorsAreFinal() {
        assertTrue(HttpStatusException(400, ApiErrorCodes.NO_FOLLOW_REQUEST).isFinalRefusal())
        assertTrue(HttpStatusException(404).isFinalRefusal())
        assertTrue(HttpStatusException(403, ApiErrorCodes.BLOCKED).isFinalRefusal())
    }

    @Test
    fun rateLimitsAreRetried() {
        assertFalse(HttpStatusException(429).isFinalRefusal())
        assertFalse(HttpStatusException(429, ApiErrorCodes.RATE_LIMIT_EXCEEDED).isFinalRefusal())
        assertFalse(HttpStatusException(400, ApiErrorCodes.RATE_LIMIT_EXCEEDED).isFinalRefusal())
    }

    @Test
    fun serverErrorsAreRetried() {
        assertFalse(HttpStatusException(500).isFinalRefusal())
        assertFalse(HttpStatusException(503).isFinalRefusal())
    }
}

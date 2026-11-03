package com.fortind.zenith.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitGateTest {

    private var now = 0L
    private val gate = RateLimitGate(clock = { now })

    private fun heldFor(path: String = "/api/x"): Long {
        var millis = 0L
        while (gate.isHeld(path)) {
            millis += 100
            now += 100
        }
        return millis
    }

    @Test
    fun nothingIsHeldBeforeA429() {
        assertFalse(gate.isHeld("/api/x"))
    }

    @Test
    fun retryAfterWins() {
        gate.hold("/api/x", "7", "20.5")

        assertTrue(heldFor() == 7_000L)
    }

    @Test
    fun theClearHeaderIsUsedWithoutRetryAfter() {
        gate.hold("/api/x", null, "2.5")

        assertTrue(heldFor() == 2_500L)
    }

    @Test
    fun noUsableHeadersHoldFiveSeconds() {
        gate.hold("/api/x", "Wed, 21 Oct 2026 07:28:00 GMT", null)

        assertTrue(heldFor() == 5_000L)
    }

    @Test
    fun holdsAreClampedToOneSecondAndTwoMinutes() {
        gate.hold("/api/low", "0.01", null)
        assertTrue(heldFor("/api/low") == 1_000L)

        gate.hold("/api/high", "9000", null)
        assertTrue(heldFor("/api/high") == 120_000L)
    }

    @Test
    fun onlyThatEndpointIsHeld() {
        gate.hold("/api/x", null, null)

        assertTrue(gate.isHeld("/api/x"))
        assertFalse(gate.isHeld("/api/y"))
    }
}

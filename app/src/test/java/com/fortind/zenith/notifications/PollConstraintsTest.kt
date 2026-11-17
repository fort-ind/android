package com.fortind.zenith.notifications

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollConstraintsTest {

    @Test
    fun pollingNeedsANetworkAndAHealthyBattery() {
        assertEquals(NetworkType.CONNECTED, PollConstraints.requiredNetworkType)
        assertTrue(PollConstraints.requiresBatteryNotLow())
    }

    @Test
    fun pollingDoesNotWaitForTheChargerOrAnIdleDevice() {
        assertFalse(PollConstraints.requiresCharging())
        assertFalse(PollConstraints.requiresDeviceIdle())
    }
}

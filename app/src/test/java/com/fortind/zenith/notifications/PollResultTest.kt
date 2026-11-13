package com.fortind.zenith.notifications

import androidx.work.ListenableWorker.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class PollResultTest {

    @Test
    fun aSuccessfulPollSucceeds() {
        assertEquals(Result.success(), pollResult(SyncOutcome.Done, runAttemptCount = 0))
    }

    @Test
    fun aFailedPollRetriesOnce() {
        assertEquals(Result.retry(), pollResult(SyncOutcome.Retry, runAttemptCount = 0))
    }

    @Test
    fun aSecondFailureWaitsForTheNextPeriodInsteadOfBackingOff() {
        assertEquals(Result.success(), pollResult(SyncOutcome.Retry, runAttemptCount = 1))
        assertEquals(Result.success(), pollResult(SyncOutcome.Retry, runAttemptCount = 7))
    }
}

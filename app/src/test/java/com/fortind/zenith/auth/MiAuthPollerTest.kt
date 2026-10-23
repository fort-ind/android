package com.fortind.zenith.auth

import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MiAuthPollerTest {

    /** Replays [script] in order; the last entry repeats once the script is exhausted. */
    private class ScriptedApi(private val script: List<Any>) : MiAuthApi {
        var calls = 0
            private set

        override suspend fun check(session: String): CheckResult {
            val step = script[minOf(calls++, script.lastIndex)]
            if (step is IOException) throw step
            return step as CheckResult
        }
    }

    private fun poller(api: MiAuthApi) = MiAuthPoller(api, intervalMs = 1_000, timeoutMs = 10_000, maxConsecutiveFailures = 3)

    @Test
    fun returnsTokenOnceApproved() = runTest {
        val api = ScriptedApi(listOf(CheckResult.Pending, CheckResult.Pending, CheckResult.Approved("tok")))

        val outcome = poller(api).poll("s")

        assertEquals(PollOutcome.Approved("tok"), outcome)
        assertEquals(3, api.calls)
        assertEquals(2_000, currentTime)
    }

    @Test
    fun approvedOnFirstCheckDoesNotWait() = runTest {
        val outcome = poller(ScriptedApi(listOf(CheckResult.Approved("tok")))).poll("s")

        assertEquals(PollOutcome.Approved("tok"), outcome)
        assertEquals(0, currentTime)
    }

    @Test
    fun timesOutWhenNeverApproved() = runTest {
        val api = ScriptedApi(listOf(CheckResult.Pending))

        val outcome = poller(api).poll("s")

        assertEquals(PollOutcome.TimedOut, outcome)
        assertEquals(10_000, currentTime)
        assertTrue("polled repeatedly before giving up", api.calls >= 10)
    }

    @Test
    fun cancellationStopsPolling() = runTest {
        val api = ScriptedApi(listOf(CheckResult.Pending))
        val job = async(start = CoroutineStart.UNDISPATCHED) { poller(api).poll("s") }

        advanceTimeBy(3_500)
        val callsBeforeCancel = api.calls
        job.cancel()
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(callsBeforeCancel, api.calls)
    }

    @Test
    fun transientNetworkFailuresAreRetried() = runTest {
        val api = ScriptedApi(listOf(IOException("a"), IOException("b"), CheckResult.Approved("tok")))

        assertEquals(PollOutcome.Approved("tok"), poller(api).poll("s"))
    }

    @Test
    fun consecutiveNetworkFailuresGiveUp() = runTest {
        val api = ScriptedApi(listOf(IOException("down")))

        val outcome = poller(api).poll("s")

        assertTrue(outcome is PollOutcome.NetworkFailure)
        assertEquals(3, api.calls)
    }

    @Test
    fun aSuccessfulCheckResetsTheFailureCount() = runTest {
        val api = ScriptedApi(
            listOf(
                IOException("1"), IOException("2"), CheckResult.Pending,
                IOException("3"), IOException("4"), CheckResult.Approved("tok"),
            )
        )

        assertEquals(PollOutcome.Approved("tok"), poller(api).poll("s"))
    }

    @Test
    fun closedTabWithoutApprovalIsAbandoned() = runTest {
        val api = ScriptedApi(listOf(CheckResult.Pending))

        val outcome = poller(api).poll("s", tabClosed = { true })

        assertEquals(PollOutcome.Abandoned, outcome)
        assertEquals(1, api.calls)
    }

    @Test
    fun approvalMadeJustBeforeTheTabClosedIsStillPickedUp() = runTest {
        // The check that was already in flight when the tab closed saw nothing; the next one must run
        // before giving up, because MiAuth only hands the token out once.
        var closed = false
        val api = object : MiAuthApi {
            var calls = 0
            override suspend fun check(session: String): CheckResult {
                calls++
                if (calls == 1) {
                    closed = true // the tab closes while this check is in flight
                    return CheckResult.Pending
                }
                return CheckResult.Approved("tok")
            }
        }

        val outcome = poller(api).poll("s", tabClosed = { closed })

        assertEquals(PollOutcome.Approved("tok"), outcome)
        assertEquals(2, api.calls)
    }
}

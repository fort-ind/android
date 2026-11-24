package com.fortind.zenith.timeline

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimelinePrefetchTest {

    private val api = FakeNotesApi().apply { timelines[TimelineKind.Home] = listOf(testNote("n2"), testNote("n1")) }

    private fun TestScope.prefetch() = TimelinePrefetch(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

    @Test
    fun theFirstMatchingTakeGetsThePageAndASecondTakeGetsNothing() = runTest {
        val prefetch = prefetch()
        prefetch.start("token", TimelineKind.Home, 30)

        assertEquals(listOf("n2", "n1"), prefetch.take("token", TimelineKind.Home, 30)?.map { it.id })
        assertNull(prefetch.take("token", TimelineKind.Home, 30))
        assertEquals(1, api.calls.size)
    }

    @Test
    fun aDifferentTokenKindOrPageSizeIsNotServed() = runTest {
        val prefetch = prefetch()

        prefetch.start("old", TimelineKind.Home, 30)
        assertNull(prefetch.take("new", TimelineKind.Home, 30))

        prefetch.start("token", TimelineKind.Home, 30)
        assertNull(prefetch.take("token", TimelineKind.Local, 30))

        prefetch.start("token", TimelineKind.Home, 30)
        assertNull(prefetch.take("token", TimelineKind.Home, 20))
    }

    @Test
    fun aFailedFetchFallsBackToNothingSoTheCallerAsksItself() = runTest {
        api.failures[TimelineKind.Home] = IOException("offline")
        val prefetch = prefetch()
        prefetch.start("token", TimelineKind.Home, 30)

        assertNull(prefetch.take("token", TimelineKind.Home, 30))
    }

    @Test
    fun aPageNobodyTakesIsDroppedAfterItsMaximumAge() = runTest {
        val prefetch = prefetch()
        prefetch.start("token", TimelineKind.Home, 30)
        runCurrent()

        advanceTimeBy(TimelinePrefetch.MAX_AGE_MS + 1)

        assertNull(prefetch.take("token", TimelineKind.Home, 30))
    }

    @Test
    fun aNewerStartReplacesTheOlderOne() = runTest {
        val prefetch = prefetch()
        prefetch.start("old", TimelineKind.Home, 30)
        prefetch.start("token", TimelineKind.Home, 30)

        assertEquals(2, prefetch.take("token", TimelineKind.Home, 30)?.size)
    }
}

package com.fortind.zenith.diagnostics

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemReportTest {
    private val environment = AppEnvironment("1.0", 36, "7.1.1", 25, "LG VS985")
    private val dir: File = Files.createTempDirectory("problem-report").toFile()
    private val store = FileProblemReportStore { File(dir, "diagnostics") }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun aReportSurvivesTheJsonRoundTrip() {
        val report = ProblemReport(ProblemKind.Crash, 1_700_000_000_000, environment, "trace", thread = "main", announced = true)

        assertEquals(report, parseProblemReport(report.toJson()))
        assertNull(parseProblemReport(report.copy(thread = null).toJson())?.thread)
    }

    @Test
    fun unreadableOrUnknownReportsParseAsNothing() {
        assertNull(parseProblemReport("not json"))
        assertNull(parseProblemReport("""{"kind":"Meteor","time":1}"""))
        assertNull(parseProblemReport("""{"kind":"Crash"}"""))
    }

    @Test
    fun crashDetailsHoldTheStackTraceAndAreCapped() {
        val details = crashDetails(IllegalStateException("boom", RuntimeException("cause")))

        assertTrue(details.startsWith("java.lang.IllegalStateException: boom"))
        assertTrue("Caused by: java.lang.RuntimeException: cause" in details)
        assertEquals(MAX_REPORT_DETAILS, "x".repeat(MAX_REPORT_DETAILS + 10).cappedDetails().length)
    }

    @Test
    fun theMainThreadIsCutOutOfAnAnrTrace() {
        val trace = listOf(
            "----- pid 4242 at 2026-11-24 14:02:00 -----",
            "Cmd line: com.fortind.zenith",
            "",
            "\"main\" prio=5 tid=1 Blocked",
            "  | group=\"main\" sCount=1",
            "  at com.fortind.zenith.Slow.wait(Slow.kt:3)",
            "",
            "\"Signal Catcher\" daemon prio=10 tid=2 Runnable",
            "  at nothing",
        )

        assertEquals(
            "\"main\" prio=5 tid=1 Blocked\n  | group=\"main\" sCount=1\n  at com.fortind.zenith.Slow.wait(Slow.kt:3)",
            mainThreadOf(trace.asSequence()),
        )
        assertEquals("", mainThreadOf(sequenceOf("no threads here")))
    }

    @Test
    fun theStoreKeepsOneReportAndTheExitFloor() {
        assertNull(store.read())
        assertNull(store.exitFloor())

        val report = ProblemReport(ProblemKind.NotResponding, 5, environment, "stack")
        store.write(report)
        store.write(report.copy(details = "newer"))
        store.setExitFloor(42)

        assertEquals("newer", store.read()?.details)
        assertEquals(42L, store.exitFloor())
        store.clear()
        assertNull(store.read())
        assertEquals(42L, store.exitFloor())
    }

    @Test
    fun theRecorderWritesTheCrashAndHandsItOn() {
        val handedOn = mutableListOf<Throwable>()
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> handedOn += error }
        try {
            val recorder = CrashRecorder(store, environment, clock = { 99 })
            recorder.install()
            recorder.install()
            val error = IllegalArgumentException("bad")

            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), error)

            assertEquals(listOf<Throwable>(error), handedOn)
            val saved = store.read()
            assertEquals(ProblemKind.Crash, saved?.kind)
            assertEquals(99L, saved?.timeMillis)
            assertEquals(Thread.currentThread().name, saved?.thread)
            assertFalse(saved?.announced ?: true)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun theFirstLoadOnlySetsTheFloorSoOldExitsAreNotReported() = runTest {
        val history = ExitHistory { listOf(exit(200, ProblemKind.NotResponding), exit(100, null)) }

        val reports = ProblemReports(store, history, environment, io = Dispatchers.Unconfined)
        reports.load()

        assertNull(reports.report.value)
        assertEquals(200L, store.exitFloor())
    }

    @Test
    fun aNewAnrAfterTheFloorBecomesTheReport() = runTest {
        store.setExitFloor(100)
        val history = ExitHistory { listOf(exit(300, null), exit(250, ProblemKind.NotResponding, "main stack"), exit(90, ProblemKind.NativeCrash)) }

        val reports = ProblemReports(store, history, environment, io = Dispatchers.Unconfined)
        reports.load()

        val report = reports.report.value
        assertEquals(ProblemKind.NotResponding, report?.kind)
        assertEquals(250L, report?.timeMillis)
        assertEquals("main stack", report?.details)
        assertEquals(300L, store.exitFloor())
    }

    @Test
    fun aCrashRecordedLaterIsNotReplacedByAnOlderExit() = runTest {
        store.setExitFloor(100)
        store.write(ProblemReport(ProblemKind.Crash, 400, environment, "crash trace"))
        val history = ExitHistory { listOf(exit(250, ProblemKind.NotResponding)) }

        val reports = ProblemReports(store, history, environment, io = Dispatchers.Unconfined)
        reports.load()

        assertEquals(ProblemKind.Crash, reports.report.value?.kind)
    }

    @Test
    fun announcingAndDeletingAreSaved() = runTest {
        store.write(ProblemReport(ProblemKind.Crash, 400, environment, "crash trace"))
        val reports = ProblemReports(store, exits = null, environment, io = Dispatchers.Unconfined)
        reports.load()
        val loaded = reports.report.value

        reports.markAnnounced()
        reports.load()

        assertTrue(reports.report.value?.announced == true)
        assertTrue(store.read()?.announced == true)
        assertSame(loaded?.details, reports.report.value?.details)

        reports.delete()
        assertNull(reports.report.value)
        assertNull(store.read())
    }

    private fun exit(time: Long, kind: ProblemKind?, details: String = "") = ProcessExit(time, kind) { details }
}

package com.fortind.zenith.account

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "PatternGuardDevice"

@RunWith(AndroidJUnit4::class)
class PatternGuardDeviceTest {

    private fun rules(vararg lines: String): List<PatternRule> =
        (MuteWordSet.parseEditor(lines.joinToString("\n")) as MuteWordsParse.Valid).set.rules.filterIsInstance<PatternRule>()

    private fun workers() = Thread.getAllStackTraces().keys.filter { it.name == "mute-patterns" && it.isAlive }

    @Test
    fun ordinaryPatternsAreCheapOnAWholePage() {
        val guard = PatternGuard()
        val patterns = rules("/colou?r/i", "/\\bspoilers?\\b/i", "/^breaking:/m", "/(foo|bar|baz)+/", "/https?:\\/\\/\\S+/")
        val note = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(50)

        val started = System.nanoTime()
        repeat(90) { guard.matches(patterns, note) }
        val millis = (System.nanoTime() - started) / 1_000_000

        Log.i(TAG, "90 notes of ${note.length} chars against ${patterns.size} patterns took $millis ms")
        assertEquals(0, guard.skippedCount)
        assertTrue("took $millis ms", millis < 2_000)
    }

    @Test
    fun aRunawayPatternDoesNotHoldUpTheCaller() {
        val guard = PatternGuard(budgetMillis = 200)
        val runaway = rules("/^(a+)+\\1\$/")
        val input = "a".repeat(5_000) + "b"

        val started = System.nanoTime()
        val found = guard.matches(runaway, input)
        val millis = (System.nanoTime() - started) / 1_000_000
        Log.i(TAG, "runaway returned $found after $millis ms, skipped=${guard.skippedCount}")

        val fresh = workers()
        Log.i(TAG, "workers right after: ${fresh.map { it.state }}")
        Thread.sleep(5_000)
        val later = workers()
        Log.i(TAG, "workers 5 s later: ${later.map { it.state }}")

        assertTrue("took $millis ms", millis < 1_000)
    }

    @Test
    fun aJavaScriptOnlyPatternIsReportedNotThrown() {
        val brace = MuteWordSet.parseEditor("/[^]/")
        val lookbehind = MuteWordSet.parseEditor("/(?<=a)b/")
        val named = MuteWordSet.parseEditor("/(?<word>a)/")
        Log.i(TAG, "[^] -> $brace; lookbehind -> $lookbehind; named -> $named")
        assertTrue(lookbehind is MuteWordsParse.Valid)
        assertTrue(named is MuteWordsParse.Valid)
    }
}

package com.fortind.zenith.account

import com.fortind.zenith.timeline.Poll
import com.fortind.zenith.timeline.PollChoice
import com.fortind.zenith.timeline.testNote
import com.fortind.zenith.timeline.testUser
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteWordsTest {

    private fun set(text: String): MuteWordSet =
        (MuteWordSet.parseEditor(text) as MuteWordsParse.Valid).set

    private fun filter(soft: String = "", hard: String = "", me: String? = "me", guard: PatternGuard = PatternGuard()) =
        WordMuteFilter(WordMutes(set(soft), set(hard)), me, guard)

    @Test
    fun jsonEntriesKeepTheServersShape() {
        val parsed = MuteWordSet.fromJson(JSONArray("""[["cat"],["red","fox"],"/colou?r/i",[""],[],"not a pattern"]"""))

        assertEquals(listOf("cat", "red fox", "/colou?r/i"), parsed.rules.map { it.editorText })
        assertEquals("""[["cat"],["red","fox"],"/colou?r/i"]""", parsed.toJson().toString().replace("\\/", "/"))
    }

    @Test
    fun editorLinesBecomeRules() {
        val parsed = set("  cat \r\n\nred   fox\n/colou?r/i\n")

        assertEquals("cat\nred fox\n/colou?r/i", parsed.toEditorText())
        assertEquals("cat\nred fox\n/colou?r/i", MuteWordSet.canonicalEditorText("  cat \r\n\nred   fox\n/colou?r/i\n"))
    }

    @Test
    fun aBrokenPatternIsRejectedWhenTyped() {
        assertEquals(MuteWordsParse.Invalid("/(/"), MuteWordSet.parseEditor("cat\n/(/"))
        assertEquals(MuteWordsParse.Invalid("/usr/bin"), MuteWordSet.parseEditor("/usr/bin"))
    }

    @Test
    fun aStoredPatternThisPlatformCannotCompileSurvivesASave() {
        val stored = MuteWordSet.fromJson(JSONArray("""["/(/"]"""))

        val parsed = MuteWordSet.parseEditor("/(/\nnew", stored)

        assertEquals("/(/\nnew", (parsed as MuteWordsParse.Valid).set.toEditorText())
        assertEquals(stored.toEditorText(), MuteWordSet.fromJson(stored.toJson()).toEditorText())
    }

    @Test
    fun patternsCompareBySource() {
        assertEquals(WordMutes(set("/a/i")), WordMutes(set("/a/i")))
        assertFalse(WordMutes(set("/a/i")) == WordMutes(set("/a/")))
    }

    @Test
    fun keywordsAreCaseSensitiveSubstringsAndWordsAreAnded() {
        val muted = filter(soft = "Cat\nred fox")
        val page = listOf(
            testNote("n1", text = "a Catalog"),
            testNote("n2", text = "cat"),
            testNote("n3", text = "fox and red"),
            testNote("n4", text = "red only"),
        )

        val result = muted.apply(page).softMuted

        assertEquals(mapOf("n1" to listOf("Cat"), "n3" to listOf("red fox")), result)
    }

    @Test
    fun patternsUseTheirFlagsAndShowWhatMatched() {
        val result = filter(soft = "/colou?r/i").apply(listOf(testNote("n1", text = "COLOR and colour"))).softMuted

        assertEquals(listOf("COLOR", "colour"), result["n1"])
    }

    @Test
    fun theNoteItRepliesToAndRenotesCount() {
        val parent = testNote("p", text = "spoiler here", user = testUser("other"))
        val quoted = testNote("q", text = "spoiler there", user = testUser("other"))
        val page = listOf(
            testNote("n1", text = "fine", reply = parent),
            testNote("n2", text = "fine", renote = quoted),
            testNote("n3", text = "fine"),
        )

        val result = filter(soft = "spoiler").apply(page).softMuted

        assertEquals(setOf("n1", "n2"), result.keys)
    }

    @Test
    fun yourOwnNotesAreNeverMuted() {
        val mine = testUser("me")
        val other = testUser("other")
        val page = listOf(
            testNote("n1", text = "spoiler", user = mine),
            testNote("n2", text = "fine", user = mine, reply = testNote("p", text = "spoiler", user = mine)),
            testNote("n3", text = "fine", user = mine, reply = testNote("p2", text = "spoiler", user = other)),
        )

        val result = filter(hard = "spoiler").apply(page)

        assertEquals(listOf("n1", "n2"), result.kept.map { it.id })
    }

    @Test
    fun aPureRenoteIsJudgedByTheNoteItShows() {
        val shown = testNote("n1", text = "spoiler", user = testUser("other"))
        val renote = testNote("r1", text = null, user = testUser("me"), renote = shown)

        assertTrue(filter(hard = "spoiler").apply(listOf(renote)).kept.isEmpty())
    }

    @Test
    fun hardMutesDropAndSoftMutesKeep() {
        val page = listOf(testNote("n1", text = "hide"), testNote("n2", text = "soft"), testNote("n3", text = "plain"))

        val result = filter(soft = "soft", hard = "hide").apply(page)

        assertEquals(listOf("n2", "n3"), result.kept.map { it.id })
        assertEquals(setOf("n2"), result.softMuted.keys)
    }

    @Test
    fun theMutedTextIncludesWarningsAltTextAndPollChoices() {
        val poll = Poll(listOf(PollChoice("left", 0, false), PollChoice("right", 0, false)), multiple = false, expiresAtMillis = null)
        val note = testNote("n1", text = "body", poll = poll).copy(cw = "warning")

        assertEquals("warning\nbody\nleft\nright", note.muteText())
    }

    @Test(timeout = 5_000)
    fun aPatternThatOverrunsItsBudgetIsSkippedFromThenOn() {
        val guard = PatternGuard(budgetMillis = 50)
        val slow = set("/^(a+)+\\1\$/")
        val slowRule = slow.rules.filterIsInstance<PatternRule>()
        val input = "a".repeat(26) + "b"

        assertEquals(emptyList<String>(), guard.matches(slowRule, input))
        assertEquals(1, guard.skippedCount)

        val started = System.nanoTime()
        assertEquals(emptyList<String>(), guard.matches(slowRule, input))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 50)
        assertEquals(listOf("ok"), guard.matches(set("/ok/").rules.filterIsInstance<PatternRule>(), "ok"))
    }

    @Test
    fun hostsAreNormalisedAndCheckedLineByLine() {
        assertEquals(
            MuteHostsParse.Valid(listOf("example.social", "b.example")),
            parseMuteHosts(" @Example.Social \n\nb.example\nexample.social"),
        )
        assertEquals(MuteHostsParse.Invalid("not a host"), parseMuteHosts("ok.example\nnot a host"))
        assertEquals("example.social", canonicalHostsText("  @EXAMPLE.social\n"))
    }
}

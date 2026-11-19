package com.fortind.zenith.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    private fun CodeHighlight.kindAt(word: String, occurrence: Int = 0): CodeTokenKind? {
        var from = 0
        var start = -1
        repeat(occurrence + 1) {
            start = text.indexOf(word, from)
            from = start + 1
        }
        require(start >= 0) { "'$word' is not in the text" }
        return tokens.firstOrNull { start >= it.start && start < it.end }?.kind
    }

    @Test
    fun kotlinKeywordsNumbersCommentsAndStringsGetTheirOwnColours() {
        val result = patient().highlight("fun answer() = 42 // the answer\nval name = \"hi\"", "kotlin")

        assertEquals("Kotlin", result.label)
        assertEquals(CodeTokenKind.Keyword, result.kindAt("fun"))
        assertEquals(CodeTokenKind.Number, result.kindAt("42"))
        assertEquals(CodeTokenKind.Comment, result.kindAt("// the answer"))
        assertEquals(CodeTokenKind.StringLiteral, result.kindAt("\"hi\""))
        assertEquals(CodeTokenKind.Function, result.kindAt("answer"))
        assertNull(result.kindAt("="))
    }

    @Test
    fun theTokensLineUpWithTheTextTheyColour() {
        val code = "val a = 1\nval b = \"x\""

        val result = patient().highlight(code, "kotlin")

        assertEquals("val", result.text.substring(result.tokens.first().start, result.tokens.first().end))
        result.tokens.forEach { token ->
            assertTrue(token.start >= 0 && token.end <= result.text.length && token.start < token.end)
        }
    }

    @Test
    fun anUntaggedJsonBlockIsRecognisedAndItsPartsColoured() {
        val result = patient().highlight("{\"name\": \"fort\", \"n\": 7, \"ok\": true}", null)

        assertEquals("JSON", result.label)
        assertEquals(CodeTokenKind.Property, result.kindAt("\"name\""))
        assertEquals(CodeTokenKind.StringLiteral, result.kindAt("\"fort\""))
        assertEquals(CodeTokenKind.Number, result.kindAt("7"))
        assertEquals(CodeTokenKind.Constant, result.kindAt("true"))
    }

    @Test
    fun htmlTagsAttributesAndValuesAreToldApart() {
        val result = patient().highlight("<div class=\"box\">hi</div>", "html")

        assertEquals(CodeTokenKind.Tag, result.kindAt("div"))
        assertEquals(CodeTokenKind.Property, result.kindAt("class"))
        assertEquals(CodeTokenKind.StringLiteral, result.kindAt("\"box\""))
        assertNull(result.kindAt("hi"))
    }

    @Test
    fun sqlKeywordsAreColoured() {
        val result = patient().highlight("SELECT name FROM users WHERE id = 3", "sql")

        assertEquals(CodeTokenKind.Keyword, result.kindAt("SELECT"))
        assertEquals(CodeTokenKind.Number, result.kindAt("3"))
    }

    @Test
    fun tabsAreExpandedBeforeColouringSoOffsetsStayRight() {
        val result = patient().highlight("\tfun a() = 1\n\t\tval b = 2", "kotlin")

        assertEquals("    fun a() = 1\n        val b = 2", result.text)
        assertEquals(CodeTokenKind.Keyword, result.kindAt("fun"))
        assertEquals(CodeTokenKind.Keyword, result.kindAt("val"))
    }

    @Test
    fun anUnsupportedLanguageIsShownPlainWithItsName() {
        val result = patient().highlight("fn main() {}", "rust")

        assertEquals("rust", result.label)
        assertFalse(result.isColored)
        assertEquals("fn main() {}", result.text)
    }

    @Test
    fun aBlockOverTheSizeLimitIsShownPlain() {
        val code = "val a = 1\n".repeat(300)

        val result = patient().highlight(code, "kotlin")

        assertTrue(code.length > CodeHighlighter.MAX_CODE_LENGTH)
        assertFalse(result.isColored)
        assertEquals("Kotlin", result.label)
    }

    @Test
    fun anEmptyBlockIsFine() {
        val result = patient().highlight("", "kotlin")

        assertEquals("", result.text)
        assertFalse(result.isColored)
    }

    @Test
    fun theSecondRequestForTheSameCodeComesFromTheCache() {
        val highlighter = patient()
        val first = highlighter.highlight("val a = 1", "kt")

        assertSame(first, highlighter.peek("val a = 1", "kt"))
        assertSame(first, highlighter.highlight("val a = 1", "kt"))
        assertNull(highlighter.peek("val a = 2", "kt"))
    }

    @Test
    fun theCacheForgetsTheOldestEntries() {
        val highlighter = patient(cacheSize = 2)
        highlighter.highlight("val a = 1", "kt")
        highlighter.highlight("val a = 2", "kt")
        highlighter.highlight("val a = 3", "kt")

        assertNull(highlighter.peek("val a = 1", "kt"))
        assertTrue(highlighter.peek("val a = 3", "kt") != null)
    }

    @Test
    fun aTokenizerThatNeverReturnsIsAbandonedAndTheBlockIsShownPlain() {
        var made = 0
        val highlighter = CodeHighlighter(budgetMillis = 300, coldBudgetMillis = 300, tokenizerFactory = {
            made++
            if (made == 1) {
                CodeTokenizer { _, _ ->
                    Thread.sleep(30_000)
                    emptyList()
                }
            } else {
                CodeTokenizer { _, text -> listOf(CodeToken(0, minOf(3, text.length), CodeTokenKind.Keyword)) }
            }
        })

        val started = System.nanoTime()
        val stuck = highlighter.highlight("val a = 1", "kotlin")
        val tookMillis = (System.nanoTime() - started) / 1_000_000

        assertFalse(stuck.isColored)
        assertEquals("val a = 1", stuck.text)
        assertTrue("took ${tookMillis}ms", tookMillis < 10_000)
        assertEquals(1, highlighter.abandonedWorkers)

        val later = highlighter.highlight("fun b() = 2", "kotlin")
        assertTrue(later.isColored)
        assertEquals(1, highlighter.abandonedWorkers)
        assertEquals(2, made)
    }

    @Test
    fun theSameHostileBlockIsNeverTriedTwice() {
        var calls = 0
        val highlighter = CodeHighlighter(budgetMillis = 200, coldBudgetMillis = 200, tokenizerFactory = {
            CodeTokenizer { _, _ ->
                calls++
                Thread.sleep(30_000)
                emptyList()
            }
        })

        highlighter.highlight("aaaa", "kotlin")
        highlighter.highlight("aaaa", "kotlin")

        assertTrue("tried $calls times", calls <= 1)
        assertEquals(1, highlighter.abandonedWorkers)
    }

    @Test
    fun aTokenizerThatFailsLeavesThePlainBlockAndKeepsWorking() {
        val highlighter = CodeHighlighter(
            budgetMillis = PATIENT_MS,
            tokenizerFactory = { CodeTokenizer { _, _ -> throw IllegalStateException("boom") } },
        )

        val result = highlighter.highlight("val a = 1", "kotlin")

        assertFalse(result.isColored)
        assertEquals(0, highlighter.abandonedWorkers)
    }

    @Test
    fun aGrammarsFirstCallGetsALongerBudgetThanLaterOnes() {
        val highlighter = CodeHighlighter(budgetMillis = 50, coldBudgetMillis = PATIENT_MS, tokenizerFactory = {
            CodeTokenizer { _, text ->
                Thread.sleep(300)
                listOf(CodeToken(0, minOf(3, text.length), CodeTokenKind.Keyword))
            }
        })

        val cold = highlighter.highlight("val a = 1", "kotlin")
        assertTrue("the first call should have had the cold budget", cold.isColored)
        assertEquals(0, highlighter.abandonedWorkers)

        val warm = highlighter.highlight("val b = 2", "kotlin")
        assertFalse("a later call over its 50 ms budget should be abandoned", warm.isColored)
        assertEquals(1, highlighter.abandonedWorkers)
    }

    private fun patient(cacheSize: Int = CodeHighlighter.CACHE_SIZE) = CodeHighlighter(budgetMillis = PATIENT_MS, cacheSize = cacheSize)

    private companion object {
        const val PATIENT_MS = 60_000L
    }
}

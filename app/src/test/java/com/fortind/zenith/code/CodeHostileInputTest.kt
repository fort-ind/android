package com.fortind.zenith.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHostileInputTest {

    private val tags = listOf(
        "js", "json", "html", "xml", "css", "python", "kotlin", "java", "groovy", "scala", "c", "cpp", "csharp", "go",
        "swift", "dart", "bash", "sql", "yaml", "ini", "makefile", "diff", "git", "latex", "regex", "clojure",
    )

    private fun hostileInputs(size: Int): List<Pair<String, String>> = buildList {
        listOf("a", "\"", "'", "`", "/", "*", "<", "&", "(", "[", "{", "\\", "0", ".", "#", "$", " ", "\n", "@", "-", "=", ":", ";", ",", "!", "?", ">", "}", "]", ")", "_", "+", "%", "|", "~", "^")
            .forEach { atom -> add("${atom.replace("\n", "\\n")} x$size" to atom.repeat(size)) }
        listOf(
            "/*", "*/", "//", "<a ", "<!--", "-->", "<![CDATA[", "0x", "1e", "$(", "\"\"\"", "--", "==", "a:", "- ", "a: ", "(a",
            "a\n", "\\\"", "<?", "?>", "\${", "#{", "'''", "=begin", "<<", "</", "/>", "a,", "a.", "a=", "a b", "1.", "1.1.", "0b", "1_", "1e+",
        ).forEach { pair -> add("${pair.replace("\n", "\\n")} repeated" to pair.repeat(size / pair.length)) }
        add("unterminated string" to "\"" + "a".repeat(size))
        add("unterminated comment" to "/*" + "a".repeat(size))
        add("unterminated tag" to "<a" + " b=\"c\"".repeat(size / 6))
        add("unterminated template" to "`" + "\${a".repeat(size / 3))
        add("escaped string" to "\"" + "\\\"".repeat(size / 2))
        add("many lines" to "x = 1; // c\n".repeat(size / 11))
        add("many strings" to "\"a\" ".repeat(size / 4))
        add("deep nesting" to "(".repeat(size / 2) + ")".repeat(size / 2))
        add("spaces then x" to " ".repeat(size) + "x")
        add("digits then x" to "1".repeat(size) + "x")
        add("letters then bang" to "a".repeat(size) + "!")
        add("slashes then x" to "/".repeat(size) + "x")
    }

    @Test
    fun everyTagIsCoveredSoNewLanguagesCannotSkipTheCheck() {
        val covered = tags.mapTo(HashSet()) { CodeLanguages.resolve(it, "").language?.grammar }

        assertEquals(emptySet<String?>(), CodeLanguages.grammars - covered)
    }

    @Test
    fun hostileInputOnTheJvmEngineIsMeasuredAtTheShippedSize() {
        val size = CodeHighlighter.MAX_CODE_LENGTH
        val inputs = hostileInputs(size)
        val slow = ArrayList<String>()
        val offenders = ArrayList<Triple<String, String, String>>()
        var worstMillis = 0L
        tags.forEach { tag ->
            val highlighter = CodeHighlighter(budgetMillis = BUDGET_MILLIS, coldBudgetMillis = BUDGET_MILLIS, cacheSize = 1, maxAbandonedWorkers = Int.MAX_VALUE)
            highlighter.highlight("a", tag)
            inputs.forEach { (name, input) ->
                val abandonedBefore = highlighter.abandonedWorkers
                val started = System.nanoTime()
                highlighter.highlight(input, tag)
                val millis = (System.nanoTime() - started) / 1_000_000
                if (highlighter.abandonedWorkers > abandonedBefore) {
                    offenders += Triple(tag, name, input)
                } else {
                    worstMillis = maxOf(worstMillis, millis)
                }
                if (millis >= REPORT_MILLIS) slow += "$tag / $name: ${millis}ms"
            }
        }
        val confirmed = offenders.filter { (tag, _, input) ->
            val retry = CodeHighlighter(budgetMillis = BUDGET_MILLIS, coldBudgetMillis = BUDGET_MILLIS, cacheSize = 1, maxAbandonedWorkers = Int.MAX_VALUE)
            retry.highlight("a", tag)
            retry.highlight(input, tag)
            retry.abandonedWorkers > 0
        }.map { (tag, name, _) -> "$tag / $name" }
        println("size=$size budget=${BUDGET_MILLIS}ms worst finished=${worstMillis}ms")
        println("abandoned first pass (${offenders.size}), confirmed on retry (${confirmed.size}): $confirmed")
        println("slow (${slow.size}): ${slow.joinToString("; ")}")
        assertTrue("abandoned on hostile input, twice: $confirmed", confirmed.isEmpty())
        assertTrue("worst finished case ${worstMillis}ms has no headroom under the ${CodeHighlighter.BUDGET_MS}ms budget: $slow", worstMillis < MAX_WORST_MILLIS)
    }

    private companion object {
        const val BUDGET_MILLIS = 3_000L
        const val REPORT_MILLIS = 250L
        const val MAX_WORST_MILLIS = 1_500L
    }
}

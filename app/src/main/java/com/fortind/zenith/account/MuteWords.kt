package com.fortind.zenith.account

import com.fortind.zenith.timeline.Note
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.PatternSyntaxException
import org.json.JSONArray

private val PATTERN_LINE = Regex("^/(.+)/(.*)$", RegexOption.DOT_MATCHES_ALL)
private val SEPARATORS = Regex("\\s+")
private const val JS_REGEX_FLAGS = "dgimsuvy"
private const val MAX_MATCHES_PER_RULE = 5
private const val MAX_PATTERN_INPUT = 10_000
private const val PATTERN_BUDGET_MS = 200L

sealed interface MuteRule {
    val editorText: String
}

data class KeywordRule(val words: List<String>) : MuteRule {
    override val editorText: String get() = words.joinToString(" ")

    fun matches(text: String): Boolean = words.all { text.contains(it) }
}

class PatternRule(val source: String) : MuteRule {
    override val editorText: String get() = source

    val regex: Regex? by lazy(LazyThreadSafetyMode.PUBLICATION) { compilePattern(source) }

    override fun equals(other: Any?): Boolean = other is PatternRule && other.source == source

    override fun hashCode(): Int = source.hashCode()

    override fun toString(): String = source
}

private fun compilePattern(source: String): Regex? {
    val parts = PATTERN_LINE.matchEntire(source) ?: return null
    val flags = parts.groupValues[2]
    if (flags.any { it !in JS_REGEX_FLAGS }) return null
    val options = buildSet {
        if ('i' in flags) add(RegexOption.IGNORE_CASE)
        if ('m' in flags) add(RegexOption.MULTILINE)
        if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
    }
    return try {
        Regex(parts.groupValues[1], options)
    } catch (_: PatternSyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

sealed interface MuteWordsParse {
    data class Valid(val set: MuteWordSet) : MuteWordsParse

    data class Invalid(val line: String) : MuteWordsParse
}

data class MuteWordSet(val rules: List<MuteRule> = emptyList()) {
    val isEmpty: Boolean get() = rules.isEmpty()

    fun toJson(): JSONArray = JSONArray().apply {
        rules.forEach { rule ->
            when (rule) {
                is PatternRule -> put(rule.source)
                is KeywordRule -> put(JSONArray().apply { rule.words.forEach { word -> put(word) } })
            }
        }
    }

    fun toEditorText(): String = rules.joinToString("\n") { it.editorText }

    companion object {
        val Empty = MuteWordSet()

        fun fromJson(array: JSONArray?): MuteWordSet {
            if (array == null) return Empty
            val rules = ArrayList<MuteRule>(array.length())
            for (index in 0 until array.length()) {
                when (val entry = array.opt(index)) {
                    is JSONArray -> {
                        val words = (0 until entry.length()).mapNotNull { (entry.opt(it) as? String)?.takeIf(String::isNotEmpty) }
                        if (words.isNotEmpty()) rules += KeywordRule(words)
                    }
                    is String -> if (PATTERN_LINE.matches(entry)) rules += PatternRule(entry)
                }
            }
            return if (rules.isEmpty()) Empty else MuteWordSet(rules)
        }

        fun parseEditor(text: String, stored: MuteWordSet = Empty): MuteWordsParse {
            val rules = ArrayList<MuteRule>()
            editorLines(text).forEach { line ->
                if (PATTERN_LINE.matches(line)) {
                    val rule = PatternRule(line)
                    if (rule.regex == null && rule !in stored.rules) return MuteWordsParse.Invalid(line)
                    rules += rule
                } else {
                    rules += KeywordRule(line.split(SEPARATORS))
                }
            }
            return MuteWordsParse.Valid(if (rules.isEmpty()) Empty else MuteWordSet(rules))
        }

        fun canonicalEditorText(text: String): String = editorLines(text).joinToString("\n") { line ->
            if (PATTERN_LINE.matches(line)) line else line.split(SEPARATORS).joinToString(" ")
        }

        private fun editorLines(text: String): List<String> =
            text.split("\r\n", "\n", "\r").map(String::trim).filter(String::isNotEmpty)
    }
}

data class WordMutes(val soft: MuteWordSet = MuteWordSet.Empty, val hard: MuteWordSet = MuteWordSet.Empty) {
    val isEmpty: Boolean get() = soft.isEmpty && hard.isEmpty
}

class PatternGuard(private val budgetMillis: Long = PATTERN_BUDGET_MS) {
    private val skipped: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private var worker: ExecutorService? = null

    val skippedCount: Int get() = skipped.size

    @Synchronized
    fun matches(rules: List<PatternRule>, text: String): List<String> {
        val live = rules.filter { it.source !in skipped && it.regex != null }
        if (live.isEmpty()) return emptyList()
        val input = if (text.length > MAX_PATTERN_INPUT) text.substring(0, MAX_PATTERN_INPUT) else text
        val running = AtomicReference<String?>()
        val found = CopyOnWriteArrayList<String>()
        val executor = worker ?: newWorker().also { worker = it }
        val task = executor.submit(
            Callable {
                live.forEach { rule ->
                    running.set(rule.source)
                    try {
                        rule.regex?.findAll(input)
                            ?.map { it.value }
                            ?.filter(String::isNotEmpty)
                            ?.take(MAX_MATCHES_PER_RULE)
                            ?.forEach(found::add)
                    } catch (_: RuntimeException) {
                        skipped += rule.source
                    } catch (_: StackOverflowError) {
                        skipped += rule.source
                    }
                }
            },
        )
        try {
            task.get(budgetMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            running.get()?.let(skipped::add)
            task.cancel(true)
            executor.shutdownNow()
            worker = null
        } catch (_: ExecutionException) {
            running.get()?.let(skipped::add)
        }
        return found.toList()
    }

    private fun newWorker(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mute-patterns").apply { isDaemon = true }
    }
}

class MutedPage(val kept: List<Note>, val softMuted: Map<String, List<String>>)

class WordMuteFilter(
    val mutes: WordMutes,
    private val myUserId: String?,
    private val guard: PatternGuard,
) {
    val isEmpty: Boolean get() = mutes.isEmpty

    fun apply(notes: List<Note>): MutedPage {
        if (mutes.isEmpty) return MutedPage(notes, emptyMap())
        val softMuted = HashMap<String, List<String>>()
        val kept = notes.filter { entry ->
            if (matches(entry, mutes.hard).isNotEmpty()) return@filter false
            val soft = matches(entry, mutes.soft)
            if (soft.isNotEmpty()) softMuted[entry.id] = soft
            true
        }
        return MutedPage(kept, softMuted)
    }

    private fun matches(entry: Note, set: MuteWordSet): List<String> {
        if (set.isEmpty) return emptyList()
        val shown = entry.target
        if (shown.deleted || shown.isHidden) return emptyList()
        val found = LinkedHashSet<String>()
        listOfNotNull(shown, shown.reply, shown.renote)
            .filter { it.user.id != myUserId }
            .forEach { candidate -> found += match(set, candidate.muteText()) }
        return found.toList()
    }

    private fun match(set: MuteWordSet, text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val keywords = set.rules.filterIsInstance<KeywordRule>().filter { it.matches(text) }.map { it.editorText }
        return keywords + guard.matches(set.rules.filterIsInstance<PatternRule>(), text)
    }
}

fun Note.muteText(): String = buildList {
    cw?.let(::add)
    text?.let(::add)
    files.forEach { file -> file.altText?.let(::add) }
    poll?.choices?.forEach { add(it.text) }
}.filter(String::isNotEmpty).joinToString("\n").trim()

private val HOST_LINE = Regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$")

sealed interface MuteHostsParse {
    data class Valid(val hosts: List<String>) : MuteHostsParse

    data class Invalid(val line: String) : MuteHostsParse
}

fun parseMuteHosts(text: String): MuteHostsParse {
    val hosts = LinkedHashSet<String>()
    text.split("\r\n", "\n", "\r").forEach { raw ->
        val line = raw.trim().trimStart('@').lowercase()
        if (line.isEmpty()) return@forEach
        if (!HOST_LINE.matches(line)) return MuteHostsParse.Invalid(raw.trim())
        hosts += line
    }
    return MuteHostsParse.Valid(hosts.toList())
}

fun canonicalHostsText(text: String): String = text.split("\r\n", "\n", "\r")
    .map { it.trim().trimStart('@').lowercase() }
    .filter(String::isNotEmpty)
    .distinct()
    .joinToString("\n")

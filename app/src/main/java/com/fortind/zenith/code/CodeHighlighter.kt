package com.fortind.zenith.code

import io.noties.prism4j.DefaultGrammarLocator
import io.noties.prism4j.Node
import io.noties.prism4j.Prism4j
import io.noties.prism4j.Syntax
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface CodeTokenizer {
    fun tokenize(grammar: String, text: String): List<CodeToken>?
}

class PrismTokenizer : CodeTokenizer {
    private val prism = Prism4j(DefaultGrammarLocator())

    override fun tokenize(grammar: String, text: String): List<CodeToken>? {
        val loaded = prism.grammar(grammar) ?: return null
        return flatten(prism.tokenize(text, loaded), text.length)
    }
}

internal fun flatten(nodes: List<Node>, expectedLength: Int): List<CodeToken>? {
    val walker = Walker()
    walker.walk(nodes, null, 0)
    return walker.tokens.takeIf { walker.offset == expectedLength }
}

private class Walker {
    val tokens = ArrayList<CodeToken>()
    var offset = 0

    fun walk(nodes: List<Node>, inherited: CodeTokenKind?, depth: Int) {
        for (node in nodes) {
            if (node is Syntax) {
                val kind = kindOf(node.type(), node.alias()) ?: inherited
                if (depth >= MAX_DEPTH) {
                    cover(node.textLength(), kind)
                } else {
                    walk(node.children(), kind, depth + 1)
                }
            } else {
                cover(node.textLength(), inherited)
            }
        }
    }

    private fun cover(length: Int, kind: CodeTokenKind?) {
        if (kind != null && length > 0) {
            val last = tokens.lastOrNull()
            if (last != null && last.kind == kind && last.end == offset) {
                tokens[tokens.lastIndex] = last.copy(end = offset + length)
            } else {
                tokens += CodeToken(offset, offset + length, kind)
            }
        }
        offset += length
    }
}

private const val MAX_DEPTH = 32

private val Kinds: Map<String, CodeTokenKind> = mapOf(
    "comment" to CodeTokenKind.Comment,
    "block-comment" to CodeTokenKind.Comment,
    "doc-comment" to CodeTokenKind.Comment,
    "prolog" to CodeTokenKind.Meta,
    "doctype" to CodeTokenKind.Meta,
    "cdata" to CodeTokenKind.Meta,
    "atrule" to CodeTokenKind.Meta,
    "important" to CodeTokenKind.Meta,
    "directive" to CodeTokenKind.Meta,
    "macro" to CodeTokenKind.Meta,
    "string" to CodeTokenKind.StringLiteral,
    "char" to CodeTokenKind.StringLiteral,
    "attr-value" to CodeTokenKind.StringLiteral,
    "template-string" to CodeTokenKind.StringLiteral,
    "triple-quoted-string" to CodeTokenKind.StringLiteral,
    "heredoc" to CodeTokenKind.StringLiteral,
    "regex" to CodeTokenKind.StringLiteral,
    "url" to CodeTokenKind.StringLiteral,
    "number" to CodeTokenKind.Number,
    "hexcode" to CodeTokenKind.Number,
    "boolean" to CodeTokenKind.Constant,
    "constant" to CodeTokenKind.Constant,
    "symbol" to CodeTokenKind.Constant,
    "entity" to CodeTokenKind.Constant,
    "keyword" to CodeTokenKind.Keyword,
    "builtin" to CodeTokenKind.Function,
    "function" to CodeTokenKind.Function,
    "function-variable" to CodeTokenKind.Function,
    "method" to CodeTokenKind.Function,
    "class-name" to CodeTokenKind.Type,
    "namespace" to CodeTokenKind.Type,
    "property" to CodeTokenKind.Property,
    "attr-name" to CodeTokenKind.Property,
    "variable" to CodeTokenKind.Property,
    "parameter" to CodeTokenKind.Property,
    "key" to CodeTokenKind.Property,
    "tag" to CodeTokenKind.Tag,
    "selector" to CodeTokenKind.Tag,
    "inserted" to CodeTokenKind.Inserted,
    "deleted" to CodeTokenKind.Deleted,
)

internal fun kindOf(type: String?, alias: String?): CodeTokenKind? =
    Kinds[type] ?: alias?.split(' ')?.firstNotNullOfOrNull { Kinds[it] }

class CodeHighlighter(
    private val budgetMillis: Long = BUDGET_MS,
    private val maxLength: Int = MAX_CODE_LENGTH,
    private val cacheSize: Int = CACHE_SIZE,
    private val tokenizerFactory: () -> CodeTokenizer = { PrismTokenizer() },
    private val coldBudgetMillis: Long = budgetMillis * COLD_FACTOR,
    private val maxAbandonedWorkers: Int = MAX_ABANDONED_WORKERS,
) {
    private val cache = object : LinkedHashMap<String, CodeHighlight>(cacheSize, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CodeHighlight>): Boolean = size > cacheSize
    }
    private var worker: ExecutorService? = null
    private var tokenizer: CodeTokenizer? = null
    private val warmGrammars = HashSet<String>()

    val abandonedWorkers: Int get() = synchronized(this) { abandoned }
    private var abandoned = 0

    fun peek(code: String, tag: String?): CodeHighlight? = synchronized(cache) { cache[cacheKey(code, tag)] }

    fun highlight(code: String, tag: String?): CodeHighlight {
        val text = expandTabs(code)
        val resolved = CodeLanguages.resolve(tag, code.takeIf { it.length <= maxLength }.orEmpty())
        val language = resolved.language
        if (language == null || code.length > maxLength) return CodeHighlight(resolved.label, text, emptyList())
        val key = cacheKey(code, tag)
        synchronized(cache) { cache[key] }?.let { return it }
        val result = CodeHighlight(resolved.label, text, tokenize(language.grammar, text).orEmpty())
        synchronized(cache) { cache[key] = result }
        return result
    }

    suspend fun highlightOffMain(code: String, tag: String?): CodeHighlight = withContext(Dispatchers.IO) { highlight(code, tag) }

    @Synchronized
    private fun tokenize(grammar: String, text: String): List<CodeToken>? {
        if (abandoned >= maxAbandonedWorkers) return null
        val executor = worker ?: newWorker().also { worker = it }
        val engine = tokenizer ?: tokenizerFactory().also { tokenizer = it }
        val task = executor.submit(Callable { engine.tokenize(grammar, text) })
        val budget = if (grammar in warmGrammars) budgetMillis else coldBudgetMillis
        return try {
            task.get(budget, TimeUnit.MILLISECONDS).also { warmGrammars += grammar }
        } catch (_: TimeoutException) {
            task.cancel(true)
            executor.shutdownNow()
            worker = null
            tokenizer = null
            warmGrammars.clear()
            abandoned++
            null
        } catch (_: ExecutionException) {
            null
        }
    }

    private fun cacheKey(code: String, tag: String?): String = tag?.trim()?.lowercase().orEmpty() + KEY_SEPARATOR + code

    private fun newWorker(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "code-highlight").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    companion object {
        const val BUDGET_MS = 750L
        const val MAX_CODE_LENGTH = 2_500
        const val CACHE_SIZE = 48
        const val COLD_FACTOR = 4
        const val MAX_ABANDONED_WORKERS = 2
        private const val LOAD_FACTOR = 0.75f
        private const val KEY_SEPARATOR = '\u0000'
    }
}

object CodeHighlights {
    private val highlighter = CodeHighlighter()

    fun peek(code: String, tag: String?): CodeHighlight? = highlighter.peek(code, tag)

    suspend fun highlight(code: String, tag: String?): CodeHighlight = highlighter.highlightOffMain(code, tag)
}

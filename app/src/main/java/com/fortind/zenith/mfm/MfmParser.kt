package com.fortind.zenith.mfm

object MfmParser {
    const val DEFAULT_NEST_LIMIT = 20

    fun parse(input: String, nestLimit: Int = DEFAULT_NEST_LIMIT): List<MfmNode> =
        FullParser(input.replace("\r\n", "\n"), nestLimit).parseAll()

    fun parseSimple(input: String): List<MfmNode> {
        val out = NodeBuilder()
        var pos = 0
        while (pos < input.length) {
            val emoji = readEmojiCode(input, pos)
            val plain = if (emoji == null) readPlainTag(input, pos) else null
            when {
                emoji != null -> {
                    out.add(emoji.node)
                    pos = emoji.end
                }
                plain != null -> {
                    out.add(plain.node)
                    pos = plain.end
                }
                else -> out.addChar(input[pos++])
            }
        }
        return out.build()
    }
}

private class Parsed(val node: MfmNode, val end: Int)

private class Sequence(val nodes: List<MfmNode>, val end: Int, val closed: Boolean)

private class NodeBuilder {
    private val nodes = mutableListOf<MfmNode>()
    private val text = StringBuilder()

    fun addChar(char: Char) {
        text.append(char)
    }

    fun add(node: MfmNode) {
        if (node is MfmText) {
            text.append(node.text)
            return
        }
        flush()
        nodes += node
    }

    fun trimTrailingNewline() {
        if (text.isNotEmpty() && text.last() == '\n') text.setLength(text.length - 1)
    }

    fun build(): List<MfmNode> {
        flush()
        return nodes.toList()
    }

    private fun flush() {
        if (text.isNotEmpty()) {
            nodes += MfmText(text.toString())
            text.setLength(0)
        }
    }
}

private enum class Kind {
    Quote, CodeBlock, MathBlock, Center, Search,
    Big, BoldAsta, BoldUnder, ItalicAsta, ItalicUnder, Strike,
    BoldTag, ItalicTag, StrikeTag, SmallTag, PlainTag,
    InlineCode, MathInline, Fn, Emoji, Mention, Hashtag, Url, UrlAlt, Link, SilentLink,
}

private val SEARCH_LINE = Regex("^(.+?)[ 　](検索|\\[検索]|Search|\\[Search])$", RegexOption.IGNORE_CASE)
private val FN_NAME = Regex("[a-zA-Z0-9_]+")
private val FN_ARGS = Regex("[a-zA-Z0-9_.,=\\-#]+")
private const val URL_CHARS = ".,_/:%#@$&?!~=+-"
private const val HASHTAG_STOP = ".,!?'\"#:/【】<>[]()「」（）"

private fun Char.isAsciiAlphanumeric(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private fun String.charBefore(pos: Int): Char? = if (pos > 0) this[pos - 1] else null

private fun readEmojiCode(src: String, pos: Int): Parsed? {
    if (src[pos] != ':' || src.charBefore(pos)?.isAsciiAlphanumeric() == true) return null
    var end = pos + 1
    while (end < src.length && (src[end].isAsciiAlphanumeric() || src[end] in "_+-")) end++
    if (end == pos + 1 || end >= src.length || src[end] != ':') return null
    if (end + 1 < src.length && src[end + 1].isAsciiAlphanumeric()) return null
    return Parsed(MfmEmojiCode(src.substring(pos + 1, end)), end + 1)
}

private fun readPlainTag(src: String, pos: Int): Parsed? {
    if (!src.startsWith("<plain>", pos)) return null
    val start = pos + "<plain>".length
    val close = src.indexOf("</plain>", start)
    if (close < 0) return null
    val content = src.substring(start, close).removePrefix("\n").removeSuffix("\n")
    return Parsed(MfmText(content), close + "</plain>".length)
}

private class FullParser(private val src: String, private val nestLimit: Int) {
    private val memo = HashMap<Long, Parsed?>()

    fun parseAll(): List<MfmNode> = parseSequence(0, null, 0, blocks = true, links = true).nodes

    private fun parseSequence(start: Int, stop: String?, depth: Int, blocks: Boolean, links: Boolean): Sequence {
        val out = NodeBuilder()
        var pos = start
        while (pos < src.length) {
            if (stop != null && src.startsWith(stop, pos)) return Sequence(out.build(), pos, true)
            val parsed = (if (blocks && atLineStart(pos)) block(pos, depth) else null) ?: inline(pos, depth, links)
            if (parsed == null) {
                out.addChar(src[pos++])
                continue
            }
            if (parsed.node.isBlock) out.trimTrailingNewline()
            out.add(parsed.node)
            pos = parsed.end
            if (parsed.node.isBlock && pos < src.length && src[pos] == '\n') pos++
        }
        return Sequence(out.build(), pos, stop == null)
    }

    private fun atLineStart(pos: Int) = pos == 0 || src[pos - 1] == '\n'

    private fun memoized(kind: Kind, pos: Int, links: Boolean, compute: () -> Parsed?): Parsed? {
        val key = (pos.toLong() * Kind.entries.size + kind.ordinal) * 2 + if (links) 1 else 0
        if (memo.containsKey(key)) return memo[key]
        return compute().also { memo[key] = it }
    }

    private fun block(pos: Int, depth: Int): Parsed? = when (src[pos]) {
        '>' -> memoized(Kind.Quote, pos, true) { quote(pos, depth) }
        '`' -> memoized(Kind.CodeBlock, pos, true) { codeBlock(pos) }
        '\\' -> memoized(Kind.MathBlock, pos, true) { mathBlock(pos) }
        '<' -> memoized(Kind.Center, pos, true) { center(pos, depth) }
        else -> null
    } ?: memoized(Kind.Search, pos, true) { search(pos) }

    private fun lineEnd(pos: Int): Int = src.indexOf('\n', pos).let { if (it < 0) src.length else it }

    private fun quote(pos: Int, depth: Int): Parsed? {
        val lines = mutableListOf<String>()
        var cursor = pos
        var end = pos
        while (cursor < src.length && src[cursor] == '>') {
            val lineEnd = lineEnd(cursor)
            lines += src.substring(cursor + 1, lineEnd).removePrefix(" ")
            end = lineEnd
            cursor = lineEnd + 1
        }
        val content = lines.joinToString("\n")
        if (content.isBlank()) return null
        val children = if (depth + 1 >= nestLimit) {
            listOf(MfmText(content))
        } else {
            FullParser(content, nestLimit - depth - 1).parseAll()
        }
        return Parsed(MfmQuote(children), end)
    }

    private fun codeBlock(pos: Int): Parsed? {
        if (!src.startsWith("```", pos)) return null
        val firstLineEnd = lineEnd(pos)
        if (firstLineEnd >= src.length) return null
        val lang = src.substring(pos + 3, firstLineEnd).trim()
        if ('`' in lang) return null
        var search = firstLineEnd
        while (true) {
            val close = src.indexOf("\n```", search)
            if (close < 0) return null
            val after = close + 4
            if (after == src.length || src[after] == '\n') {
                val code = src.substring(firstLineEnd + 1, close)
                return Parsed(MfmBlockCode(code, lang.ifEmpty { null }), after)
            }
            search = close + 1
        }
    }

    private fun mathBlock(pos: Int): Parsed? {
        if (!src.startsWith("\\[", pos)) return null
        val close = src.indexOf("\\]", pos + 2)
        if (close < 0) return null
        val after = close + 2
        if (after != src.length && src[after] != '\n') return null
        val formula = src.substring(pos + 2, close).trim('\n')
        if (formula.isBlank()) return null
        return Parsed(MfmMathBlock(formula), after)
    }

    private fun center(pos: Int, depth: Int): Parsed? {
        if (!src.startsWith("<center>", pos) || depth + 1 >= nestLimit) return null
        var start = pos + "<center>".length
        if (start < src.length && src[start] == '\n') start++
        val sequence = parseSequence(start, "</center>", depth + 1, blocks = false, links = true)
        if (!sequence.closed) return null
        val after = sequence.end + "</center>".length
        if (after != src.length && src[after] != '\n') return null
        val children = sequence.nodes.toMutableList()
        (children.lastOrNull() as? MfmText)?.let { last ->
            val trimmed = last.text.removeSuffix("\n")
            if (trimmed.isEmpty()) children.removeAt(children.lastIndex) else children[children.lastIndex] = MfmText(trimmed)
        }
        if (children.isEmpty()) return null
        return Parsed(MfmCenter(children), after)
    }

    private fun search(pos: Int): Parsed? {
        val end = lineEnd(pos)
        val match = SEARCH_LINE.matchEntire(src.substring(pos, end)) ?: return null
        val query = match.groupValues[1]
        if (query.isBlank()) return null
        return Parsed(MfmSearch(query, match.value), end)
    }

    private fun inline(pos: Int, depth: Int, links: Boolean): Parsed? {
        val nested = depth + 1 < nestLimit
        return when (src[pos]) {
            ':' -> memoized(Kind.Emoji, pos, links) { readEmojiCode(src, pos) }
            '*' -> if (!nested) {
                null
            } else {
                memoized(Kind.Big, pos, links) { delimited(pos, "***", depth, links) { MfmFn("tada", emptyMap(), it) } }
                    ?: memoized(Kind.BoldAsta, pos, links) { delimited(pos, "**", depth, links, ::MfmBold) }
                    ?: memoized(Kind.ItalicAsta, pos, links) { simpleEmphasis(pos, '*', ::MfmItalic) }
            }
            '_' -> memoized(Kind.BoldUnder, pos, links) { simpleEmphasis(pos, "__", ::MfmBold) }
                ?: memoized(Kind.ItalicUnder, pos, links) { simpleEmphasis(pos, '_', ::MfmItalic) }
            '~' -> if (nested) memoized(Kind.Strike, pos, links) { strikeWave(pos, depth, links) } else null
            '<' -> tag(pos, depth, links, nested)
            '`' -> memoized(Kind.InlineCode, pos, links) { inlineCode(pos) }
            '\\' -> memoized(Kind.MathInline, pos, links) { mathInline(pos) }
            '$' -> if (nested) memoized(Kind.Fn, pos, links) { fn(pos, depth, links) } else null
            '@' -> if (links) memoized(Kind.Mention, pos, links) { mention(pos) } else null
            '#' -> memoized(Kind.Hashtag, pos, links) { hashtag(pos) }
            'h' -> if (links) memoized(Kind.Url, pos, links) { url(pos) } else null
            '[' -> if (links && nested) memoized(Kind.Link, pos, links) { link(pos, pos + 1, false, depth) } else null
            '?' -> if (links && nested && src.startsWith("?[", pos)) {
                memoized(Kind.SilentLink, pos, links) { link(pos, pos + 2, true, depth) }
            } else {
                null
            }
            else -> null
        }
    }

    private fun tag(pos: Int, depth: Int, links: Boolean, nested: Boolean): Parsed? = when {
        src.startsWith("<plain>", pos) -> memoized(Kind.PlainTag, pos, links) { readPlainTag(src, pos) }
        src.startsWith("<http", pos) && links -> memoized(Kind.UrlAlt, pos, links) { urlAlt(pos) }
        !nested -> null
        src.startsWith("<b>", pos) -> memoized(Kind.BoldTag, pos, links) { tagged(pos, "b", depth, links, ::MfmBold) }
        src.startsWith("<i>", pos) -> memoized(Kind.ItalicTag, pos, links) { tagged(pos, "i", depth, links, ::MfmItalic) }
        src.startsWith("<s>", pos) -> memoized(Kind.StrikeTag, pos, links) { tagged(pos, "s", depth, links, ::MfmStrike) }
        src.startsWith("<small>", pos) -> memoized(Kind.SmallTag, pos, links) { tagged(pos, "small", depth, links, ::MfmSmall) }
        else -> null
    }

    private fun delimited(
        pos: Int,
        marker: String,
        depth: Int,
        links: Boolean,
        build: (List<MfmNode>) -> MfmNode,
    ): Parsed? {
        if (!src.startsWith(marker, pos) || src.indexOf(marker, pos + marker.length) < 0) return null
        val sequence = parseSequence(pos + marker.length, marker, depth + 1, blocks = false, links = links)
        if (!sequence.closed || sequence.nodes.isEmpty()) return null
        return Parsed(build(sequence.nodes), sequence.end + marker.length)
    }

    private fun tagged(pos: Int, name: String, depth: Int, links: Boolean, build: (List<MfmNode>) -> MfmNode): Parsed? {
        val open = "<$name>"
        val close = "</$name>"
        if (src.indexOf(close, pos + open.length) < 0) return null
        val sequence = parseSequence(pos + open.length, close, depth + 1, blocks = false, links = links)
        if (!sequence.closed || sequence.nodes.isEmpty()) return null
        return Parsed(build(sequence.nodes), sequence.end + close.length)
    }

    private fun simpleEmphasis(pos: Int, marker: Char, build: (List<MfmNode>) -> MfmNode): Parsed? {
        if (src.charBefore(pos)?.isAsciiAlphanumeric() == true) return null
        return emphasisContent(pos, marker.toString(), build)
    }

    private fun simpleEmphasis(pos: Int, marker: String, build: (List<MfmNode>) -> MfmNode): Parsed? =
        emphasisContent(pos, marker, build)

    private fun emphasisContent(pos: Int, marker: String, build: (List<MfmNode>) -> MfmNode): Parsed? {
        if (!src.startsWith(marker, pos)) return null
        val start = pos + marker.length
        var end = start
        while (end < src.length && (src[end].isAsciiAlphanumeric() || src[end] == ' ' || src[end] == '\t')) end++
        if (end == start || !src.startsWith(marker, end)) return null
        if (marker.length == 1 && src.startsWith(marker + marker, end)) return null
        return Parsed(build(listOf(MfmText(src.substring(start, end)))), end + marker.length)
    }

    private fun strikeWave(pos: Int, depth: Int, links: Boolean): Parsed? {
        if (!src.startsWith("~~", pos)) return null
        val close = src.indexOf("~~", pos + 2)
        if (close < 0 || src.indexOf('\n', pos).let { it in 0 until close }) return null
        val sequence = parseSequence(pos + 2, "~~", depth + 1, blocks = false, links = links)
        if (!sequence.closed || sequence.nodes.isEmpty()) return null
        return Parsed(MfmStrike(sequence.nodes), sequence.end + 2)
    }

    private fun inlineCode(pos: Int): Parsed? {
        if (src.startsWith("```", pos)) return null
        var end = pos + 1
        while (end < src.length && src[end] != '`' && src[end] != '\n' && src[end] != '´') end++
        if (end >= src.length || src[end] != '`' || end == pos + 1) return null
        return Parsed(MfmInlineCode(src.substring(pos + 1, end)), end + 1)
    }

    private fun mathInline(pos: Int): Parsed? {
        if (!src.startsWith("\\(", pos)) return null
        val close = src.indexOf("\\)", pos + 2)
        if (close < 0) return null
        val formula = src.substring(pos + 2, close)
        if (formula.isEmpty() || '\n' in formula) return null
        return Parsed(MfmMathInline(formula), close + 2)
    }

    private fun fn(pos: Int, depth: Int, links: Boolean): Parsed? {
        if (!src.startsWith("$[", pos)) return null
        val name = FN_NAME.matchAt(src, pos + 2)?.value ?: return null
        var cursor = pos + 2 + name.length
        val args = LinkedHashMap<String, String?>()
        if (cursor < src.length && src[cursor] == '.') {
            val raw = FN_ARGS.matchAt(src, cursor + 1)?.value ?: return null
            raw.split(',').filter { it.isNotEmpty() }.forEach { arg ->
                val split = arg.indexOf('=')
                if (split < 0) args[arg] = null else args[arg.substring(0, split)] = arg.substring(split + 1)
            }
            cursor += 1 + raw.length
        }
        if (cursor >= src.length || src[cursor] != ' ') return null
        if (src.indexOf(']', cursor) < 0) return null
        val sequence = parseSequence(cursor + 1, "]", depth + 1, blocks = false, links = links)
        if (!sequence.closed) return null
        return Parsed(MfmFn(name, args, sequence.nodes), sequence.end + 1)
    }

    private fun mention(pos: Int): Parsed? {
        if (src.charBefore(pos)?.isAsciiAlphanumeric() == true) return null
        var end = pos + 1
        while (end < src.length && (src[end].isAsciiAlphanumeric() || src[end] == '_' || src[end] == '-')) end++
        var username = src.substring(pos + 1, end)
        val trimmedUser = username.trimEnd('-')
        end -= username.length - trimmedUser.length
        username = trimmedUser
        if (username.isEmpty() || username.startsWith('-')) return null
        var host: String? = null
        if (end < src.length && src[end] == '@') {
            var hostEnd = end + 1
            while (hostEnd < src.length && (src[hostEnd].isAsciiAlphanumeric() || src[hostEnd] in "_.-")) hostEnd++
            val candidate = src.substring(end + 1, hostEnd).trimEnd('.', '-')
            if (candidate.isNotEmpty() && !candidate.startsWith('.') && !candidate.startsWith('-')) {
                host = candidate
                end = end + 1 + candidate.length
            }
        }
        return Parsed(MfmMention(username, host), end)
    }

    private fun hashtag(pos: Int): Parsed? {
        if (src.charBefore(pos)?.isAsciiAlphanumeric() == true) return null
        var end = pos + 1
        while (end < src.length && !src[end].isWhitespace() && src[end] !in HASHTAG_STOP) end++
        val tag = src.substring(pos + 1, end)
        if (tag.isEmpty() || tag.all { it.isDigit() }) return null
        return Parsed(MfmHashtag(tag), end)
    }

    private fun readUrl(start: Int): Int? {
        val scheme = when {
            src.startsWith("https://", start) -> 8
            src.startsWith("http://", start) -> 7
            else -> return null
        }
        var end = start + scheme
        var parens = 0
        var brackets = 0
        while (end < src.length) {
            val char = src[end]
            when {
                char.isAsciiAlphanumeric() || char in URL_CHARS -> Unit
                char == '(' -> parens++
                char == '[' -> brackets++
                char == ')' && parens > 0 -> parens--
                char == ']' && brackets > 0 -> brackets--
                else -> break
            }
            end++
        }
        while (end > start + scheme && src[end - 1] in ".,") end--
        if (parens > 0 || brackets > 0) {
            while (end > start + scheme && src[end - 1] in "([") end--
        }
        return if (end > start + scheme) end else null
    }

    private fun url(pos: Int): Parsed? {
        val end = readUrl(pos) ?: return null
        return Parsed(MfmUrl(src.substring(pos, end), brackets = false), end)
    }

    private fun urlAlt(pos: Int): Parsed? {
        val close = src.indexOf('>', pos + 1)
        if (close < 0) return null
        val candidate = src.substring(pos + 1, close)
        if (candidate.any { it.isWhitespace() }) return null
        if (!candidate.startsWith("https://") && !candidate.startsWith("http://")) return null
        return Parsed(MfmUrl(candidate, brackets = true), close + 1)
    }

    private fun link(pos: Int, labelStart: Int, silent: Boolean, depth: Int): Parsed? {
        val separator = src.indexOf("](", labelStart)
        if (separator < 0 || src.indexOf('\n', labelStart).let { it in 0 until separator }) return null
        val label = parseSequence(labelStart, "](", depth + 1, blocks = false, links = false)
        if (!label.closed || label.nodes.isEmpty()) return null
        val urlStart = label.end + 2
        val urlEnd = readUrl(urlStart) ?: return null
        if (urlEnd >= src.length || src[urlEnd] != ')') return null
        return Parsed(MfmLink(silent, src.substring(urlStart, urlEnd), label.nodes), urlEnd + 1)
    }
}

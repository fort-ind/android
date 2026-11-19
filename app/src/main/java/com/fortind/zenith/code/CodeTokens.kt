package com.fortind.zenith.code

enum class CodeTokenKind {
    Keyword,
    StringLiteral,
    Comment,
    Number,
    Constant,
    Function,
    Type,
    Property,
    Tag,
    Meta,
    Inserted,
    Deleted,
}

data class CodeToken(val start: Int, val end: Int, val kind: CodeTokenKind)

data class CodeHighlight(val label: String?, val text: String, val tokens: List<CodeToken>) {
    val isColored: Boolean get() = tokens.isNotEmpty()
}

fun expandTabs(code: String, width: Int = TAB_WIDTH): String {
    if ('\t' !in code) return code
    val out = StringBuilder(code.length + code.count { it == '\t' } * (width - 1))
    var column = 0
    for (char in code) {
        when (char) {
            '\t' -> {
                val spaces = width - column % width
                repeat(spaces) { out.append(' ') }
                column += spaces
            }
            '\n', '\r' -> {
                out.append(char)
                column = 0
            }
            else -> {
                out.append(char)
                column++
            }
        }
    }
    return out.toString()
}

private const val TAB_WIDTH = 4

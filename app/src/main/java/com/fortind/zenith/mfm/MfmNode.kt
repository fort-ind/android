package com.fortind.zenith.mfm

sealed interface MfmNode

sealed interface MfmContainer : MfmNode {
    val children: List<MfmNode>
}

data class MfmText(val text: String) : MfmNode

data class MfmBold(override val children: List<MfmNode>) : MfmContainer

data class MfmItalic(override val children: List<MfmNode>) : MfmContainer

data class MfmStrike(override val children: List<MfmNode>) : MfmContainer

data class MfmSmall(override val children: List<MfmNode>) : MfmContainer

data class MfmCenter(override val children: List<MfmNode>) : MfmContainer

data class MfmQuote(override val children: List<MfmNode>) : MfmContainer

data class MfmFn(val name: String, val args: Map<String, String?>, override val children: List<MfmNode>) : MfmContainer

data class MfmLink(val silent: Boolean, val url: String, override val children: List<MfmNode>) : MfmContainer

data class MfmInlineCode(val code: String) : MfmNode

data class MfmBlockCode(val code: String, val lang: String?) : MfmNode

data class MfmMathInline(val formula: String) : MfmNode

data class MfmMathBlock(val formula: String) : MfmNode

data class MfmSearch(val query: String, val content: String) : MfmNode

data class MfmEmojiCode(val name: String) : MfmNode

data class MfmMention(val username: String, val host: String?) : MfmNode {
    val acct: String get() = if (host == null) "@$username" else "@$username@$host"
}

data class MfmHashtag(val tag: String) : MfmNode

data class MfmUrl(val url: String, val brackets: Boolean) : MfmNode

val MfmNode.isBlock: Boolean
    get() = this is MfmQuote || this is MfmCenter || this is MfmBlockCode || this is MfmMathBlock || this is MfmSearch

fun List<MfmNode>.toPlainText(): String = buildString { appendPlain(this@toPlainText) }

private fun StringBuilder.appendPlain(nodes: List<MfmNode>) {
    nodes.forEach { node ->
        when (node) {
            is MfmText -> append(node.text)
            is MfmContainer -> appendPlain(node.children)
            is MfmInlineCode -> append(node.code)
            is MfmBlockCode -> append(node.code)
            is MfmMathInline -> append(node.formula)
            is MfmMathBlock -> append(node.formula)
            is MfmSearch -> append(node.content)
            is MfmEmojiCode -> append(':').append(node.name).append(':')
            is MfmMention -> append(node.acct)
            is MfmHashtag -> append('#').append(node.tag)
            is MfmUrl -> append(node.url)
        }
        if (node.isBlock) append('\n')
    }
}

fun List<MfmNode>.emojiNames(): Set<String> = buildSet { collectEmoji(this@emojiNames) }

private fun MutableSet<String>.collectEmoji(nodes: List<MfmNode>) {
    nodes.forEach { node ->
        when (node) {
            is MfmEmojiCode -> add(node.name)
            is MfmContainer -> collectEmoji(node.children)
            else -> Unit
        }
    }
}

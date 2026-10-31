package com.fortind.zenith.mfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MfmParserTest {

    private fun parse(text: String) = MfmParser.parse(text)

    private fun text(value: String) = MfmText(value)

    @Test
    fun plainTextStaysText() {
        assertEquals(listOf(text("hello world")), parse("hello world"))
    }

    @Test
    fun boldVariants() {
        assertEquals(listOf(MfmBold(listOf(text("a")))), parse("**a**"))
        assertEquals(listOf(MfmBold(listOf(text("a b")))), parse("__a b__"))
        assertEquals(listOf(MfmBold(listOf(text("a")))), parse("<b>a</b>"))
    }

    @Test
    fun italicVariantsRespectWordBoundaries() {
        assertEquals(listOf(MfmItalic(listOf(text("a")))), parse("*a*"))
        assertEquals(listOf(MfmItalic(listOf(text("a")))), parse("<i>a</i>"))
        assertEquals(listOf(text("snake_case_name")), parse("snake_case_name"))
        assertEquals(listOf(text("2*3*4")), parse("2*3*4"))
    }

    @Test
    fun strikeSmallAndBig() {
        assertEquals(listOf(MfmStrike(listOf(text("gone")))), parse("~~gone~~"))
        assertEquals(listOf(MfmStrike(listOf(text("gone")))), parse("<s>gone</s>"))
        assertEquals(listOf(MfmSmall(listOf(text("tiny")))), parse("<small>tiny</small>"))
        assertEquals(listOf(MfmFn("tada", emptyMap(), listOf(text("big")))), parse("***big***"))
        assertEquals(listOf(text("~~a\nb~~")), parse("~~a\nb~~"))
    }

    @Test
    fun nestedInlineFormatting() {
        assertEquals(
            listOf(MfmBold(listOf(text("a "), MfmItalic(listOf(text("b"))), text(" c")))),
            parse("**a <i>b</i> c**"),
        )
    }

    @Test
    fun unclosedMarkersFallBackToText() {
        assertEquals(listOf(text("**open")), parse("**open"))
        assertEquals(listOf(text("<b>open")), parse("<b>open"))
        assertEquals(listOf(text("\$[x open")), parse("\$[x open"))
        assertEquals(listOf(text("[label](not a url)")), parse("[label](not a url)"))
    }

    @Test
    fun customEmojiNeedsColonsAndNoAdjacentLetters() {
        assertEquals(listOf(text("hi "), MfmEmojiCode("blobcat"), text("!")), parse("hi :blobcat:!"))
        assertEquals(listOf(text("12:30:45")), parse("12:30:45"))
        assertEquals(listOf(text("a:b:c")), parse("a:b:c"))
    }

    @Test
    fun mentionsWithAndWithoutHosts() {
        assertEquals(listOf(MfmMention("alice", null), text(" hi")), parse("@alice hi"))
        assertEquals(listOf(MfmMention("bob", "example.social"), text(".")), parse("@bob@example.social."))
        assertEquals(listOf(text("mail@example.com")), parse("mail@example.com"))
    }

    @Test
    fun hashtagsStopAtPunctuation() {
        assertEquals(listOf(MfmHashtag("misskey"), text(", yes")), parse("#misskey, yes"))
        assertEquals(listOf(text("#123")), parse("#123"))
        assertEquals(listOf(text("a#b")), parse("a#b"))
    }

    @Test
    fun urlsTrimTrailingPunctuationAndKeepBalancedParens() {
        assertEquals(listOf(MfmUrl("https://example.com/a", false), text(".")), parse("https://example.com/a."))
        assertEquals(
            listOf(text("("), MfmUrl("https://en.wikipedia.org/wiki/Foo_(bar)", false), text(")")),
            parse("(https://en.wikipedia.org/wiki/Foo_(bar))"),
        )
        assertEquals(listOf(MfmUrl("https://example.com", true)), parse("<https://example.com>"))
    }

    @Test
    fun linksAndSilentLinks() {
        assertEquals(listOf(MfmLink(false, "https://x.y", listOf(text("site")))), parse("[site](https://x.y)"))
        assertEquals(listOf(MfmLink(true, "https://x.y", listOf(MfmBold(listOf(text("b")))))), parse("?[**b**](https://x.y)"))
        assertEquals(
            listOf(MfmLink(false, "https://x.y", listOf(text("@alice")))),
            parse("[@alice](https://x.y)"),
        )
    }

    @Test
    fun functionsParseNameArgsAndContent() {
        assertEquals(
            listOf(MfmFn("spin", mapOf("x" to null, "speed" to "2s"), listOf(text("hi")))),
            parse("\$[spin.x,speed=2s hi]"),
        )
        assertEquals(
            listOf(MfmFn("fg", mapOf("color" to "f00"), listOf(MfmFn("x2", emptyMap(), listOf(MfmEmojiCode("a")))))),
            parse("\$[fg.color=f00 \$[x2 :a:]]"),
        )
    }

    @Test
    fun inlineCodeAndMath() {
        assertEquals(listOf(text("run "), MfmInlineCode("ls -la")), parse("run `ls -la`"))
        assertEquals(listOf(MfmMathInline("x^2")), parse("\\(x^2\\)"))
        assertEquals(listOf(MfmInlineCode("**not bold**")), parse("`**not bold**`"))
    }

    @Test
    fun plainTagDisablesSyntax() {
        assertEquals(listOf(text("**raw** :x:")), parse("<plain>**raw** :x:</plain>"))
    }

    @Test
    fun blocksOnlyStartAtLineBeginnings() {
        assertEquals(
            listOf(text("before"), MfmQuote(listOf(text("quoted\nlines"))), text("after")),
            parse("before\n> quoted\n> lines\nafter"),
        )
        assertEquals(listOf(text("a > b")), parse("a > b"))
    }

    @Test
    fun codeBlocksKeepTheirContentRaw() {
        assertEquals(
            listOf(MfmBlockCode("val x = **1**\nprintln(x)", "kotlin"), text("done")),
            parse("```kotlin\nval x = **1**\nprintln(x)\n```\ndone"),
        )
        assertEquals(listOf(text("```nope")), parse("```nope"))
    }

    @Test
    fun centerMathBlockAndSearch() {
        assertEquals(listOf(MfmCenter(listOf(MfmBold(listOf(text("c")))))), parse("<center>**c**</center>"))
        assertEquals(listOf(MfmMathBlock("a+b")), parse("\\[a+b\\]"))
        assertEquals(listOf(MfmSearch("misskey app", "misskey app Search")), parse("misskey app Search"))
        assertEquals(listOf(MfmSearch("猫", "猫 検索")), parse("猫 検索"))
    }

    @Test
    fun nestLimitTurnsDeepFormattingIntoText() {
        val nodes = MfmParser.parse("**<i>~~deep~~</i>**", nestLimit = 3)
        val bold = nodes.single() as MfmBold
        val italic = bold.children.single() as MfmItalic
        assertEquals(listOf(text("~~deep~~")), italic.children)
    }

    @Test
    fun pathologicalInputStaysFast() {
        val inputs = listOf("**".repeat(5_000), "\$[x ".repeat(3_000) + "]", "<b>".repeat(5_000), "*a ".repeat(5_000))
        inputs.forEach { input ->
            val started = System.nanoTime()
            MfmParser.parse(input)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("took $elapsedMs ms for ${input.take(10)}", elapsedMs < 2_000)
        }
    }

    @Test
    fun simpleParserOnlyKnowsEmojiAndPlain() {
        assertEquals(
            listOf(text("**cat** "), MfmEmojiCode("neko"), text(" @a")),
            MfmParser.parseSimple("**cat** :neko: @a"),
        )
    }

    @Test
    fun plainTextAndEmojiNamesAreExtracted() {
        val nodes = parse("**hi** :wave: \$[x2 :big:] @bob")
        assertEquals("hi :wave: :big: @bob", nodes.toPlainText())
        assertEquals(setOf("wave", "big"), nodes.emojiNames())
    }
}

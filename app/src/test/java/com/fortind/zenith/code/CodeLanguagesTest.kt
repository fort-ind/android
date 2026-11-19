package com.fortind.zenith.code

import io.noties.prism4j.DefaultGrammarLocator
import io.noties.prism4j.Prism4j
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeLanguagesTest {

    @Test
    fun everyMappedGrammarExistsInThePrismBundle() {
        val prism = Prism4j(DefaultGrammarLocator())

        CodeLanguages.grammars.forEach { name -> assertNotNull("no grammar named $name", prism.grammar(name)) }
    }

    @Test
    fun commonTagsAndTheirAliasesResolve() {
        assertEquals("javascript", CodeLanguages.resolve("JS", "").language?.grammar)
        assertEquals("kotlin", CodeLanguages.resolve("kt", "").language?.grammar)
        assertEquals("python", CodeLanguages.resolve(" py ", "").language?.grammar)
        assertEquals("bash", CodeLanguages.resolve("sh", "").language?.grammar)
        assertEquals("yaml", CodeLanguages.resolve("yml", "").language?.grammar)
        assertEquals("markup", CodeLanguages.resolve("html", "").language?.grammar)
        assertEquals("csharp", CodeLanguages.resolve("c#", "").language?.grammar)
        assertEquals("cpp", CodeLanguages.resolve("c++", "").language?.grammar)
    }

    @Test
    fun theLabelIsTheLanguagesNameNotTheTag() {
        assertEquals("TypeScript", CodeLanguages.resolve("ts", "").label)
        assertEquals("C#", CodeLanguages.resolve("cs", "").label)
        assertEquals("TOML", CodeLanguages.resolve("toml", "").label)
    }

    @Test
    fun anUnsupportedLanguageKeepsItsTagAsTheLabelAndGetsNoGrammar() {
        val resolved = CodeLanguages.resolve("rust", "fn main() {}")

        assertNull(resolved.language)
        assertEquals("rust", resolved.label)
    }

    @Test
    fun aTagThatDoesNotLookLikeALanguageIsNotShown() {
        val resolved = CodeLanguages.resolve("this is not a language tag at all", "x")

        assertNull(resolved.language)
        assertNull(resolved.label)
    }

    @Test
    fun anUntaggedBlockGuessesOnlyMarkupAndJson() {
        assertEquals("HTML", CodeLanguages.resolve(null, "<div class=\"a\">hi</div>").label)
        assertEquals("XML", CodeLanguages.resolve("", "<?xml version=\"1.0\"?><a/>").label)
        assertEquals("JSON", CodeLanguages.resolve(null, "{\"a\": [1, 2, true]}").label)
        assertEquals("JSON", CodeLanguages.resolve(null, "[1, 2, 3]").label)
    }

    @Test
    fun proseAndOtherCodeAreNotGuessed() {
        assertNull(CodeLanguages.resolve(null, "just some words").language)
        assertNull(CodeLanguages.resolve(null, "val x = 1").language)
        assertNull(CodeLanguages.resolve(null, "{not json at all").language)
        assertNull(CodeLanguages.resolve(null, "< 3 is a heart").language)
    }

    @Test
    fun languageTagsAreShortAndPlain() {
        assertTrue(CodeLanguages.isLanguageTag("c++"))
        assertTrue(CodeLanguages.isLanguageTag("objective-c"))
        assertFalse(CodeLanguages.isLanguageTag(""))
        assertFalse(CodeLanguages.isLanguageTag("a".repeat(21)))
        assertFalse(CodeLanguages.isLanguageTag("two words"))
    }
}

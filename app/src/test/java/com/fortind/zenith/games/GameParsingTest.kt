package com.fortind.zenith.games

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameParsingTest {

    @Test
    fun slugsBecomeTitles() {
        assertEquals("CSS Space Shooter", titleFromSlug("css-space-shooter"))
        assertEquals("Diep.io", titleFromSlug("diep-io"))
        assertEquals("Paper.io 2", titleFromSlug("paper-io-2"))
        assertEquals("Minecraft 1.8.8 Fixed", titleFromSlug("minecraft-1-8-8-fixed"))
        assertEquals("Final Fantasy II", titleFromSlug("final-fantasy-ii"))
    }

    @Test
    fun categoryComesFromThePath() {
        assertEquals(GameCategory.FLASH, gameFromUrl("https://www.fort1nd.com/games/flash/duck-life")?.category)
        assertEquals(
            GameCategory.RETRO,
            gameFromUrl("https://www.fort1nd.com/games/retroclassic-mostly-emulated/doom")?.category,
        )
        assertEquals(GameCategory.OTHER, gameFromUrl("https://www.fort1nd.com/games/doodles")?.category)
    }

    @Test
    fun nonGamePagesAreSkipped() {
        assertNull(gameFromUrl("https://www.fort1nd.com/apps/appstone/smash-karts"))
        assertNull(gameFromUrl("https://www.fort1nd.com/"))
        assertNull(gameFromUrl("not a url"))
    }

    @Test
    fun gamesSortAlphabeticallyIgnoringCaseThenByCategory() {
        val sorted = sortGames(
            listOf(
                Game("chess", GameCategory.FLASH, "a"),
                Game("Abandoned", GameCategory.FLASH, "b"),
                Game("Chess", GameCategory.CODEPEN, "c"),
                Game("2048", GameCategory.CODEPEN, "d"),
            )
        )

        assertEquals(listOf("d", "b", "a", "c"), sorted.map { it.url })
    }

    @Test
    fun sectionsGroupByFirstLetterWithDigitsUnderHash() {
        val sections = sectionGames(
            listOf(
                Game("007 Nightfire", GameCategory.RETRO, "1"),
                Game("Abandoned", GameCategory.FLASH, "2"),
                Game("Age of War", GameCategory.FLASH, "3"),
                Game("Éclair", GameCategory.HTML, "4"),
            )
        )

        assertEquals(listOf("#", "A", "E"), sections.map { it.letter })
        assertEquals(2, sections[1].games.size)
    }

    @Test
    fun searchRanksPrefixThenWordStartThenSubstring() {
        val games = sortGames(
            listOf(
                Game("Duck Life", GameCategory.FLASH, "duck"),
                Game("Ducklings", GameCategory.HTML, "ducklings"),
                Game("Learn to Fly", GameCategory.FLASH, "fly"),
                Game("Red Ball", GameCategory.FLASH, "ball"),
                Game("8 Ball Pool", GameCategory.FLASH, "pool"),
                Game("Pokéclassic", GameCategory.RETRO, "poke"),
            )
        )

        assertEquals(listOf("duck", "ducklings"), searchGames(games, "duck").map { it.url })
        assertEquals(listOf("pool", "ball"), searchGames(games, "ball").map { it.url })
        assertEquals(listOf("fly"), searchGames(games, "earn").map { it.url })
        assertEquals(listOf("poke"), searchGames(games, "POKE").map { it.url })
        assertTrue(searchGames(games, "  ").isEmpty())
    }

    @Test
    fun bundledSitemapHasOnlyGamesWithUniqueUrls() {
        val games = File("src/main/assets/sitemap.xml").inputStream().use(::parseSitemapGames)

        assertTrue(games.size > 200)
        assertTrue(games.all { "/games/" in it.url })
        assertEquals(games.size, games.map { it.url }.toSet().size)
    }
}

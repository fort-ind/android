package com.fortind.zenith.games

import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Declared in the order the filter chips show them; [OTHER] catches anything else under /games/. */
enum class GameCategory(val pathPrefix: String) {
    HTML("games/html/"),
    FLASH("games/flash/"),
    RETRO("games/retroclassic-mostly-emulated/"),
    MINECRAFT("games/minecraft/"),
    CODEPEN("games/codepen/"),
    OTHER("games/"),
}

data class Game(val title: String, val category: GameCategory, val url: String)

data class GameSection(val letter: String, val games: List<Game>)

fun parseSitemapGames(input: InputStream): List<Game> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
    val locs = document.getElementsByTagName("loc")
    val games = (0 until locs.length).mapNotNull { gameFromUrl(locs.item(it).textContent.trim()) }
    return sortGames(games)
}

fun gameFromUrl(url: String): Game? {
    val path = try {
        URI(url).path
    } catch (_: URISyntaxException) {
        null
    }?.trim('/') ?: return null
    if (!path.startsWith(GameCategory.OTHER.pathPrefix)) return null
    val category = GameCategory.entries.first { path.startsWith(it.pathPrefix) }
    return Game(title = titleFromSlug(path.substringAfterLast('/')), category = category, url = url)
}

private val upperCaseTokens = setOf(
    "cs", "css", "dbz", "ds", "fnaf", "gba", "gbc", "gta", "hd", "html", "ii", "iii", "mlb", "mlg", "mw",
    "n64", "nba", "nds", "nes", "nfl", "nfs", "nhl", "psp", "qwop", "snes", "tmnt", "tv", "ufc", "ufo", "wwe",
)

private val domainSuffixTokens = setOf("com", "gg", "io", "lol", "net", "org")

fun titleFromSlug(slug: String): String {
    val tokens = slug.split('-', '_').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return slug
    return buildString {
        tokens.forEachIndexed { i, token ->
            val previous = tokens.getOrNull(i - 1)
            when {
                i > 0 && token.lowercase() in domainSuffixTokens -> append('.').append(token.lowercase())
                previous != null && token.isAllDigits() && previous.isAllDigits() -> append('.').append(token)
                else -> {
                    if (i > 0) append(' ')
                    append(
                        if (token.lowercase() in upperCaseTokens) token.uppercase()
                        else token.lowercase().replaceFirstChar { it.uppercaseChar() }
                    )
                }
            }
        }
    }
}

private fun String.isAllDigits() = isNotEmpty() && all { it.isDigit() }

private val titleCollator: Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.SECONDARY }

fun sortGames(games: List<Game>): List<Game> =
    games.sortedWith { a, b ->
        titleCollator.compare(a.title, b.title).takeIf { it != 0 } ?: a.category.compareTo(b.category)
    }

/** Letters group under their unaccented capital (É under E); digits and symbols under "#". */
fun sectionLetter(title: String): String {
    val first = stripMarks(title.take(1)).firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercase() else "#"
}

fun sectionGames(games: List<Game>): List<GameSection> =
    games.groupBy { sectionLetter(it.title) }.map { (letter, items) -> GameSection(letter, items) }


fun searchGames(games: List<Game>, query: String): List<Game> {
    val needle = searchWords(query).joinToString("")
    if (needle.isEmpty()) return emptyList()
    return games
        .mapNotNull { game -> matchRank(searchWords(game.title), needle)?.let { it to game } }
        .sortedBy { it.first }
        .map { it.second }
}

private fun matchRank(words: List<String>, needle: String): Int? {
    val joined = words.joinToString("")
    if (joined.startsWith(needle)) return 0
    var offset = 0
    for (word in words) {
        if (joined.startsWith(needle, offset)) return 1
        offset += word.length
    }
    return if (needle in joined) 2 else null
}

private fun searchWords(text: String): List<String> =
    stripMarks(text).lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

private fun stripMarks(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

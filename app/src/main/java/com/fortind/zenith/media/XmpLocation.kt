package com.fortind.zenith.media

import java.io.InputStream

private val PLACE_FIELDS = setOf(
    "City",
    "State",
    "Country",
    "CountryCode",
    "CountryName",
    "Location",
    "LocationCreated",
    "LocationShown",
    "LocationName",
    "Sublocation",
    "ProvinceState",
    "WorldRegion",
)
private const val NAME = "[A-Za-z_][\\w.-]*"
private val ElementStart = Regex("<($NAME):($NAME)(?=[\\s/>])")
private val AttributeField = Regex("\\s+($NAME):($NAME)\\s*=\\s*(\"[^\"]*\"|'[^']*')")
private val Declaration = Regex("<!(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)
private const val REPLACEMENT_CHAR = '\uFFFD'
private const val BYTE_ORDER_MARK = 0xFEFF
private const val LAST_ASCII = 0x7F

private val PACKET_MARKERS = listOf("<x:xmpmeta" to "</x:xmpmeta>", "<rdf:RDF" to "</rdf:RDF>")
private val LONGEST_START = PACKET_MARKERS.maxOf { it.first.length }
private const val SCAN_CHUNK_BYTES = 64 * 1024
private const val MAX_PACKET_CHARS = 4 * 1024 * 1024

internal fun isLocationField(localName: String): Boolean =
    localName.startsWith("gps", ignoreCase = true) || localName in PLACE_FIELDS

fun xmpHasLocation(xmp: String): Boolean =
    ElementStart.findAll(xmp).any { isLocationField(it.groupValues[2]) } ||
        AttributeField.findAll(xmp).any { isLocationField(it.groupValues[2]) }

fun withoutXmpLocation(xmp: String): String? {
    if (xmp.contains(REPLACEMENT_CHAR) || Declaration.containsMatchIn(xmp)) return null
    var text = xmp
    while (true) {
        val start = ElementStart.findAll(text).firstOrNull { isLocationField(it.groupValues[2]) } ?: break
        val end = elementEnd(text, start) ?: return null
        text = text.removeRange(start.range.first, end)
    }
    text = AttributeField.replace(text) { if (isLocationField(it.groupValues[2])) "" else it.value }
    if (xmpHasLocation(text)) return null
    return asciiSafe(text)
}

private fun elementEnd(text: String, start: MatchResult): Int? {
    val tagEnd = startTagEnd(text, start.range.last + 1) ?: return null
    if (text[tagEnd - 1] == '/') return tagEnd + 1
    val close = Regex("</" + Regex.escape(start.value.substring(1)) + "\\s*>").find(text, tagEnd + 1) ?: return null
    return close.range.last + 1
}

private fun startTagEnd(text: String, from: Int): Int? {
    var quote: Char? = null
    for (index in from until text.length) {
        val char = text[index]
        when {
            quote != null -> if (char == quote) quote = null
            char == '"' || char == '\'' -> quote = char
            char == '>' -> return index
        }
    }
    return null
}

private enum class Section { Text, Tag, Instruction, Comment, CharacterData }

private fun asciiSafe(text: String): String? {
    val out = StringBuilder(text.length)
    var section = Section.Text
    var quote: Char? = null
    var index = 0
    while (index < text.length) {
        val point = text.codePointAt(index)
        val width = Character.charCount(point)
        val ascii = point <= LAST_ASCII
        when (section) {
            Section.Text -> when {
                text.startsWith("<!--", index) -> section = Section.Comment
                text.startsWith("<![CDATA[", index) -> section = Section.CharacterData
                text.startsWith("<?", index) -> section = Section.Instruction
                point == '<'.code -> section = Section.Tag
            }
            Section.Tag -> when {
                quote != null -> if (point == quote.code) quote = null
                point == '"'.code || point == '\''.code -> quote = point.toChar()
                point == '>'.code -> section = Section.Text
                !ascii -> return null
            }
            Section.Instruction -> when {
                text.startsWith("?>", index) -> {
                    out.append("?>")
                    index += 2
                    section = Section.Text
                    continue
                }
                point == BYTE_ORDER_MARK -> {
                    index += width
                    continue
                }
                !ascii -> return null
            }
            Section.Comment -> if (text.startsWith("-->", index)) {
                out.append("-->")
                index += 3
                section = Section.Text
                continue
            }
            Section.CharacterData -> when {
                text.startsWith("]]>", index) -> {
                    out.append("]]>")
                    index += 3
                    section = Section.Text
                    continue
                }
                !ascii -> return null
            }
        }
        when {
            ascii -> out.appendCodePoint(point)
            section == Section.Comment -> out.append('?')
            else -> out.append("&#x").append(Integer.toHexString(point).uppercase()).append(';')
        }
        index += width
    }
    return out.toString()
}

fun streamHasXmpLocation(
    input: InputStream,
    chunkBytes: Int = SCAN_CHUNK_BYTES,
    maxPacketChars: Int = MAX_PACKET_CHARS,
): Boolean {
    val buffer = ByteArray(chunkBytes)
    var carried = ""
    var packet: StringBuilder? = null
    var endMarker = ""
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        var text = carried + String(buffer, 0, read, Charsets.ISO_8859_1)
        carried = ""
        while (text.isNotEmpty()) {
            val open = packet
            if (open == null) {
                val start = PACKET_MARKERS
                    .mapNotNull { (begin, end) -> text.indexOf(begin).takeIf { it >= 0 }?.let { it to end } }
                    .minByOrNull { it.first }
                if (start == null) {
                    carried = text.takeLast(LONGEST_START - 1)
                    text = ""
                } else {
                    packet = StringBuilder()
                    endMarker = start.second
                    text = text.substring(start.first)
                }
            } else {
                val searchFrom = maxOf(0, open.length - endMarker.length + 1)
                open.append(text)
                val end = open.indexOf(endMarker, searchFrom)
                if (end < 0) {
                    if (open.length > maxPacketChars) return true
                    text = ""
                } else {
                    val packetEnd = end + endMarker.length
                    if (xmpHasLocation(open.substring(0, packetEnd))) return true
                    text = open.substring(packetEnd)
                    packet = null
                }
            }
        }
    }
    return packet?.let { xmpHasLocation(it.toString()) } ?: false
}

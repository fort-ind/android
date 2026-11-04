package com.fortind.zenith.media

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

private const val HEADER_BYTES = 8L
private const val LARGE_HEADER_BYTES = 16L
private const val LARGE_SIZE_MARKER = 1L
private const val MAX_LOCATION_PAYLOAD = 64L * 1024
private val CONTAINERS = setOf("moov", "trak", "udta")
private val LOCATION_BOXES = setOf("©xyz", "loci")
private const val FREE = "free"

internal fun neutralizeMp4Location(file: File): Boolean = RandomAccessFile(file, "rw").use { source ->
    var neutralized = false
    walk(source, 0L, source.length()) { neutralized = true }
    neutralized
}

private fun walk(source: RandomAccessFile, start: Long, end: Long, onNeutralized: () -> Unit) {
    var position = start
    while (position + HEADER_BYTES <= end) {
        source.seek(position)
        val declared = source.readInt().toLong() and 0xFFFFFFFFL
        val type = ByteArray(4).also(source::readFully).toString(Charsets.ISO_8859_1)
        val header = if (declared == LARGE_SIZE_MARKER) LARGE_HEADER_BYTES else HEADER_BYTES
        val size = when {
            declared == LARGE_SIZE_MARKER -> source.readLong()
            declared == 0L -> end - position
            else -> declared
        }
        if (size < header || position + size > end) return
        val payloadStart = position + header
        val boxEnd = position + size
        when {
            type in CONTAINERS -> walk(source, payloadStart, boxEnd, onNeutralized)
            type in LOCATION_BOXES -> {
                if (boxEnd - payloadStart > MAX_LOCATION_PAYLOAD) throw IOException("Location box too large")
                source.seek(position + 4)
                source.write(FREE.toByteArray(Charsets.ISO_8859_1))
                source.write(ByteArray((boxEnd - payloadStart).toInt()))
                onNeutralized()
            }
        }
        position = boxEnd
    }
}

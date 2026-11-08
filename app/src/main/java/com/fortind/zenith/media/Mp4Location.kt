package com.fortind.zenith.media

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

private const val HEADER_BYTES = 8L
private const val LARGE_HEADER_BYTES = 16L
private const val LARGE_SIZE_MARKER = 1L
private const val FULL_BOX_HEADER = 4L
private const val UUID_BYTES = 16
private const val MAX_LOCATION_PAYLOAD = 64L * 1024
private const val MAX_KEYS_PAYLOAD = 1024L * 1024
private const val MAX_XMP_PAYLOAD = 4L * 1024 * 1024
private const val ZERO_CHUNK = 64 * 1024
private val CONTAINERS = setOf("moov", "trak", "udta")
private val LOCATION_BOXES = setOf("©xyz", "loci")
private const val META = "meta"
private const val KEYS = "keys"
private const val ITEM_LIST = "ilst"
private const val HANDLER = "hdlr"
private const val XMP_BOX = "XMP_"
private const val UUID_BOX = "uuid"
private const val FREE = "free"
private const val LOCATION_KEY = "location"
private val XMP_UUID = byteArrayOf(
    0xBE.toByte(), 0x7A, 0xCF.toByte(), 0xCB.toByte(), 0x97.toByte(), 0xA9.toByte(), 0x42, 0xE8.toByte(),
    0x9C.toByte(), 0x71, 0x99.toByte(), 0x94.toByte(), 0x91.toByte(), 0xE3.toByte(), 0xAF.toByte(), 0xAC.toByte(),
)

internal fun neutralizeMp4Location(file: File): Boolean = RandomAccessFile(file, "rw").use { source ->
    val boxes = LocationFinder(FileBoxInput(source)).find(source.length())
    boxes.forEach { box -> box.neutralize(source) }
    boxes.isNotEmpty()
}

internal fun streamHasMp4Location(input: InputStream): Boolean =
    LocationFinder(StreamBoxInput(input)).find(Long.MAX_VALUE).isNotEmpty()

private class LocationBox(val start: Long, val payloadStart: Long, val end: Long) {
    fun neutralize(file: RandomAccessFile) {
        file.seek(start + 4)
        file.write(FREE.toByteArray(Charsets.ISO_8859_1))
        file.seek(payloadStart)
        val zeros = ByteArray(ZERO_CHUNK)
        var remaining = end - payloadStart
        while (remaining > 0) {
            val count = minOf(remaining, ZERO_CHUNK.toLong()).toInt()
            file.write(zeros, 0, count)
            remaining -= count
        }
    }
}

private class BoxHeader(val type: String, val start: Long, val payloadStart: Long, val end: Long) {
    val payloadSize: Long get() = end - payloadStart
}

private class ListedItem(val keyIndex: Int, val box: BoxHeader)

private class LocationFinder(private val input: BoxInput) {
    private val found = mutableListOf<LocationBox>()

    fun find(end: Long): List<LocationBox> {
        try {
            walk(0L, end)
        } catch (_: EOFException) {
            return found
        }
        return found
    }

    private fun walk(start: Long, end: Long) {
        forEachBox(start, end) { box ->
            when (box.type) {
                in CONTAINERS -> walk(box.payloadStart, box.end)
                META -> walkMeta(box)
                in LOCATION_BOXES -> {
                    if (box.payloadSize > MAX_LOCATION_PAYLOAD) throw IOException("Location box too large")
                    found += box.toLocation()
                }
                XMP_BOX -> if (xmpLeaksLocation(box, box.payloadStart)) found += box.toLocation()
                UUID_BOX -> if (box.payloadSize >= UUID_BYTES && input.readBytes(UUID_BYTES).contentEquals(XMP_UUID)) {
                    if (xmpLeaksLocation(box, box.payloadStart + UUID_BYTES)) found += box.toLocation()
                }
            }
        }
    }

    private fun walkMeta(meta: BoxHeader) {
        if (meta.payloadSize < HEADER_BYTES) return
        val head = input.peek(HEADER_BYTES.toInt())
        val quickTimeStyle = head.copyOfRange(4, 8).toString(Charsets.ISO_8859_1) == HANDLER
        val childrenStart = if (quickTimeStyle) meta.payloadStart else meta.payloadStart + FULL_BOX_HEADER
        val keys = mutableMapOf<Int, String>()
        val items = mutableListOf<ListedItem>()
        forEachBox(childrenStart, meta.end) { box ->
            when (box.type) {
                KEYS -> keys += readKeys(box)
                ITEM_LIST -> forEachBox(box.payloadStart, box.end) { item ->
                    if (item.type in LOCATION_BOXES) found += item.toLocation() else items += ListedItem(item.typeAsIndex(), item)
                }
            }
        }
        items.filter { keys[it.keyIndex]?.contains(LOCATION_KEY, ignoreCase = true) == true }.forEach { found += it.box.toLocation() }
    }

    private fun readKeys(box: BoxHeader): Map<Int, String> {
        if (box.payloadSize > MAX_KEYS_PAYLOAD) throw IOException("Metadata keys too large")
        val payload = input.readBytes(box.payloadSize.toInt())
        if (payload.size < 8) return emptyMap()
        val count = payload.intAt(4)
        val keys = mutableMapOf<Int, String>()
        var offset = 8
        for (index in 1..count) {
            if (offset + 8 > payload.size) break
            val size = payload.intAt(offset)
            if (size < 8 || offset + size > payload.size) break
            keys[index] = payload.copyOfRange(offset + 8, offset + size).toString(Charsets.UTF_8)
            offset += size
        }
        return keys
    }

    private fun xmpLeaksLocation(box: BoxHeader, textStart: Long): Boolean {
        val length = box.end - textStart
        if (length > MAX_XMP_PAYLOAD) return true
        input.skipTo(textStart)
        return xmpHasLocation(input.readBytes(length.toInt()).toString(Charsets.UTF_8))
    }

    private fun forEachBox(start: Long, end: Long, onBox: (BoxHeader) -> Unit) {
        var position = start
        while (position + HEADER_BYTES <= end) {
            input.skipTo(position)
            val declared = input.readInt().toLong() and 0xFFFFFFFFL
            val type = input.readBytes(4).toString(Charsets.ISO_8859_1)
            val header = if (declared == LARGE_SIZE_MARKER) LARGE_HEADER_BYTES else HEADER_BYTES
            val size = when {
                declared == LARGE_SIZE_MARKER -> input.readLong()
                declared == 0L -> end - position
                else -> declared
            }
            if (size < header || size > end - position) return
            val box = BoxHeader(type, position, position + header, position + size)
            onBox(box)
            position = box.end
        }
    }

    private fun BoxHeader.toLocation() = LocationBox(start, payloadStart, end)

    private fun BoxHeader.typeAsIndex(): Int = type.toByteArray(Charsets.ISO_8859_1).intAt(0)

    private fun ByteArray.intAt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or (this[offset + 3].toInt() and 0xFF)
}

private interface BoxInput {
    fun skipTo(position: Long)
    fun readInt(): Int
    fun readLong(): Long
    fun readBytes(count: Int): ByteArray
    fun peek(count: Int): ByteArray
}

private class FileBoxInput(private val file: RandomAccessFile) : BoxInput {
    override fun skipTo(position: Long) = file.seek(position)
    override fun readInt(): Int = file.readInt()
    override fun readLong(): Long = file.readLong()
    override fun readBytes(count: Int): ByteArray = ByteArray(count).also(file::readFully)
    override fun peek(count: Int): ByteArray {
        val position = file.filePointer
        return readBytes(count).also { file.seek(position) }
    }
}

private class StreamBoxInput(stream: InputStream) : BoxInput {
    private val buffered = BufferedInputStream(stream)
    private val data = DataInputStream(buffered)
    private var position = 0L

    override fun skipTo(position: Long) {
        if (position < this.position) throw IOException("Boxes out of order")
        var remaining = position - this.position
        while (remaining > 0) {
            val skipped = data.skip(remaining)
            if (skipped <= 0) {
                if (data.read() < 0) throw EOFException()
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
        this.position = position
    }

    override fun readInt(): Int = data.readInt().also { position += 4 }
    override fun readLong(): Long = data.readLong().also { position += 8 }
    override fun readBytes(count: Int): ByteArray = ByteArray(count).also { data.readFully(it); position += count }

    override fun peek(count: Int): ByteArray {
        buffered.mark(count)
        val bytes = ByteArray(count)
        data.readFully(bytes)
        buffered.reset()
        return bytes
    }
}

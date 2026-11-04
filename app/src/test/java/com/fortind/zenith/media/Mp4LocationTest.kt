package com.fortind.zenith.media

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Mp4LocationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun box(type: String, payload: ByteArray = ByteArray(0)): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(8 + payload.size)
            it.write(type.toByteArray(Charsets.ISO_8859_1))
            it.write(payload)
        }
        return bytes.toByteArray()
    }

    private fun write(vararg boxes: ByteArray): File = folder.newFile().also { file ->
        file.outputStream().use { out -> boxes.forEach(out::write) }
    }

    private val gps = "+37.7749-122.4194/".toByteArray()

    @Test
    fun locationAtomInsideUdtaBecomesAnEmptyFreeBoxOfTheSameSize() {
        val udta = box("udta", box("\u00a9xyz", gps) + box("name", "keep".toByteArray()))
        val before = box("ftyp", "isom".toByteArray()) + box("moov", box("mvhd", ByteArray(4)) + udta) + box("mdat", ByteArray(16) { 7 })
        val file = write(before)

        assertTrue(neutralizeMp4Location(file))

        val after = file.readBytes()
        assertEquals(before.size, after.size)
        assertFalse(String(after, Charsets.ISO_8859_1).contains("+37.7749"))
        assertTrue(String(after, Charsets.ISO_8859_1).contains("free"))
        assertTrue(String(after, Charsets.ISO_8859_1).contains("keep"))
        assertArrayEquals(ByteArray(16) { 7 }, after.copyOfRange(after.size - 16, after.size))
    }

    @Test
    fun locationInsideATrackIsFoundToo() {
        val file = write(box("moov", box("trak", box("udta", box("loci", gps)))))

        assertTrue(neutralizeMp4Location(file))
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("37.7749"))
    }

    @Test
    fun aFileWithoutLocationIsLeftAlone() {
        val before = box("ftyp", "isom".toByteArray()) + box("moov", box("udta", box("name", "x".toByteArray()))) + box("mdat", ByteArray(4))
        val file = write(before)

        assertFalse(neutralizeMp4Location(file))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun aTruncatedBoxStopsTheWalkInsteadOfReadingPastTheEnd() {
        val broken = box("moov", box("udta", box("\u00a9xyz", gps))).dropLast(5).toByteArray()
        val file = write(broken)

        assertFalse(neutralizeMp4Location(file))
    }
}

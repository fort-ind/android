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
        assertTrue("a cut-off location box still holds coordinates, so the upload must stop", streamHasMp4Location(broken.inputStream()))
    }

    private fun int(value: Int): ByteArray = byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    private fun key(name: String): ByteArray = int(8 + name.length) + "mdta".toByteArray() + name.toByteArray()

    private fun item(index: Int, value: String): ByteArray {
        val data = box("data", int(1) + int(0) + value.toByteArray())
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(8 + data.size)
            it.writeInt(index)
            it.write(data)
        }
        return bytes.toByteArray()
    }

    private fun appleMeta(): ByteArray {
        val keys = box("keys", int(0) + int(2) + key("com.apple.quicktime.make") + key("com.apple.quicktime.location.ISO6709"))
        val ilst = box("ilst", item(1, "Apple") + item(2, "+37.7749-122.4194+012.000/"))
        return box("meta", box("hdlr", ByteArray(25)) + keys + ilst)
    }

    @Test
    fun appleLocationKeysAreNeutralizedAndOtherKeysKept() {
        val before = box("ftyp", "qt  ".toByteArray()) + box("moov", box("mvhd", ByteArray(4)) + appleMeta()) + box("mdat", ByteArray(8) { 3 })
        val file = write(before)
        assertTrue(streamHasMp4Location(before.inputStream()))

        assertTrue(neutralizeMp4Location(file))

        val after = file.readBytes()
        assertEquals(before.size, after.size)
        val text = String(after, Charsets.ISO_8859_1)
        assertFalse(text.contains("+37.7749"))
        assertTrue(text.contains("Apple"))
        assertFalse(streamHasMp4Location(after.inputStream()))
    }

    @Test
    fun anIsoMetaBoxWithVersionFlagsIsWalkedToo() {
        val keys = box("keys", int(0) + int(1) + key("com.apple.quicktime.location.ISO6709"))
        val meta = box("meta", int(0) + box("hdlr", ByteArray(25)) + keys + box("ilst", item(1, "+1.0+2.0/")))
        val file = write(box("moov", box("udta", meta)))

        assertTrue(neutralizeMp4Location(file))
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("+1.0+2.0"))
    }

    @Test
    fun itemsListedBeforeTheirKeysAreStillMatched() {
        val keys = box("keys", int(0) + int(1) + key("com.apple.quicktime.location.ISO6709"))
        val meta = box("meta", box("hdlr", ByteArray(25)) + box("ilst", item(1, "+5.5+6.6/")) + keys)

        assertTrue(streamHasMp4Location(box("moov", meta).inputStream()))
    }

    @Test
    fun xmpWithGpsInsideUdtaIsNeutralizedButPlainXmpIsKept() {
        val withGps = box("moov", box("udta", box("XMP_", """<x:xmpmeta><rdf:Description exif:GPSLatitude="1"/></x:xmpmeta>""".toByteArray())))
        val plain = box("moov", box("udta", box("XMP_", """<x:xmpmeta><rdf:Description xmp:CreatorTool="Premiere"/></x:xmpmeta>""".toByteArray())))

        assertTrue(streamHasMp4Location(withGps.inputStream()))
        assertFalse(streamHasMp4Location(plain.inputStream()))
        val file = write(withGps)
        assertTrue(neutralizeMp4Location(file))
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("GPSLatitude"))
    }

    @Test
    fun xmpInAnAdobeUuidBoxIsChecked() {
        val uuid = byteArrayOf(
            0xBE.toByte(), 0x7A, 0xCF.toByte(), 0xCB.toByte(), 0x97.toByte(), 0xA9.toByte(), 0x42, 0xE8.toByte(),
            0x9C.toByte(), 0x71, 0x99.toByte(), 0x94.toByte(), 0x91.toByte(), 0xE3.toByte(), 0xAF.toByte(), 0xAC.toByte(),
        )
        val bytes = box("ftyp", "isom".toByteArray()) + box("uuid", uuid + """<x:xmpmeta p:City="Oslo"/>""".toByteArray())

        assertTrue(streamHasMp4Location(bytes.inputStream()))
        assertFalse(streamHasMp4Location(box("uuid", ByteArray(16) + """<x:xmpmeta p:City="Oslo"/>""".toByteArray()).inputStream()))
    }

    @Test
    fun theStreamCheckAgreesWithTheFileWalkOnAndroidLayouts() {
        val udta = box("moov", box("mvhd", ByteArray(4)) + box("udta", box("\u00a9xyz", gps))) + box("mdat", ByteArray(4096))

        assertTrue(streamHasMp4Location(udta.inputStream()))
        assertTrue(streamHasMp4Location((box("mdat", ByteArray(4096)) + box("moov", box("trak", box("udta", box("loci", gps))))).inputStream()))
        assertFalse(streamHasMp4Location((box("ftyp", "isom".toByteArray()) + box("mdat", ByteArray(64))).inputStream()))
    }

    @Test
    fun dataThatIsNotMp4IsNotFlagged() {
        assertFalse(streamHasMp4Location(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 1, 2, 3, 4, 5, 6, 7, 8).inputStream()))
        assertFalse(streamHasMp4Location(ByteArray(0).inputStream()))
    }
}

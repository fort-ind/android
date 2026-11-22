package com.fortind.zenith.media

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSnifferTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun iso(brand: String): ByteArray = bytes(0, 0, 0, 0x18) + "ftyp$brand".toByteArray(Charsets.ISO_8859_1)

    @Test
    fun commonImageSignaturesAreRecognised() {
        assertEquals("image/jpeg", sniffMediaType(bytes(0xFF, 0xD8, 0xFF, 0xE1)))
        assertEquals("image/png", sniffMediaType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals("image/gif", sniffMediaType("GIF89a...".toByteArray()))
        assertEquals("image/webp", sniffMediaType("RIFF\u0000\u0000\u0000\u0000WEBPVP8X".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("image/tiff", sniffMediaType("II*\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("image/bmp", sniffMediaType("BM\u0000\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("image/x-icon", sniffMediaType(bytes(0, 0, 1, 0, 1, 0)))
        assertEquals("image/jxl", sniffMediaType(bytes(0xFF, 0x0A, 0)))
    }

    @Test
    fun isoBrandsPickImageVideoOrAudio() {
        assertEquals("image/heic", sniffMediaType(iso("heic")))
        assertEquals("image/heif", sniffMediaType(iso("mif1")))
        assertEquals("image/avif", sniffMediaType(iso("avif")))
        assertEquals("video/quicktime", sniffMediaType(iso("qt  ")))
        assertEquals("video/mp4", sniffMediaType(iso("isom")))
        assertEquals("audio/mp4", sniffMediaType(iso("M4A ")))
        assertEquals("video/webm", sniffMediaType(bytes(0x1A, 0x45, 0xDF, 0xA3)))
    }

    @Test
    fun unknownAndTruncatedHeadersAreNotGuessed() {
        assertNull(sniffMediaType(ByteArray(0)))
        assertNull(sniffMediaType("<svg xmlns".toByteArray()))
        assertNull(sniffMediaType(bytes(0, 0, 0, 0x18, 0x66, 0x74)))
    }

    @Test
    fun aMislabelledFileIsCheckedAsWhatItReallyIs() {
        assertEquals("image/jpeg", sanitizerType("image/bmp", "image/jpeg"))
        assertEquals("image/jpeg", sanitizerType("image/gif", "image/jpeg"))
        assertEquals("image/jpeg", sanitizerType("application/octet-stream", "image/jpeg"))
        assertEquals("video/mp4", sanitizerType("image/gif", "video/mp4"))
    }

    @Test
    fun aMatchingOrUnknownSniffKeepsTheDeclaredType() {
        assertEquals("image/jpeg", sanitizerType("image/jpeg", null))
        assertEquals("video/mp4", sanitizerType("video/mp4", "video/quicktime"))
        assertEquals("image/png", sanitizerType("image/png", "image/jpeg"))
        assertEquals("image/bmp", sanitizerType("image/bmp", "image/bmp"))
    }

    @Test
    fun readHeaderStopsAtTheLimitOrTheEnd() {
        assertArrayEquals(bytes(1, 2), ByteArrayInputStream(bytes(1, 2)).readHeader(4))
        assertArrayEquals(bytes(1, 2, 3), ByteArrayInputStream(bytes(1, 2, 3, 4, 5)).readHeader(3))
    }
}

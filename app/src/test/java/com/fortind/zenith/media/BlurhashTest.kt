package com.fortind.zenith.media

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurhashTest {

    private val hash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"

    @Test
    fun decodesToTheRequestedSizeWithOpaquePixels() {
        val pixels = Blurhash.decode(hash, 32, 24)

        assertNotNull(pixels)
        assertEquals(32 * 24, pixels!!.size)
        assertTrue(pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun averageColourIsCloseToTheDecodedMean() {
        val pixels = Blurhash.decode(hash, 32, 32)!!
        val average = Blurhash.averageColor(hash)!!
        val meanRed = pixels.map { (it shr 16) and 0xFF }.average()

        assertTrue(abs(meanRed - ((average shr 16) and 0xFF)) < 40)
    }

    @Test
    fun invalidHashesDecodeToNull() {
        assertNull(Blurhash.decode(null, 32, 32))
        assertNull(Blurhash.decode("short", 32, 32))
        assertNull(Blurhash.decode(hash.dropLast(1), 32, 32))
        assertNull(Blurhash.decode(hash.replace('L', '"'), 32, 32))
        assertNull(Blurhash.decode(hash, 0, 32))
    }
}

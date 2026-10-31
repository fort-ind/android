package com.fortind.zenith.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimatedFormatTest {

    private fun webp(flags: Int): ByteArray {
        val bytes = ByteArray(30)
        "RIFF".toByteArray().copyInto(bytes, 0)
        "WEBP".toByteArray().copyInto(bytes, 8)
        "VP8X".toByteArray().copyInto(bytes, 12)
        bytes[20] = flags.toByte()
        return bytes
    }

    private fun png(vararg chunks: String): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val body = chunks.flatMap { type ->
            val data = ByteArray(4)
            listOf(0, 0, 0, data.size).map(Int::toByte) + type.toByteArray().toList() + data.toList() + List(4) { 0.toByte() }
        }
        return signature + body.toByteArray()
    }

    @Test
    fun animatedWebPIsDetectedFromTheVp8xFlags() {
        assertEquals(AnimatedFormat.WebP, detectAnimatedFormat(webp(0x02)))
        assertEquals(AnimatedFormat.WebP, detectAnimatedFormat(webp(0x12)))
    }

    @Test
    fun stillWebPIsIgnored() {
        assertNull(detectAnimatedFormat(webp(0x10)))
    }

    @Test
    fun apngNeedsAnAnimationControlChunkBeforeImageData() {
        assertEquals(AnimatedFormat.Png, detectAnimatedFormat(png("IHDR", "acTL", "IDAT")))
        assertNull(detectAnimatedFormat(png("IHDR", "IDAT", "acTL")))
        assertNull(detectAnimatedFormat(png("IHDR", "IDAT")))
    }

    @Test
    fun otherFormatsAndTruncatedHeadersAreIgnored() {
        assertNull(detectAnimatedFormat(ByteArray(0)))
        assertNull(detectAnimatedFormat("GIF89a".toByteArray()))
        assertNull(detectAnimatedFormat(webp(0x02).copyOf(12)))
    }
}

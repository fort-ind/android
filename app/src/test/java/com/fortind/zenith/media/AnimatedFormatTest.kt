package com.fortind.zenith.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun webPCanvasSizeIsReadFromTheVp8xChunk() {
        val bytes = webp(0x02)
        bytes[24] = 0xFF.toByte()
        bytes[25] = 0x01
        bytes[27] = 0x63
        assertEquals(CanvasSize(512, 100), animatedCanvasSize(bytes, AnimatedFormat.WebP))
        assertNull(animatedCanvasSize(bytes.copyOf(28), AnimatedFormat.WebP))
    }

    @Test
    fun apngCanvasSizeIsReadFromTheHeaderChunk() {
        val bytes = ByteArray(24)
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).copyInto(bytes, 0)
        bytes[13] = 13
        "IHDR".toByteArray().copyInto(bytes, 12)
        bytes[18] = 0x10
        bytes[22] = 0x08
        assertEquals(CanvasSize(4_096, 2_048), animatedCanvasSize(bytes, AnimatedFormat.Png))
        assertNull(animatedCanvasSize(bytes.copyOf(20), AnimatedFormat.Png))
    }

    @Test
    fun hugeCanvasesDoNotFitAndDoNotOverflow() {
        val bytes = ByteArray(24) { 0xFF.toByte() }
        val size = animatedCanvasSize(bytes, AnimatedFormat.Png)
        assertEquals(CanvasSize(0xFFFFFFFFL, 0xFFFFFFFFL), size)
        assertFalse(size!!.fitsWithin(2_048L * 2_048))
        assertTrue(CanvasSize(2_048, 2_048).fitsWithin(2_048L * 2_048))
        assertFalse(CanvasSize(0, 10).fitsWithin(2_048L * 2_048))
        assertFalse(CanvasSize(2_049, 2_048).fitsWithin(2_048L * 2_048))
    }
}

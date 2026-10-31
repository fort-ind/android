package com.fortind.zenith.media

enum class AnimatedFormat { WebP, Png }

private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)
private val VP8X = "VP8X".toByteArray(Charsets.US_ASCII)
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private const val PNG_ANIMATION_CONTROL = "acTL"
private const val PNG_IMAGE_DATA = "IDAT"
private const val WEBP_FLAGS_OFFSET = 20
private const val WEBP_ANIMATION_FLAG = 0x02
private const val PNG_CHUNK_OVERHEAD = 12

fun detectAnimatedFormat(header: ByteArray): AnimatedFormat? = when {
    isAnimatedWebP(header) -> AnimatedFormat.WebP
    isAnimatedPng(header) -> AnimatedFormat.Png
    else -> null
}

private fun isAnimatedWebP(bytes: ByteArray): Boolean =
    bytes.size > WEBP_FLAGS_OFFSET &&
        bytes.startsWith(RIFF, 0) &&
        bytes.startsWith(WEBP, 8) &&
        bytes.startsWith(VP8X, 12) &&
        bytes[WEBP_FLAGS_OFFSET].toInt() and WEBP_ANIMATION_FLAG != 0

private fun isAnimatedPng(bytes: ByteArray): Boolean {
    if (!bytes.startsWith(PNG_SIGNATURE, 0)) return false
    var offset = PNG_SIGNATURE.size
    while (offset + 8 <= bytes.size) {
        val length = bytes.readInt(offset)
        if (length < 0) return false
        when (String(bytes, offset + 4, 4, Charsets.US_ASCII)) {
            PNG_ANIMATION_CONTROL -> return true
            PNG_IMAGE_DATA -> return false
        }
        offset += PNG_CHUNK_OVERHEAD + length
    }
    return false
}

private fun ByteArray.startsWith(prefix: ByteArray, at: Int): Boolean =
    size >= at + prefix.size && prefix.indices.all { this[at + it] == prefix[it] }

private fun ByteArray.readInt(at: Int): Int =
    (this[at].toInt() and 0xFF shl 24) or
        (this[at + 1].toInt() and 0xFF shl 16) or
        (this[at + 2].toInt() and 0xFF shl 8) or
        (this[at + 3].toInt() and 0xFF)

package com.fortind.zenith.media

import java.io.InputStream

const val SNIFF_BYTES = 32

private val PngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val JxlContainer = byteArrayOf(0, 0, 0, 0x0C, 0x4A, 0x58, 0x4C, 0x20)
private val WebmSignature = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
private val HeicBrands = setOf("heic", "heix", "heim", "heis", "hevc", "hevx")
private val HeifBrands = setOf("mif1", "msf1")
private val AvifBrands = setOf("avif", "avis")
private val AudioBrands = setOf("M4A ", "M4B ", "M4P ")
private val TrustedOnlyWhenMatching = setOf(
    "image/bmp", "image/x-ms-bmp", "image/x-icon", "image/vnd.microsoft.icon", "image/gif", "image/svg+xml",
)

fun sniffMediaType(header: ByteArray): String? = when {
    header.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
    header.startsWith(PngSignature) -> "image/png"
    header.startsWithAscii("GIF87a") || header.startsWithAscii("GIF89a") -> "image/gif"
    header.startsWithAscii("RIFF") && header.asciiAt(8, 4) == "WEBP" -> "image/webp"
    header.startsWithAscii("II*\u0000") || header.startsWithAscii("MM\u0000*") -> "image/tiff"
    header.startsWith(0xFF, 0x0A) || header.startsWith(JxlContainer) -> "image/jxl"
    header.startsWith(0x00, 0x00, 0x01, 0x00) -> "image/x-icon"
    header.startsWithAscii("BM") -> "image/bmp"
    header.startsWith(WebmSignature) -> "video/webm"
    header.asciiAt(4, 4) == "ftyp" -> isoBrandType(header.asciiAt(8, 4))
    else -> null
}

fun sanitizerType(declared: String, sniffed: String?): String = when {
    sniffed == null || sniffed == declared -> declared
    sniffed.substringBefore('/') != declared.substringBefore('/') -> sniffed
    declared in TrustedOnlyWhenMatching -> sniffed
    else -> declared
}

fun InputStream.readHeader(limit: Int = SNIFF_BYTES): ByteArray {
    val buffer = ByteArray(limit)
    var filled = 0
    while (filled < limit) {
        val read = read(buffer, filled, limit - filled)
        if (read < 0) break
        filled += read
    }
    return buffer.copyOf(filled)
}

private fun isoBrandType(brand: String?): String? = when (brand) {
    null -> null
    in HeicBrands -> "image/heic"
    in HeifBrands -> "image/heif"
    in AvifBrands -> "image/avif"
    in AudioBrands -> "audio/mp4"
    "qt  " -> "video/quicktime"
    else -> "video/mp4"
}

private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.startsWithAscii(prefix: String): Boolean = asciiAt(0, prefix.length) == prefix

private fun ByteArray.asciiAt(offset: Int, length: Int): String? =
    if (size < offset + length) null else String(this, offset, length, Charsets.ISO_8859_1)

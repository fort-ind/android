package com.fortind.zenith.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.fortind.zenith.timeline.MediaKind
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID

data class LocalMedia(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
) {
    val kind: MediaKind
        get() = when {
            mimeType.startsWith("image/") -> MediaKind.Image
            mimeType.startsWith("video/") -> MediaKind.Video
            mimeType.startsWith("audio/") -> MediaKind.Audio
            else -> MediaKind.Other
        }
}

interface MediaReader {
    fun describe(uri: String): LocalMedia?

    fun open(uri: String): InputStream

    fun stage(uri: String): String = uri
}

class ContentResolverMediaReader(
    private val resolver: ContentResolver,
    private val stagingDir: File,
) : MediaReader {

    override fun describe(uri: String): LocalMedia? {
        val parsed = uri.toUri()
        if (parsed.scheme == ContentResolver.SCHEME_FILE) return describeFile(parsed)
        var name: String? = null
        var size = UNKNOWN_SIZE
        try {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
                }
            }
        } catch (_: SecurityException) {
            return null
        }
        val resolvedName = name?.takeIf { it.isNotBlank() } ?: parsed.lastPathSegment ?: return null
        val mime = resolver.getType(parsed) ?: mimeFromName(resolvedName) ?: return null
        return LocalMedia(uri, resolvedName, mime, if (size >= 0) size else measure(uri))
    }

    override fun open(uri: String): InputStream =
        resolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException(uri)

    override fun stage(uri: String): String {
        if (uri.toUri().scheme == ContentResolver.SCHEME_FILE) return uri
        val media = describe(uri) ?: return uri
        if (media.sizeBytes > STAGE_LIMIT_BYTES) return uri
        return try {
            stagingDir.mkdirs()
            val target = File(stagingDir, "${UUID.randomUUID()}-${media.stageName()}")
            open(uri).use { input -> target.outputStream().use { input.copyTo(it) } }
            Uri.fromFile(target).toString()
        } catch (_: IOException) {
            uri
        }
    }

    private fun LocalMedia.stageName(): String {
        val safe = name.replace('/', '_')
        if (safe.substringAfterLast('.', "").isNotEmpty()) return safe
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: return safe
        return "$safe.$extension"
    }

    private fun describeFile(parsed: Uri): LocalMedia? {
        val file = parsed.path?.let(::File)?.takeIf { it.isFile } ?: return null
        val mime = mimeFromName(file.name) ?: return null
        return LocalMedia(parsed.toString(), file.name, mime, file.length())
    }

    private fun measure(uri: String): Long = try {
        open(uri).use { input ->
            var total = 0L
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
            }
            total
        }
    } catch (_: IOException) {
        UNKNOWN_SIZE
    }

    private fun mimeFromName(name: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

    private companion object {
        const val UNKNOWN_SIZE = -1L
        const val STAGE_LIMIT_BYTES = 16L * 1024 * 1024
        const val BUFFER_BYTES = 64 * 1024
    }
}

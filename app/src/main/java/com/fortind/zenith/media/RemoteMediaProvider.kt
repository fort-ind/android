package com.fortind.zenith.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import coil3.SingletonImageLoader
import com.fortind.zenith.ZenithApplication
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

class RemoteMediaProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = parts(uri)?.mimeType

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cached = remoteUrl(uri)?.let(::cacheFile)?.takeIf { it.isFile }
        val row = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> parts(uri)?.name
                OpenableColumns.SIZE -> cached?.length()
                else -> null
            }
        }
        return MatrixCursor(columns).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        val url = remoteUrl(uri) ?: throw FileNotFoundException(uri.toString())
        val file = ensureLocal(url) ?: throw FileNotFoundException(url)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun cacheDir(): File = File(requireNotNull(context).cacheDir, CACHE_DIR)

    private fun cacheFile(url: String): File = File(cacheDir(), sha256(url))

    @Synchronized
    private fun ensureLocal(url: String): File? {
        val target = cacheFile(url)
        if (target.isFile) return target
        val context = requireNotNull(context)
        val dir = cacheDir().apply { mkdirs() }
        prune(dir)
        val partial = File(dir, "${target.name}.part")
        return try {
            if (!copyFromImageCache(context, url, partial)) download(context, url, partial)
            if (partial.renameTo(target)) target else null
        } catch (_: IOException) {
            partial.delete()
            null
        }
    }

    private fun copyFromImageCache(context: Context, url: String, into: File): Boolean {
        val snapshot = SingletonImageLoader.get(context).diskCache?.openSnapshot(url) ?: return false
        snapshot.use { into.outputStream().use { output -> it.data.toFile().inputStream().use { input -> input.copyTo(output) } } }
        return true
    }

    private fun download(context: Context, url: String, into: File) {
        val client = (context.applicationContext as ZenithApplication).container.okHttpClient
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val length = response.body.contentLength()
            if (length > MAX_BYTES) throw IOException("too large")
            into.outputStream().use { output ->
                val input = response.body.byteStream()
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw IOException("too large")
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun prune(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    companion object {
        internal const val CACHE_DIR = "remote-media"
        private const val MEDIA_PATH = "media"
        private const val PATH_SEGMENTS = 5
        private const val URL_SAFE_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        private const val MAX_BYTES = 64L * 1024 * 1024
        private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val BUFFER_BYTES = 64 * 1024

        fun authority(context: Context): String = "${context.packageName}.remotemedia"

        fun uriFor(context: Context, url: String, mimeType: String, name: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath(MEDIA_PATH)
            .appendPath(Base64.encodeToString(url.toByteArray(), URL_SAFE_FLAGS))
            .appendPath(mimeType.substringBefore('/'))
            .appendPath(mimeType.substringAfter('/'))
            .appendPath(name)
            .build()

        fun isRemoteMedia(context: Context, uri: Uri): Boolean = uri.scheme == "content" && uri.authority == authority(context)

        private class Parts(val url: String, val mimeType: String, val name: String)

        private fun parts(uri: Uri): Parts? {
            val segments = uri.pathSegments
            if (segments.size != PATH_SEGMENTS || segments[0] != MEDIA_PATH) return null
            val url = try {
                String(Base64.decode(segments[1], URL_SAFE_FLAGS))
            } catch (_: IllegalArgumentException) {
                return null
            }
            return Parts(url, segments[2] + "/" + segments[3], segments[4])
        }

        private fun remoteUrl(uri: Uri): String? = parts(uri)?.url?.takeIf { it.startsWith("https://") }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

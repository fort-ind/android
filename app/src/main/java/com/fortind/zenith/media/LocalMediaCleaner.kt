package com.fortind.zenith.media

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface LocalMediaCleaner {
    suspend fun release(media: LocalMedia)
}

object NoMediaCleanup : LocalMediaCleaner {
    override suspend fun release(media: LocalMedia) = Unit
}

class CacheMediaCleaner(private val cacheDir: () -> File) : LocalMediaCleaner {
    override suspend fun release(media: LocalMedia) {
        withContext(NonCancellable + Dispatchers.IO) {
            ownedCacheFile(media.uri, cacheDir())?.delete()
        }
    }
}

private val OwnedFolders = listOf("captures", "staged", "compressed", "sanitized")

fun ownedCacheFile(uri: String, cacheDir: File): File? {
    val path = try {
        URI(uri).takeIf { it.scheme == "file" }?.path
    } catch (_: URISyntaxException) {
        null
    } ?: return null
    val file = File(path).canonicalFile
    val root = cacheDir.canonicalFile
    val owned = OwnedFolders.any { file.path.startsWith(File(root, it).path + File.separator) }
    return file.takeIf { owned && it.isFile }
}

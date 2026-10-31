package com.fortind.zenith.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ImageReencoder(private val context: Context) {

    private val outputDir: File get() = File(context.cacheDir, "compressed").also { it.mkdirs() }

    suspend fun toJpeg(media: LocalMedia, maxEdge: Int?, quality: Int): LocalMedia? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(media.uri)
            .allowHardware(false)
            .apply {
                if (maxEdge == null) {
                    size(Size.ORIGINAL)
                } else {
                    size(maxEdge, maxEdge).scale(Scale.FIT).precision(Precision.INEXACT)
                }
            }
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@withContext null
        val output = File(outputDir, "${UUID.randomUUID()}.jpg")
        output.outputStream().use { result.image.toBitmap().compress(Bitmap.CompressFormat.JPEG, quality, it) }
        LocalMedia(Uri.fromFile(output).toString(), media.name.substringBeforeLast('.') + ".jpg", "image/jpeg", output.length())
    }
}

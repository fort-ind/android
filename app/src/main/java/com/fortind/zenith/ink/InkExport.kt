package com.fortind.zenith.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.ink.strokes.Stroke
import com.fortind.zenith.media.LocalMedia
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InkPicture(
    val strokes: List<Stroke>,
    val sheet: SheetSize,
    val pixels: PixelSize,
    val backgroundArgb: Int?,
    val photo: Bitmap?,
    val jpeg: Boolean,
)

class InkExporter(private val cacheDir: File, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun export(picture: InkPicture, displayName: String): LocalMedia = withContext(Dispatchers.Default) {
        val bitmap = createBitmap(picture.pixels.width, picture.pixels.height)
        try {
            val canvas = Canvas(bitmap)
            picture.backgroundArgb?.let(canvas::drawColor)
            picture.photo?.let { photo ->
                canvas.drawBitmap(
                    photo,
                    null,
                    Rect(0, 0, picture.pixels.width, picture.pixels.height),
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
                )
            }
            InkPainter().draw(
                canvas,
                picture.strokes,
                picture.pixels.width / picture.sheet.width,
                InkStyles.highlighterMultiplies(picture.backgroundArgb),
            )
            val extension = if (picture.jpeg) "jpg" else "png"
            val file = outputFile(extension)
            withContext(Dispatchers.IO) {
                file.outputStream().use { out ->
                    bitmap.compress(
                        if (picture.jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                        if (picture.jpeg) JPEG_QUALITY else PNG_QUALITY,
                        out,
                    )
                }
            }
            LocalMedia(
                uri = Uri.fromFile(file).toString(),
                name = "$displayName.$extension",
                mimeType = if (picture.jpeg) "image/jpeg" else "image/png",
                sizeBytes = file.length(),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun outputFile(extension: String): File {
        val directory = File(cacheDir, "captures").also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(clock()))
        return File(directory, "INK_$stamp.$extension")
    }

    private companion object {
        const val JPEG_QUALITY = 92
        const val PNG_QUALITY = 100
    }
}

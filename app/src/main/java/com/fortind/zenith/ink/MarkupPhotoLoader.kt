package com.fortind.zenith.ink

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MarkupPhotoLoader(private val context: Context) {
    suspend fun load(uris: List<String>): Bitmap? {
        for (uri in uris) {
            load(uri)?.let { return it }
        }
        return null
    }

    private suspend fun load(uri: String): Bitmap? = withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .memoryCachePolicy(CachePolicy.DISABLED)
            .size(SheetGeometry.MARKUP_MAX_PIXELS, SheetGeometry.MARKUP_MAX_PIXELS)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@withTimeoutOrNull null
        withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap()
            val limited = SheetGeometry.limitPixels(bitmap.width, bitmap.height)
            if (limited.width == bitmap.width && limited.height == bitmap.height) bitmap else bitmap.scale(limited.width, limited.height)
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 120_000L
    }
}

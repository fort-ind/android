package com.fortind.zenith.media

import android.os.Build
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import com.github.penfeizhou.animation.webp.WebPDrawable
import java.nio.ByteBuffer

class LegacyAnimatedDecoder(
    private val result: SourceFetchResult,
    private val format: AnimatedFormat,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = result.source.source().use { it.readByteArray() }
        val loader = object : ByteBufferLoader() {
            override fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
        }
        val drawable = when (format) {
            AnimatedFormat.WebP -> WebPDrawable(loader)
            AnimatedFormat.Png -> APNGDrawable(loader)
        }
        return DecodeResult(image = drawable.asImage(), isSampled = false)
    }

    class Factory(private val sdkInt: Int = Build.VERSION.SDK_INT) : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val peek = result.source.source().peek()
            peek.request(HEADER_BYTES)
            val header = peek.buffer.let { it.copy().readByteArray(minOf(it.size, HEADER_BYTES)) }
            val format = detectAnimatedFormat(header) ?: return null
            if (format == AnimatedFormat.WebP && sdkInt >= Build.VERSION_CODES.P) return null
            val canvas = animatedCanvasSize(header, format) ?: return null
            if (!canvas.fitsWithin(MAX_CANVAS_PIXELS)) return null
            if (peek.request(MAX_BYTES + 1)) return null
            return LegacyAnimatedDecoder(result, format)
        }
    }

    private companion object {
        const val HEADER_BYTES = 65_536L
        const val MAX_BYTES = 16L * 1024 * 1024
        const val MAX_CANVAS_PIXELS = 2_048L * 2_048
    }
}

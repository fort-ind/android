package com.fortind.zenith.ui.media

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.fortind.zenith.media.Blurhash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BLURHASH_SIZE = 32
private val blurhashCache = LruCache<String, ImageBitmap>(64)

@Composable
fun rememberBlurhash(hash: String?): ImageBitmap? {
    val bitmap by produceState(initialValue = hash?.let { blurhashCache.get(it) }, hash) {
        if (hash == null || value != null) return@produceState
        value = withContext(Dispatchers.Default) {
            Blurhash.decode(hash, BLURHASH_SIZE, BLURHASH_SIZE)?.let { pixels ->
                Bitmap.createBitmap(pixels, BLURHASH_SIZE, BLURHASH_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }?.also { blurhashCache.put(hash, it) }
    }
    return bitmap
}

@Composable
fun BlurhashImage(hash: String?, modifier: Modifier = Modifier) {
    val bitmap = rememberBlurhash(hash) ?: return
    Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
fun rememberImageRequest(url: String, animate: Boolean): ImageRequest {
    val context = LocalContext.current
    return remember(url, animate, context) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(animate)
            .apply {
                if (!animate) {
                    decoderFactory(BitmapFactoryDecoder.Factory())
                    memoryCacheKeyExtra("static", "1")
                }
            }
            .build()
    }
}

@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    animate: Boolean,
    modifier: Modifier = Modifier,
    blurhash: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier = modifier) {
        if (blurhash != null) BlurhashImage(blurhash, Modifier.matchParentSize())
        AsyncImage(
            model = rememberImageRequest(url, animate),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize(),
        )
    }
}

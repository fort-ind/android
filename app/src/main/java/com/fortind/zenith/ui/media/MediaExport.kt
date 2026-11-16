package com.fortind.zenith.ui.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.graphics.drawable.toBitmap
import androidx.print.PrintHelper
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import com.fortind.zenith.timeline.NoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class SaveTarget(val fileName: String, val mimeType: String)

class CreateMediaDocument : ActivityResultContract<SaveTarget, Uri?>() {
    override fun createIntent(context: Context, input: SaveTarget): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = input.mimeType
        putExtra(Intent.EXTRA_TITLE, input.fileName)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data.takeIf { resultCode == android.app.Activity.RESULT_OK }
}

fun saveTargetFor(file: NoteFile): SaveTarget = SaveTarget(suggestedFileName(file.name, file.id, file.mimeType), file.mimeType)

fun suggestedFileName(name: String?, id: String, mimeType: String): String {
    val base = name?.trim()?.replace(UnsafeFileNameChars, "_")?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
    val extension = extensionFor(mimeType)
    return when {
        base == null -> if (extension == null) id else "$id.$extension"
        base.contains('.') || extension == null -> base
        else -> "$base.$extension"
    }
}

private fun extensionFor(mimeType: String): String? =
    KnownExtensions[mimeType.lowercase()] ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

private val KnownExtensions = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
    "image/gif" to "gif",
    "image/avif" to "avif",
    "image/apng" to "png",
    "video/mp4" to "mp4",
    "video/webm" to "webm",
)

private const val PRINT_MAX_PX = 3_508

private val UnsafeFileNameChars =Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")

suspend fun downloadToDocument(context: Context, client: OkHttpClient, url: String, target: Uri): Boolean = withContext(Dispatchers.IO) {
    val saved = try {
        copyInto(context, client, url, target)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
    if (!saved) discardDocument(context, target)
    saved
}

private fun copyInto(context: Context, client: OkHttpClient, url: String, target: Uri): Boolean =
    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) return false
        val output = context.contentResolver.openOutputStream(target, "wt") ?: return false
        output.use { response.body.byteStream().copyTo(it) }
        true
    }

private fun discardDocument(context: Context, target: Uri) {
    try {
        DocumentsContract.deleteDocument(context.contentResolver, target)
    } catch (_: IOException) {
    } catch (_: SecurityException) {
    } catch (_: IllegalArgumentException) {
    } catch (_: UnsupportedOperationException) {
    }
}

suspend fun loadBitmap(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context).data(url).size(PRINT_MAX_PX, PRINT_MAX_PX).precision(Precision.INEXACT).allowHardware(false).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    return result.image.asDrawable(context.resources).toBitmap()
}

fun canPrint(): Boolean = PrintHelper.systemSupportsPrint()

fun printBitmap(context: Context, jobName: String, bitmap: Bitmap) {
    PrintHelper(context).apply {
        scaleMode = PrintHelper.SCALE_MODE_FIT
        colorMode = PrintHelper.COLOR_MODE_COLOR
    }.printBitmap(jobName, bitmap)
}

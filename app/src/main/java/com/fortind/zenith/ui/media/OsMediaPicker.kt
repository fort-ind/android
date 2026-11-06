package com.fortind.zenith.ui.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.activity.result.contract.ActivityResultContract

class PickOsMedia(
    private val imagesOnly: Boolean,
    private val maxItems: Int = 1,
) : ActivityResultContract<Unit, List<Uri>>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        if (isOsPhotoPickerAvailable()) osPickerIntent() else documentIntent()

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val clip = intent.clipData
        val uris = if (clip != null && clip.itemCount > 0) {
            (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        } else {
            listOfNotNull(intent.data)
        }
        return uris.take(maxItems)
    }

    private fun osPickerIntent(): Intent = Intent(ACTION_PICK_IMAGES).apply {
        if (imagesOnly) type = IMAGE_TYPE
        if (maxItems > 1) putExtra(EXTRA_PICK_IMAGES_MAX, maxItems)
    }

    private fun documentIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, if (imagesOnly) arrayOf(IMAGE_TYPE) else arrayOf(IMAGE_TYPE, VIDEO_TYPE))
        if (maxItems > 1) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
}

fun isOsPhotoPickerAvailable(): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> true
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= MIN_R_EXTENSION
    else -> false
}

private const val ACTION_PICK_IMAGES = "android.provider.action.PICK_IMAGES"
private const val EXTRA_PICK_IMAGES_MAX = "android.provider.extra.PICK_IMAGES_MAX"
private const val IMAGE_TYPE = "image/*"
private const val VIDEO_TYPE = "video/*"
private const val MIN_R_EXTENSION = 2

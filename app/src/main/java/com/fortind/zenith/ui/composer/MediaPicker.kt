package com.fortind.zenith.ui.composer

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

@Composable
fun rememberMediaPicker(remaining: Int, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val multipleLimit = multiPickLimit(remaining)
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(multipleLimit)) { uris ->
        currentOnPicked.value(uris)
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) currentOnPicked.value(listOf(uri))
    }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    return {
        if (remaining >= MIN_MULTIPLE) multiple.launch(request) else if (remaining == 1) single.launch(request)
    }
}

private fun multiPickLimit(remaining: Int): Int {
    val platformMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MediaStore.getPickImagesMaxLimit() else Int.MAX_VALUE
    return remaining.coerceAtMost(platformMax).coerceAtLeast(MIN_MULTIPLE)
}

private const val MIN_MULTIPLE = 2

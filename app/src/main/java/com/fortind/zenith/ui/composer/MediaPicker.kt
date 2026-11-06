package com.fortind.zenith.ui.composer

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import com.fortind.zenith.ui.media.PickOsMedia

@Composable
fun rememberMediaPicker(remaining: Int, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(PickOsMedia(imagesOnly = false, maxItems = pickLimit(remaining))) { uris ->
        if (uris.isNotEmpty()) currentOnPicked.value(uris)
    }
    return {
        if (remaining >= 1) launcher.launch(Unit)
    }
}

private fun pickLimit(remaining: Int): Int {
    val platformMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MediaStore.getPickImagesMaxLimit() else Int.MAX_VALUE
    return remaining.coerceAtMost(platformMax).coerceAtLeast(1)
}

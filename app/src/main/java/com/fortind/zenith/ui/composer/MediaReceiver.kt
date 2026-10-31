package com.fortind.zenith.ui.composer

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.fortind.zenith.media.MediaReader

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberMediaReceiver(
    reader: MediaReader,
    onMedia: (List<String>) -> Unit,
    onDragOver: (Boolean) -> Unit,
): ReceiveContentListener {
    val currentOnMedia by rememberUpdatedState(onMedia)
    val currentOnDragOver by rememberUpdatedState(onDragOver)
    val resolver = LocalContext.current.contentResolver
    return remember(reader, resolver) {
        object : ReceiveContentListener {
            override fun onDragEnter() = currentOnDragOver(true)

            override fun onDragExit() = currentOnDragOver(false)

            override fun onDragEnd() = currentOnDragOver(false)

            override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
                currentOnDragOver(false)
                if (!transferableContent.hasMediaType(MediaType.Image) && !transferableContent.hasMediaType(VideoMediaType)) {
                    return transferableContent
                }
                val staged = mutableListOf<String>()
                val remaining = transferableContent.consume { item ->
                    val uri = item.uri
                    if (uri != null && resolver.isMedia(uri)) {
                        staged += reader.stage(uri.toString())
                        true
                    } else {
                        false
                    }
                }
                if (staged.isNotEmpty()) currentOnMedia(staged)
                return remaining
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val VideoMediaType = MediaType("video/*")

private fun ContentResolver.isMedia(uri: Uri): Boolean {
    val type = getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase())
    return type != null && (type.startsWith("image/") || type.startsWith("video/"))
}

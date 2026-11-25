package com.fortind.zenith.ui.social

import android.app.Activity
import android.content.ClipDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import androidx.compose.runtime.rememberCoroutineScope
import com.fortind.zenith.media.MediaReader
import com.fortind.zenith.ui.composer.isComposerMedia
import com.fortind.zenith.ui.composer.stageForComposer
import com.fortind.zenith.ui.media.startedInThisWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class DropToPost internal constructor(
    private val activity: Activity?,
    private val reader: MediaReader,
    private val scope: CoroutineScope,
) {
    var hovering by mutableStateOf(false)
        private set

    internal var enabled = false

    internal var onMedia: (List<String>) -> Unit = {}

    fun accepts(event: DragAndDropEvent): Boolean = enabled && !event.startedInThisWindow() && acceptsDropToPost(event.mimeTypes())

    val target: DragAndDropTarget = object : DragAndDropTarget {
        override fun onEntered(event: DragAndDropEvent) {
            hovering = true
        }

        override fun onExited(event: DragAndDropEvent) {
            hovering = false
        }

        override fun onEnded(event: DragAndDropEvent) {
            hovering = false
        }

        override fun onDrop(event: DragAndDropEvent): Boolean {
            hovering = false
            val host = activity ?: return false
            val dragEvent = event.toAndroidDragEvent()
            val clip = dragEvent.clipData ?: return false
            val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            if (uris.isEmpty()) return false
            val permissions = host.requestDragAndDropPermissions(dragEvent)
            scope.launch {
                try {
                    val media = withContext(Dispatchers.IO) { uris.filter { isComposerMedia(host, host.contentResolver, it) } }
                    if (media.isNotEmpty()) onMedia(stageForComposer(host, reader, media))
                } finally {
                    permissions?.release()
                }
            }
            return true
        }
    }
}

fun acceptsDropToPost(mimeTypes: Collection<String>): Boolean =
    mimeTypes.any { ClipDescription.compareMimeTypes(it, "image/*") || ClipDescription.compareMimeTypes(it, "video/*") }

@Composable
fun rememberDropToPost(activity: Activity?, reader: MediaReader, enabled: Boolean, onMedia: (List<String>) -> Unit): DropToPost {
    val scope = rememberCoroutineScope()
    val drop = remember(activity, reader, scope) { DropToPost(activity, reader, scope) }
    SideEffect {
        drop.enabled = enabled
        drop.onMedia = onMedia
    }
    return drop
}

fun Modifier.dropToPost(drop: DropToPost): Modifier = dragAndDropTarget(shouldStartDragAndDrop = drop::accepts, target = drop.target)

@Composable
fun DropToPostOverlay(drop: DropToPost) {
    if (!drop.hovering) return
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.drop_to_post)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .background(colors.primaryContainer.copy(alpha = 0.88f), MaterialTheme.shapes.extraLarge)
            .border(3.dp, colors.primary, MaterialTheme.shapes.extraLarge)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(56.dp))
            Text(label, style = MaterialTheme.typography.headlineSmall, color = colors.onPrimaryContainer, textAlign = TextAlign.Center)
        }
    }
}

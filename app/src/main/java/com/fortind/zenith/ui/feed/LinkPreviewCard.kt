package com.fortind.zenith.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fortind.zenith.timeline.LinkPreview
import com.fortind.zenith.timeline.LinkPreviewState
import com.fortind.zenith.ui.media.NetworkImage
import kotlinx.coroutines.launch

private val LinkCardHeight = 88.dp
private const val LINK_DWELL_MS = 250L
private const val LINK_VISIBLE_FRACTION = 0.1f

@Composable
internal fun LinkPreviewSlot(url: String, environment: NoteEnvironment, callbacks: NoteCallbacks) {
    val source = environment.linkPreviews ?: return
    val scope = rememberCoroutineScope()
    var state by remember(url, source) { mutableStateOf(source.peek(url)) }
    var requested by remember(url, source) { mutableStateOf(false) }
    var gaveUp by remember(url, source) { mutableStateOf(false) }
    when (val current = state) {
        LinkPreviewState.None -> Unit
        is LinkPreviewState.Ready -> LinkPreviewCard(current.preview, environment.animate) { callbacks.openUrl(current.preview.url) }
        LinkPreviewState.Unknown -> if (!gaveUp) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LinkCardHeight)
                    .onVisibilityChanged(minDurationMs = LINK_DWELL_MS, minFractionVisible = LINK_VISIBLE_FRACTION) { visible ->
                        if (visible && !requested) {
                            requested = true
                            scope.launch {
                                val result = source.load(url)
                                state = result
                                gaveUp = result == LinkPreviewState.Unknown
                            }
                        }
                    },
            ) {}
        }
    }
}

@Composable
private fun LinkPreviewCard(preview: LinkPreview, animate: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LinkCardHeight)
            .testTag("note:link"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val thumbnail = preview.thumbnailUrl?.takeUnless { preview.sensitive }
            if (thumbnail != null) {
                NetworkImage(
                    url = thumbnail,
                    contentDescription = null,
                    animate = animate,
                    modifier = Modifier.size(LinkCardHeight),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                preview.siteName?.let { site ->
                    Text(
                        text = site,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                preview.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

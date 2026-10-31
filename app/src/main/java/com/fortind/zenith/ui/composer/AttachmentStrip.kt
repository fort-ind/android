package com.fortind.zenith.ui.composer

import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.fortind.zenith.R
import com.fortind.zenith.composer.Attachment
import com.fortind.zenith.composer.AttachmentFailure
import com.fortind.zenith.composer.AttachmentPhase
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.ui.media.NetworkImage
import com.fortind.zenith.ui.pointerHover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TileSize = 112.dp

@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    animate: Boolean,
    onOpen: (String) -> Unit,
    onEditDetails: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: (id: String, compress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        items(attachments.size, key = { attachments[it].localId }, contentType = { "attachment" }) { index ->
            AttachmentTile(
                attachment = attachments[index],
                position = index + 1,
                total = attachments.size,
                animate = animate,
                onOpen = { onOpen(attachments[index].localId) },
                onEditDetails = { onEditDetails(attachments[index].localId) },
                onRemove = { onRemove(attachments[index].localId) },
                onRetry = { compress -> onRetry(attachments[index].localId, compress) },
            )
        }
    }
}

@Composable
private fun AttachmentTile(
    attachment: Attachment,
    position: Int,
    total: Int,
    animate: Boolean,
    onOpen: () -> Unit,
    onEditDetails: () -> Unit,
    onRemove: () -> Unit,
    onRetry: (compress: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var hovered by remember { mutableStateOf(false) }
    val kind = attachment.media.kind
    val label = stringResource(
        when (kind) {
            MediaKind.Image -> R.string.attachment_photo
            MediaKind.Video -> R.string.attachment_video
            else -> R.string.attachment_file
        },
        position,
        total,
    )
    val state = when (attachment.phase) {
        AttachmentPhase.Compressing -> stringResource(R.string.attachment_state_compressing)
        AttachmentPhase.Uploading -> stringResource(R.string.attachment_state_uploading, (attachment.progress * PERCENT).toInt())
        AttachmentPhase.Ready -> stringResource(R.string.attachment_state_ready)
        AttachmentPhase.Failed -> failureText(attachment)
    }
    Box(
        modifier = Modifier
            .size(TileSize)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerHighest)
            .pointerHover { hovered = it }
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onOpen)
            .then(if (focused) Modifier.border(3.dp, colors.primary, MaterialTheme.shapes.large) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = state
            },
    ) {
        AttachmentThumbnail(attachment, animate)
        if (kind == MediaKind.Video && attachment.phase == AttachmentPhase.Ready) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp).size(24.dp))
            }
        }
        if (hovered && attachment.phase == AttachmentPhase.Ready) {
            Box(modifier = Modifier.fillMaxSize().background(colors.onSurface.copy(alpha = 0.08f)))
        }
        when (attachment.phase) {
            AttachmentPhase.Compressing, AttachmentPhase.Uploading -> ProgressCover(attachment)
            AttachmentPhase.Failed -> FailureCover(attachment, state, onRetry)
            AttachmentPhase.Ready -> ReadyBadges(attachment, onEditDetails)
        }
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.attachment_remove), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(attachment: Attachment, animate: Boolean) {
    val media = attachment.media
    when (media.kind) {
        MediaKind.Image -> NetworkImage(
            url = media.uri,
            contentDescription = null,
            animate = animate,
            blurhash = attachment.remote?.blurhash,
            modifier = Modifier.fillMaxSize(),
        )
        MediaKind.Video -> {
            val remoteThumbnail = attachment.remote?.thumbnailUrl
            if (remoteThumbnail != null) {
                NetworkImage(url = remoteThumbnail, contentDescription = null, animate = false, modifier = Modifier.fillMaxSize())
            } else {
                val frame by rememberVideoFrame(media.uri)
                frame?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                if (media.kind == MediaKind.Audio) Icons.Default.AudioFile else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun rememberVideoFrame(uri: String): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri) {
        if (uri.startsWith("http")) return@produceState
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri.toUri())
                retriever.getFrameAtTime(0)?.asImageBitmap()
            } catch (_: RuntimeException) {
                null
            } finally {
                retriever.release()
            }
        }
    }
}

@Composable
private fun ProgressCover(attachment: Attachment) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        if (attachment.phase == AttachmentPhase.Compressing && attachment.progress <= 0f) {
            CircularProgressIndicator(color = Color.White, trackColor = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
        } else {
            CircularProgressIndicator(
                progress = { attachment.progress },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun FailureCover(attachment: Attachment, message: String, onRetry: (compress: Boolean) -> Unit) {
    val tooLarge = attachment.failure == AttachmentFailure.TooLarge
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).padding(4.dp),
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 2, modifier = Modifier.padding(horizontal = 4.dp))
        val canRetry = attachment.failure != AttachmentFailure.Unreadable
        if (canRetry) {
            TextButton(onClick = { onRetry(tooLarge) }) {
                Text(
                    stringResource(if (tooLarge) R.string.attachment_compress else R.string.attachment_retry),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ReadyBadges(attachment: Attachment, onEditDetails: () -> Unit) {
    val hasAlt = attachment.altText.isNotBlank()
    val colors = MaterialTheme.colorScheme
    val altLabel = stringResource(if (hasAlt) R.string.attachment_alt else R.string.attachment_add_description)
    Surface(
        onClick = onEditDetails,
        shape = MaterialTheme.shapes.small,
        color = if (hasAlt) colors.primaryContainer else Color.Black.copy(alpha = 0.65f),
        contentColor = if (hasAlt) colors.onPrimaryContainer else Color.White,
        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).semantics { contentDescription = altLabel },
    ) {
        Text(
            stringResource(R.string.attachment_alt),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
    if (attachment.isSensitive) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        ) {
            Icon(
                Icons.Default.VisibilityOff,
                contentDescription = stringResource(R.string.attachment_sensitive),
                modifier = Modifier.padding(4.dp).size(16.dp),
            )
        }
    }
}

@Composable
private fun failureText(attachment: Attachment): String {
    val context = LocalContext.current
    return when (attachment.failure) {
        AttachmentFailure.TooLarge -> stringResource(R.string.attachment_too_large, Formatter.formatShortFileSize(context, attachment.media.sizeBytes))
        AttachmentFailure.NoSpace -> stringResource(R.string.attachment_no_space)
        AttachmentFailure.Unreadable -> stringResource(R.string.attachment_unreadable)
        AttachmentFailure.PermissionMissing -> stringResource(R.string.attachment_permission)
        AttachmentFailure.Network, null -> stringResource(R.string.attachment_network)
    }
}

private const val PERCENT = 100f

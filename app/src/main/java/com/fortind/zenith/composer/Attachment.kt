package com.fortind.zenith.composer

import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.timeline.NoteFile

const val MAX_ATTACHMENTS = 16

enum class AttachmentPhase { Compressing, Uploading, Ready, Failed }

enum class AttachmentFailure { TooLarge, NoSpace, Unreadable, PermissionMissing, Network }

data class Attachment(
    val localId: String,
    val media: LocalMedia,
    val phase: AttachmentPhase,
    val progress: Float = 0f,
    val failure: AttachmentFailure? = null,
    val remote: NoteFile? = null,
    val altText: String = "",
    val isSensitive: Boolean = false,
    val owned: Boolean = true,
) {
    val isReady: Boolean get() = phase == AttachmentPhase.Ready && remote != null

    val needsSync: Boolean
        get() = remote?.let { it.altText.orEmpty() != altText || it.isSensitive != isSensitive } ?: false
}

data class DraftAttachment(val file: NoteFile, val altText: String, val isSensitive: Boolean)

fun Attachment.toDraft(): DraftAttachment? = remote?.takeIf { isReady && owned }?.let { DraftAttachment(it, altText, isSensitive) }

fun DraftAttachment.toAttachment(): Attachment = Attachment(
    localId = file.id,
    media = LocalMedia(uri = file.url, name = file.name ?: file.id, mimeType = file.mimeType, sizeBytes = 0L),
    phase = AttachmentPhase.Ready,
    progress = 1f,
    remote = file,
    altText = altText,
    isSensitive = isSensitive,
    owned = true,
)

fun NoteFile.toExistingAttachment(): Attachment = Attachment(
    localId = id,
    media = LocalMedia(uri = url, name = name ?: id, mimeType = mimeType, sizeBytes = 0L),
    phase = AttachmentPhase.Ready,
    progress = 1f,
    remote = this,
    altText = altText.orEmpty(),
    isSensitive = isSensitive,
    owned = false,
)

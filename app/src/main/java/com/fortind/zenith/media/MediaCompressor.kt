package com.fortind.zenith.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.fortind.zenith.timeline.MediaKind
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

interface MediaCompressor {
    suspend fun compress(media: LocalMedia, onProgress: (Float) -> Unit): LocalMedia
}

object NoCompression : MediaCompressor {
    override suspend fun compress(media: LocalMedia, onProgress: (Float) -> Unit): LocalMedia = media
}

@OptIn(UnstableApi::class)
class AndroidMediaCompressor(private val context: Context) : MediaCompressor {

    private val reencoder = ImageReencoder(context)

    private val outputDir: File get() = File(context.cacheDir, "compressed").also { it.mkdirs() }

    override suspend fun compress(media: LocalMedia, onProgress: (Float) -> Unit): LocalMedia = when {
        media.kind == MediaKind.Image && media.mimeType in COMPRESSIBLE_IMAGES && media.sizeBytes > IMAGE_THRESHOLD_BYTES ->
            compressImage(media)
        media.kind == MediaKind.Video && media.sizeBytes > VIDEO_THRESHOLD_BYTES -> compressVideo(media, onProgress)
        else -> media
    }

    private suspend fun compressImage(media: LocalMedia): LocalMedia {
        val shrunk = reencoder.toJpeg(media, IMAGE_MAX_EDGE, IMAGE_QUALITY) ?: return media
        if (media.sizeBytes > 0 && shrunk.sizeBytes >= media.sizeBytes) {
            File(shrunk.uri.toUri().path.orEmpty()).delete()
            return media
        }
        return shrunk
    }

    private suspend fun compressVideo(media: LocalMedia, onProgress: (Float) -> Unit): LocalMedia {
        val source = readVideoGeometry(media.uri)
        val scale = source?.takeIf { it.shortEdge > VIDEO_MAX_SHORT_EDGE }?.downscaledTo(VIDEO_MAX_SHORT_EDGE)
        val large = (source?.shortEdge ?: 0) > VIDEO_MAX_SHORT_EDGE
        val output = videoCodecs().firstNotNullOfOrNull { codec ->
            exportTo(media, codec, listOfNotNull(scale), codec.bitrate(large), onProgress)
        } ?: return media
        if (media.sizeBytes > 0 && output.length() >= media.sizeBytes) {
            output.delete()
            return media
        }
        return LocalMedia(Uri.fromFile(output).toString(), media.name.substringBeforeLast('.') + ".mp4", MimeTypes.VIDEO_MP4, output.length())
    }

    private fun videoCodecs(): List<VideoCodec> =
        if (hardwareHevcEncoder) listOf(VideoCodec.Hevc, VideoCodec.Avc) else listOf(VideoCodec.Avc)

    private val hardwareHevcEncoder: Boolean by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.isHardware() && info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H265, ignoreCase = true) }
        }
    }

    private suspend fun exportTo(
        media: LocalMedia,
        codec: VideoCodec,
        videoEffects: List<androidx.media3.common.Effect>,
        bitrate: Int,
        onProgress: (Float) -> Unit,
    ): File? {
        val output = File(outputDir, "${UUID.randomUUID()}.mp4")
        return try {
            withContext(Dispatchers.Main) { export(media, output, codec.mimeType, videoEffects, bitrate, onProgress) }
            output
        } catch (e: CancellationException) {
            output.delete()
            throw e
        } catch (_: IOException) {
            output.delete()
            null
        } catch (_: RuntimeException) {
            output.delete()
            null
        }
    }

    private suspend fun export(
        media: LocalMedia,
        output: File,
        videoMimeType: String,
        videoEffects: List<androidx.media3.common.Effect>,
        bitrate: Int,
        onProgress: (Float) -> Unit,
    ) = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val holder = ProgressHolder()
        lateinit var transformer: Transformer
        val poll = object : Runnable {
            override fun run() {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / PERCENT)
                handler.postDelayed(this, PROGRESS_POLL_MS)
            }
        }
        transformer = Transformer.Builder(context)
            .setVideoMimeType(videoMimeType)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                    .build(),
            )
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll)
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacks(poll)
                        if (continuation.isActive) continuation.resumeWithException(IOException("Video export failed", exportException))
                    }
                },
            )
            .build()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(media.uri))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
        continuation.invokeOnCancellation {
            handler.post {
                handler.removeCallbacks(poll)
                transformer.cancel()
            }
        }
        transformer.start(item, output.absolutePath)
        handler.post(poll)
    }

    private fun readVideoGeometry(uri: String): VideoGeometry? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri.toUri())
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation % 180 == 0) VideoGeometry(width, height) else VideoGeometry(height, width)
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private class VideoGeometry(val width: Int, val height: Int) {
        val shortEdge: Int get() = minOf(width, height)

        fun downscaledTo(targetShortEdge: Int): Presentation {
            val ratio = targetShortEdge.toFloat() / shortEdge
            return Presentation.createForWidthAndHeight(
                evenEdge(width * ratio),
                evenEdge(height * ratio),
                Presentation.LAYOUT_SCALE_TO_FIT,
            )
        }

        private fun evenEdge(value: Float): Int = value.roundToInt() and 1.inv()
    }

    private enum class VideoCodec(val mimeType: String, private val standardBitrate: Int, private val largeBitrate: Int) {
        Hevc(MimeTypes.VIDEO_H265, standardBitrate = 3_000_000, largeBitrate = 5_000_000),
        Avc(MimeTypes.VIDEO_H264, standardBitrate = 5_000_000, largeBitrate = 8_000_000);

        fun bitrate(large: Boolean): Int = if (large) largeBitrate else standardBitrate
    }

    private companion object {
        val COMPRESSIBLE_IMAGES = setOf("image/jpeg", "image/heic", "image/heif", "image/avif")
        const val IMAGE_THRESHOLD_BYTES = 1L * 1024 * 1024
        const val VIDEO_THRESHOLD_BYTES = 8L * 1024 * 1024
        const val IMAGE_MAX_EDGE = 3840
        const val IMAGE_QUALITY = 88
        const val VIDEO_MAX_SHORT_EDGE = 1080
        const val PROGRESS_POLL_MS = 300L
        const val PERCENT = 100f
    }
}

private fun MediaCodecInfo.isHardware(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isHardwareAccelerated
    } else {
        SoftwareCodecPrefixes.none { name.startsWith(it, ignoreCase = true) }
    }

private val SoftwareCodecPrefixes = listOf("OMX.google.", "c2.android.")

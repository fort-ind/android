package com.fortind.zenith.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.fortind.zenith.timeline.MediaKind
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationNotRemovedException : IOException("Could not strip location")

interface MediaSanitizer {
    suspend fun stripLocation(media: LocalMedia): LocalMedia
}

object NoSanitizing : MediaSanitizer {
    override suspend fun stripLocation(media: LocalMedia): LocalMedia = media
}

private enum class ImageLocation { Clean, Found, Unreadable }

class AndroidMediaSanitizer(private val context: Context, private val reader: MediaReader) : MediaSanitizer {

    private val reencoder = ImageReencoder(context)

    override suspend fun stripLocation(media: LocalMedia): LocalMedia {
        return withContext(Dispatchers.IO) {
            when (media.kind) {
                MediaKind.Video -> stripVideoLocation(media)
                MediaKind.Image -> stripImageLocation(media)
                else -> media
            }
        }
    }

    private suspend fun stripImageLocation(media: LocalMedia): LocalMedia {
        if (media.mimeType in METADATA_FREE_FORMATS) return media
        val location = imageLocation(media)
        return when {
            location == ImageLocation.Clean -> media
            location == ImageLocation.Found && media.mimeType in EDITABLE_FORMATS ->
                editedCopy(media)?.takeIf { imageLocation(it) == ImageLocation.Clean } ?: reencoded(media)
            else -> reencoded(media)
        }
    }

    private suspend fun reencoded(media: LocalMedia): LocalMedia =
        reencoder.toJpeg(media, null, LOSSLESS_LIKE_QUALITY)?.takeIf { imageLocation(it) == ImageLocation.Clean }
            ?: throw LocationNotRemovedException()

    private fun imageLocation(media: LocalMedia): ImageLocation {
        val inXmpPackets = try {
            reader.open(media.uri).use(::streamHasXmpLocation)
        } catch (_: IOException) {
            return ImageLocation.Unreadable
        }
        if (inXmpPackets) return ImageLocation.Found
        if (media.mimeType in XMP_ONLY_FORMATS) return ImageLocation.Clean
        if (!exifReadable(media.mimeType)) return ImageLocation.Unreadable
        return try {
            reader.open(media.uri).use { input ->
                val exif = ExifInterface(input)
                val xmp = exif.getAttributeBytes(ExifInterface.TAG_XMP)?.toString(Charsets.UTF_8)
                if (GPS_TAGS.any(exif::hasAttribute) || xmp?.let(::xmpHasLocation) == true) ImageLocation.Found else ImageLocation.Clean
            }
        } catch (_: IOException) {
            ImageLocation.Unreadable
        } catch (_: RuntimeException) {
            ImageLocation.Unreadable
        }
    }

    private fun exifReadable(mimeType: String): Boolean = when (mimeType) {
        in HEIF_FORMATS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        AVIF -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        else -> ExifInterface.isSupportedMimeType(mimeType)
    }

    private fun editedCopy(media: LocalMedia): LocalMedia? {
        val output = sanitizedFile(media.name.substringAfterLast('.', "jpg"))
        return try {
            reader.open(media.uri).use { input -> output.outputStream().use { input.copyTo(it) } }
            val exif = ExifInterface(output.absolutePath)
            GPS_TAGS.forEach { exif.setAttribute(it, null) }
            exif.getAttributeBytes(ExifInterface.TAG_XMP)?.toString(Charsets.UTF_8)?.takeIf(::xmpHasLocation)?.let { xmp ->
                exif.setAttribute(ExifInterface.TAG_XMP, withoutXmpLocation(xmp))
            }
            exif.saveAttributes()
            LocalMedia(Uri.fromFile(output).toString(), media.name, media.mimeType, output.length())
        } catch (_: IOException) {
            output.delete()
            null
        } catch (_: RuntimeException) {
            output.delete()
            null
        }
    }

    private fun stripVideoLocation(media: LocalMedia): LocalMedia {
        if (videoLocationReported(media) == false && videoBoxesHaveLocation(media) == false) return media
        val output = sanitizedFile(media.name.substringAfterLast('.', "mp4"))
        try {
            reader.open(media.uri).use { input -> output.outputStream().use { input.copyTo(it) } }
            neutralizeMp4Location(output)
            val cleaned = LocalMedia(Uri.fromFile(output).toString(), media.name, media.mimeType, output.length())
            if (videoLocationReported(cleaned) == true || videoBoxesHaveLocation(cleaned) != false) throw LocationNotRemovedException()
            return cleaned
        } catch (e: IOException) {
            output.delete()
            throw e
        }
    }

    private fun videoLocationReported(media: LocalMedia): Boolean? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, media.uri.toUri())
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) != null
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun videoBoxesHaveLocation(media: LocalMedia): Boolean? = try {
        reader.open(media.uri).use(::streamHasMp4Location)
    } catch (_: IOException) {
        null
    }

    private fun sanitizedFile(extension: String): File =
        File(File(context.cacheDir, "sanitized").also { it.mkdirs() }, "${UUID.randomUUID()}.$extension")

    private companion object {
        val EDITABLE_FORMATS = setOf("image/jpeg", "image/png", "image/webp")
        val METADATA_FREE_FORMATS = setOf("image/bmp", "image/x-ms-bmp", "image/x-icon", "image/vnd.microsoft.icon")
        val XMP_ONLY_FORMATS = setOf("image/gif", "image/svg+xml")
        val HEIF_FORMATS = setOf("image/heic", "image/heif")
        const val AVIF = "image/avif"
        const val LOSSLESS_LIKE_QUALITY = 95

        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
            ExifInterface.TAG_GPS_VERSION_ID,
        )
    }
}

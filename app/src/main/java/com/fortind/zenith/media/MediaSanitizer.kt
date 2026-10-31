package com.fortind.zenith.media

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.fortind.zenith.timeline.MediaKind
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MediaSanitizer {
    suspend fun stripLocation(media: LocalMedia): LocalMedia
}

object NoSanitizing : MediaSanitizer {
    override suspend fun stripLocation(media: LocalMedia): LocalMedia = media
}

class AndroidMediaSanitizer(private val context: Context, private val reader: MediaReader) : MediaSanitizer {

    private val reencoder = ImageReencoder(context)

    override suspend fun stripLocation(media: LocalMedia): LocalMedia {
        if (media.kind != MediaKind.Image) return media
        return withContext(Dispatchers.IO) {
            when (media.mimeType) {
                in EDITABLE_FORMATS -> if (hasLocation(media)) copyWithoutLocation(media) else media
                in REENCODED_FORMATS -> if (hasLocation(media)) reencoder.toJpeg(media, null, LOSSLESS_LIKE_QUALITY) ?: throw IOException("Could not strip location") else media
                else -> media
            }
        }
    }

    private fun hasLocation(media: LocalMedia): Boolean = try {
        reader.open(media.uri).use { input ->
            val exif = ExifInterface(input)
            GPS_TAGS.any(exif::hasAttribute)
        }
    } catch (_: IOException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private fun copyWithoutLocation(media: LocalMedia): LocalMedia {
        val extension = media.name.substringAfterLast('.', "jpg")
        val output = File(File(context.cacheDir, "sanitized").also { it.mkdirs() }, "${UUID.randomUUID()}.$extension")
        reader.open(media.uri).use { input -> output.outputStream().use { input.copyTo(it) } }
        val exif = ExifInterface(output.absolutePath)
        GPS_TAGS.forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
        return LocalMedia(Uri.fromFile(output).toString(), media.name, media.mimeType, output.length())
    }

    private companion object {
        val EDITABLE_FORMATS = setOf("image/jpeg", "image/png", "image/webp")
        val REENCODED_FORMATS = setOf("image/heic", "image/heif", "image/avif")
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

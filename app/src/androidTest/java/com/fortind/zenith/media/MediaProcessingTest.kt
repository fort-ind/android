package com.fortind.zenith.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaProcessingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val reader = ContentResolverMediaReader(context.contentResolver, File(context.cacheDir, "staged"))
    private val created = mutableListOf<File>()

    @After
    fun cleanUp() {
        created.forEach { it.delete() }
    }

    private fun newFile(extension: String): File =
        File(context.cacheDir, "test-${UUID.randomUUID()}.$extension").also { created += it }

    private fun jpeg(width: Int, height: Int, noisy: Boolean = false, withLocation: Boolean): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (noisy) {
            val random = Random(7)
            val pixels = IntArray(width * height) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        } else {
            bitmap.eraseColor(Color.CYAN)
        }
        val file = newFile("jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, "ZenithTest")
        if (withLocation) exif.setLatLong(37.775, -122.419)
        exif.saveAttributes()
        return file
    }

    private fun describe(file: File): LocalMedia = reader.describe(Uri.fromFile(file).toString())!!

    private fun fileOf(media: LocalMedia): File = File(media.uri.toUri().path!!).also { created += it }

    @Test
    fun locationIsStrippedFromAJpegAndOtherMetadataSurvives() = runBlocking {
        val source = jpeg(640, 480, withLocation = true)
        assertNotNull("fixture must start with a location", ExifInterface(source.absolutePath).latLong)
        val media = describe(source)

        val cleaned = AndroidMediaSanitizer(context, reader).stripLocation(media)

        assertNotEquals(media.uri, cleaned.uri)
        val exif = ExifInterface(fileOf(cleaned).absolutePath)
        assertNull(exif.latLong)
        assertFalse(exif.hasAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertFalse(exif.hasAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertEquals("ZenithTest", exif.getAttribute(ExifInterface.TAG_MAKE))
        val decoded = BitmapFactory.decodeFile(fileOf(cleaned).absolutePath)
        assertEquals(640, decoded.width)
        assertEquals(480, decoded.height)
        assertNotNull("the original must be left untouched", ExifInterface(source.absolutePath).latLong)
    }

    @Test
    fun aPhotoWithoutLocationIsUploadedAsIs() = runBlocking {
        val media = describe(jpeg(320, 240, withLocation = false))

        val result = AndroidMediaSanitizer(context, reader).stripLocation(media)

        assertEquals(media, result)
    }

    @Test
    fun nonImagesAreNeverTouchedBySanitizing() = runBlocking {
        val video = LocalMedia("file:///does/not/exist.mp4", "clip.mp4", "video/mp4", 1024L)

        assertEquals(video, AndroidMediaSanitizer(context, reader).stripLocation(video))
    }

    @Test
    fun compressionDownscalesAndShrinksALargePhoto() = runBlocking {
        val source = jpeg(4200, 3000, noisy = true, withLocation = true)
        val media = describe(source)
        assertTrue("fixture should be big enough to compress", media.sizeBytes > 1024 * 1024)

        val result = AndroidMediaCompressor(context).compress(media) { }

        assertEquals("image/jpeg", result.mimeType)
        assertTrue("expected a smaller file", result.sizeBytes < media.sizeBytes)
        val output = fileOf(result)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.absolutePath, bounds)
        assertTrue("long edge should be capped at 3840, was ${bounds.outWidth}", maxOf(bounds.outWidth, bounds.outHeight) <= 3840)
        assertNull("compression re-encodes, so location must be gone", ExifInterface(output.absolutePath).latLong)
    }

    @Test
    fun smallPhotosAreNotRecompressed() = runBlocking {
        val media = describe(jpeg(640, 480, withLocation = false))

        assertEquals(media, AndroidMediaCompressor(context).compress(media) { })
    }
}

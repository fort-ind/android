package com.fortind.zenith.emoji

import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnicodeEmojiGlyphDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.targetContext.assets

    @Test
    fun theSystemFontCheckOnlyHidesEmojiNewerThanTheDevice() = runBlocking {
        val all = AssetUnicodeEmojiSource(assets, canDraw = { { true } }).groups().sumOf { it.emoji.size }
        val drawable = AssetUnicodeEmojiSource(assets).groups().sumOf { it.emoji.size }

        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "sdk=${Build.VERSION.SDK_INT} bundled=$all drawable=$drawable hidden=${all - drawable}\n")
        })
        assertTrue(drawable > 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) assertEquals(all, drawable)
    }
}

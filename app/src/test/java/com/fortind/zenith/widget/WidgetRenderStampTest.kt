package com.fortind.zenith.widget

import com.fortind.zenith.settings.AppIcon
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRenderStampTest {

    private val stamp = WidgetRenderStamp(signedIn = true, icon = AppIcon.DEFAULT, appVersion = 36)

    @Test
    fun aWidgetNeverRenderedBeforeNeedsARender() {
        assertTrue(needsRender(rendered = null, current = stamp))
    }

    @Test
    fun anUnchangedStateSkipsTheRender() {
        assertFalse(needsRender(rendered = stamp, current = stamp.copy()))
    }

    @Test
    fun signingInOrOutRenders() {
        assertTrue(needsRender(rendered = stamp, current = stamp.copy(signedIn = false)))
    }

    @Test
    fun anotherAppIconRenders() {
        assertTrue(needsRender(rendered = stamp, current = stamp.copy(icon = AppIcon.FALL)))
    }

    @Test
    fun anUpdatedAppRendersOnceSoANewLayoutShows() {
        assertTrue(needsRender(rendered = stamp, current = stamp.copy(appVersion = 37)))
    }
}

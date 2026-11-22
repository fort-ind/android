package com.fortind.zenith.ui

import androidx.browser.customtabs.CustomTabsClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomTabBrowserTest {
    @Test
    fun theSignInBrowserCanBeFoundThroughTheManifestQuery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val browser = CustomTabsClient.getPackageName(context, null)
        println("preferred Custom Tabs browser: $browser")
        assertNotNull(browser)
    }
}

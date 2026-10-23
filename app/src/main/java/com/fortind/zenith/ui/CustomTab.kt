package com.fortind.zenith.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens [url] in a Custom Tab themed to the app. Returns false if no browser can handle it.
 *
 * @param toolbarColor ARGB color for the tab toolbar, normally the theme's surface color.
 */
fun launchCustomTab(context: Context, url: String, darkTheme: Boolean, toolbarColor: Int): Boolean {
    val tab = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setColorScheme(
            if (darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT
        )
        .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
        .build()
    return try {
        tab.launchUrl(context, url.toUri())
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

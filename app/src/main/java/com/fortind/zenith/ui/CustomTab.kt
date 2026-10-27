package com.fortind.zenith.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

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
        launchBrowserView(context, url)
    } catch (_: SecurityException) {
        launchBrowserView(context, url)
    }
}

private fun launchBrowserView(context: Context, url: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    return try {
        context.startActivity(view)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

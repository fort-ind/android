package com.fortind.zenith.ui

import android.content.Context
import androidx.compose.ui.platform.UriHandler

class CustomTabUriHandler(
    private val context: Context,
    private val darkTheme: Boolean,
    private val toolbarColor: Int,
) : UriHandler {
    override fun openUri(uri: String) {
        launchCustomTab(context, uri, darkTheme, toolbarColor)
    }
}

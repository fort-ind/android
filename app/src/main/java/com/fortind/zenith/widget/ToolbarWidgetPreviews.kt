@file:OptIn(ExperimentalGlancePreviewApi::class)

package com.fortind.zenith.widget

import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.fortind.zenith.settings.AppIcon

@Composable
private fun PreviewToolbar(signedIn: Boolean) {
    val context = LocalContext.current
    WidgetTheme(AppIcon.DEFAULT) {
        ToolbarWidgetContent(signedIn, toolbarLabels(context), toolbarActions(context, composerOptions = null))
    }
}

@Preview(widthDp = 57, heightDp = 102)
@Preview(widthDp = 130, heightDp = 102)
@Preview(widthDp = 276, heightDp = 102)
@Preview(widthDp = 269, heightDp = 51)
@Preview(widthDp = 57, heightDp = 338)
@Preview(widthDp = 150, heightDp = 150)
@Preview(widthDp = 411, heightDp = 183)
@Preview(widthDp = 130, heightDp = 220)
@Preview(widthDp = 276, heightDp = 456)
@Composable
private fun SignedInToolbarPreview() {
    PreviewToolbar(signedIn = true)
}

@Preview(widthDp = 57, heightDp = 102)
@Preview(widthDp = 276, heightDp = 102)
@Preview(widthDp = 150, heightDp = 150)
@Preview(widthDp = 411, heightDp = 183)
@Preview(widthDp = 276, heightDp = 456)
@Composable
private fun SignedOutToolbarPreview() {
    PreviewToolbar(signedIn = false)
}

@Preview(widthDp = 130, heightDp = 102)
@Preview(widthDp = 276, heightDp = 220)
@Composable
private fun LoadErrorPreview() {
    val context = LocalContext.current
    val openApp = actionStartActivity(WidgetIntents.openApp(context))
    WidgetTheme(AppIcon.DEFAULT) {
        WidgetLoadError(errorLabels(context), onRetry = openApp, onOpenApp = openApp)
    }
}

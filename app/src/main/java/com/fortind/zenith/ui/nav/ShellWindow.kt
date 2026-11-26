package com.fortind.zenith.ui.nav

import android.content.IntentSender
import androidx.compose.runtime.Immutable
import com.fortind.zenith.settings.WindowBounds

@Immutable
class ShellWindow(
    val poppedOutTab: AppDestination?,
    val onLeaveTab: (AppDestination, AppRoute?) -> Unit,
    val onOpenTabWindow: (AppDestination, WindowBounds?) -> Unit,
    val onTopRouteChanged: (AppRoute?) -> Unit = {},
    val onOpenRouteInTabWindow: (AppDestination, AppRoute, WindowBounds?) -> Unit = { _, _, _ -> },
    val tabWindowSender: (AppDestination, AppRoute) -> IntentSender? = { _, _ -> null },
)

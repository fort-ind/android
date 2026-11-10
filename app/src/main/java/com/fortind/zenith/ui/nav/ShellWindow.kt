package com.fortind.zenith.ui.nav

import androidx.compose.runtime.Immutable
import com.fortind.zenith.settings.WindowBounds

@Immutable
class ShellWindow(
    val poppedOutTab: AppDestination?,
    val onLeaveTab: (AppDestination, AppRoute?) -> Unit,
    val onOpenTabWindow: (AppDestination, WindowBounds?) -> Unit,
)

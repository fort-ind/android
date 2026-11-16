package com.fortind.zenith.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration

fun hasHardwareKeyboard(configuration: Configuration): Boolean =
    configuration.keyboard != Configuration.KEYBOARD_NOKEYS && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO

@Composable
fun rememberInitialFocus(keyboardOnly: Boolean = false): FocusRequester {
    val requester = remember { FocusRequester() }
    val hasKeyboard = hasHardwareKeyboard(LocalConfiguration.current)
    LaunchedEffect(requester) { if (!keyboardOnly || hasKeyboard) requester.requestFocus() }
    return requester
}

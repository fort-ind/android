package com.fortind.zenith.ui.composer

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OverlayFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val behindFocus = FocusRequester()

    private fun show(open: Boolean) {
        lateinit var inputMode: InputModeManager
        val focus = OverlayFocus()
        rule.setContent {
            inputMode = LocalInputModeManager.current
            Column(modifier = Modifier.onPreviewKeyEvent { open && focus.enterFrom(it) }) {
                Box(modifier = Modifier.size(40.dp).focusRequester(behindFocus).focusable().testTag("behind"))
                Box(modifier = Modifier.size(40.dp).focusable().testTag("behind2"))
                Column(modifier = Modifier.overlayFocusHost(focus, open)) {
                    if (open) {
                        Box(modifier = Modifier.size(40.dp).focusable().testTag("first"))
                        Box(modifier = Modifier.size(40.dp).focusable().testTag("last"))
                    }
                }
            }
        }
        rule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun tabFromNothingEndsUpInTheOverlay() {
        show(open = true)

        repeat(2) { press(Key.Tab) }

        rule.onNodeWithTag("behind").assertIsNotFocused()
        rule.onNodeWithTag("behind2").assertIsNotFocused()
    }

    @Test
    fun tabFromBehindMovesIntoTheOverlay() {
        show(open = true)
        rule.runOnIdle { behindFocus.requestFocus() }

        press(Key.Tab)

        rule.onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun focusCanNotLeaveAnOpenOverlay() {
        show(open = true)

        repeat(5) { press(Key.Tab) }

        rule.onNodeWithTag("last").assertIsFocused()
        rule.onNodeWithTag("behind").assertIsNotFocused()
        rule.onNodeWithTag("behind2").assertIsNotFocused()
    }

    @Test
    fun arrowsAlsoEnterTheOverlay() {
        show(open = true)
        rule.runOnIdle { behindFocus.requestFocus() }

        press(Key.DirectionDown)

        rule.onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun withNoOverlayTabReachesTheContent() {
        show(open = false)

        press(Key.Tab)

        rule.onNodeWithTag("behind").assertIsFocused()
    }
}

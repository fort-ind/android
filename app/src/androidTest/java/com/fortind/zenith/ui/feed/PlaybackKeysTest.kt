package com.fortind.zenith.ui.feed

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.media.FeedPlayback
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackKeysTest {
    @get:Rule
    val rule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = NoteFile("f1", "clip.mp4", "video/mp4", "https://invalid.invalid/clip.mp4", null, null, false, null, null, null)
    private val focus = FocusRequester()
    private var activations = 0
    private var bubbled = 0
    private lateinit var playback: FeedPlayback

    @Before
    fun setUp() {
        instrumentation.runOnMainSync { playback = FeedPlayback(instrumentation.targetContext) }
        rule.setContent {
            Box(
                modifier = Modifier
                    .onKeyEvent {
                        bubbled++
                        false
                    }
                    .playbackKeys(playback, file) { activations++ }
                    .focusRequester(focus)
                    .focusable()
                    .size(100.dp)
                    .testTag("video"),
            )
        }
        rule.runOnIdle { focus.requestFocus() }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { playback.player?.release() }
    }

    private fun press(key: Key) {
        rule.onNodeWithTag("video").performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun spaceStartsTheFile() {
        assertNull(playback.current)

        press(Key.Spacebar)

        rule.runOnIdle { assertEquals("f1", playback.current?.id) }
    }

    @Test
    fun arrowsPassThroughUntilTheFileIsLoaded() {
        press(Key.DirectionLeft)
        press(Key.DirectionRight)

        rule.runOnIdle { assertEquals(4, bubbled) }
    }

    @Test
    fun arrowsAreTakenOnceTheFileIsLoaded() {
        press(Key.Spacebar)
        val before = rule.runOnIdle { bubbled }

        press(Key.DirectionRight)

        rule.runOnIdle { assertEquals(before + 1, bubbled) }
    }

    @Test
    fun enterRunsTheActivateAction() {
        press(Key.Enter)

        rule.runOnIdle { assertEquals(1, activations) }
    }

    @Test
    fun aHeldModifierLeavesTheKeysAlone() {
        rule.onNodeWithTag("video").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Spacebar) } }
        rule.waitForIdle()

        rule.runOnIdle { assertNull(playback.current) }
    }
}

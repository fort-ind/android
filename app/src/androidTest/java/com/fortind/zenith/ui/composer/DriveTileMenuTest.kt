package com.fortind.zenith.ui.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.ui.theme.ZenithTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DriveTileMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val holdMillis = InstrumentationRegistry.getArguments().getString("holdMillis")?.toLong() ?: 0L

    private val file = NoteFile("f1", "picture.png", "image/png", "https://example.invalid/picture.png", null, null, false, null, null, null)

    private fun showMenu(attached: Boolean = false, selected: Boolean = false, canSelect: Boolean = true, onToggle: () -> Unit = {}, onCopy: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        rule.setContent {
            ZenithTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().testTag("area")) {
                        DriveTileMenu(file, Offset(120f, 300f), attached, selected, canSelect, onToggle, onCopy, onDismiss)
                    }
                }
            }
        }
    }

    @After
    fun hold() {
        Thread.sleep(holdMillis)
    }

    @Test
    fun rightClickingATileOpensItsMenuAndLeftClickingToggles() {
        var opened = 0
        var toggled = 0
        rule.setContent {
            Box(modifier = Modifier.size(200.dp).testTag("tile")) {
                DriveTile(file, selected = false, enabled = true, animate = false, onToggle = { toggled++ }, onOpenMenu = { _, _ -> opened++ })
            }
        }

        rule.onNodeWithTag("tile").performMouseInput { rightClick(center) }
        rule.runOnIdle {
            assertEquals(1, opened)
            assertEquals(0, toggled)
        }

        rule.onNodeWithTag("tile").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, toggled) }
    }

    @Test
    fun theMenuKeyOpensTheMenuOnAFocusedTile() {
        var opened = 0
        lateinit var inputMode: InputModeManager
        rule.setContent {
            inputMode = LocalInputModeManager.current
            Box(modifier = Modifier.size(200.dp)) {
                DriveTile(file, selected = false, enabled = true, animate = false, onToggle = {}, onOpenMenu = { _, _ -> opened++ })
            }
        }
        rule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }

        rule.onNodeWithContentDescription("picture.png").requestFocus().assertIsFocused().performKeyInput { pressKey(Key.Menu) }

        rule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun aFreeFileCanBeSelected() {
        var toggled = false
        showMenu(onToggle = { toggled = true })

        rule.onNodeWithText("picture.png").assertExists()
        rule.onNodeWithText("Select").assertIsEnabled().performClick()

        rule.runOnIdle { assertTrue(toggled) }
    }

    @Test
    fun aSelectedFileOffersDeselect() {
        showMenu(selected = true)

        rule.onNodeWithText("Deselect").assertIsEnabled()
    }

    @Test
    fun anAttachedFileSaysSoButCanStillCopyItsLink() {
        var copied = false
        showMenu(attached = true, onCopy = { copied = true })

        rule.onNodeWithText("Already attached").assertIsNotEnabled()
        rule.onNodeWithText("Copy link").performClick()

        rule.runOnIdle { assertTrue(copied) }
    }

    @Test
    fun noFreeSlotsDisablesSelect() {
        showMenu(canSelect = false)

        rule.onNodeWithText("Select").assertIsNotEnabled()
    }

    @Test
    fun tappingOutsideDismisses() {
        var dismissed = false
        showMenu(onDismiss = { dismissed = true })

        rule.onNodeWithTag("area").performTouchInput { click(bottomRight - Offset(5f, 5f)) }

        rule.runOnIdle { assertTrue(dismissed) }
    }
}

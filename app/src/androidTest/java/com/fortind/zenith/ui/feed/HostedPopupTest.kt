package com.fortind.zenith.ui.feed

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostedPopupTest {

    @get:Rule
    val rule = createComposeRule()

    private var rows by mutableStateOf((0 until 60).toList())
    private var popupOpen by mutableStateOf(true)
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    private fun showList(hosted: Boolean) {
        rule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            val popups = remember { HostedPopups() }
            CompositionLocalProvider(LocalHostedPopups provides if (hosted) popups else null) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.height(240.dp)) {
                        items(rows, key = { it }, contentType = { "row" }) { id ->
                            val anchor = remember { PopupAnchor() }
                            Box(Modifier.fillMaxWidth().height(60.dp).hostedPopupAnchor(anchor)) {
                                Text("row $id")
                                HostedPopup(anchor, open = id == 0 && popupOpen) {
                                    DropdownMenu(expanded = true, onDismissRequest = {}) {
                                        DropdownMenuItem(text = { Text("menu item") }, onClick = {})
                                    }
                                }
                            }
                        }
                    }
                    HostedPopupsLayer(popups)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun trackPopup(): WeakReference<View> {
        val owner = rule.onNodeWithText("menu item").fetchSemanticsNode().root as View
        return WeakReference(owner.parent as View)
    }

    private fun isCollected(reference: WeakReference<View>): Boolean {
        repeat(25) {
            Runtime.getRuntime().gc()
            Runtime.getRuntime().runFinalization()
            Thread.sleep(120)
            if (reference.get() == null) return true
        }
        return false
    }

    private fun scrollTo(index: Int) {
        rule.runOnIdle { scope.launch { listState.scrollToItem(index) } }
        rule.waitForIdle()
    }

    @Test
    fun aHostedPopupShowsWhileOpenAndGoesWhenClosed() {
        showList(hosted = true)
        rule.onNodeWithText("menu item").assertExists()
        rule.runOnIdle { popupOpen = false }
        rule.waitForIdle()
        rule.onNodeWithText("menu item").assertDoesNotExist()
    }

    @Test
    fun aPopupStillShowsInlineWhenThereIsNoHost() {
        showList(hosted = false)
        rule.onNodeWithText("menu item").assertExists()
    }

    @Test
    fun aHostedPopupIsReleasedWhenItsRowIsRemovedWhileOpen() {
        showList(hosted = true)
        val popup = trackPopup()
        rule.runOnIdle { rows = rows.drop(1) }
        rule.waitForIdle()
        rule.onNodeWithText("menu item").assertDoesNotExist()
        assertTrue("the popup window is still reachable after its row was removed", isCollected(popup))
    }

    @Test
    fun aHostedPopupIsReleasedWhenItsRowScrollsAwayWhileOpen() {
        showList(hosted = true)
        val popup = trackPopup()
        scrollTo(40)
        rule.onNodeWithText("menu item").assertDoesNotExist()
        assertTrue("the popup window is still reachable after its row scrolled away", isCollected(popup))
    }
}

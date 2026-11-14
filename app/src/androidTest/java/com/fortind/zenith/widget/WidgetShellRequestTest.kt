package com.fortind.zenith.widget

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.MainActivity
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.ui.nav.AppDestination
import com.fortind.zenith.ui.nav.ShellRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val RESTORE_TIMEOUT_MILLIS = 30_000L
private const val UI_TIMEOUT_MILLIS = 30_000L
private const val SEARCH_CLOSED = "search:field"
private const val SEARCH_OPEN = "search:results"

@RunWith(AndroidJUnit4::class)
class WidgetShellRequestTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sessions = (context.applicationContext as ZenithApplication).container.sessionService

    @Before
    fun enterTheShell() {
        val state = runBlocking { withTimeout(RESTORE_TIMEOUT_MILLIS) { sessions.state.first { it != SessionState.Restoring } } }
        if (state == SessionState.SignedOut) sessions.browseAsGuest()
    }

    private fun shown(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun selected(destination: AppDestination) =
        rule.onAllNodes(hasTestTag("nav:${destination.name}") and isSelected()).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aColdSearchRequestOpensHomeSearch() {
        ActivityScenario.launch<MainActivity>(WidgetIntents.shell(context, ShellRequest.SEARCH)).use {
            rule.waitUntil(UI_TIMEOUT_MILLIS) { shown(SEARCH_OPEN) }
        }
    }

    @Test
    fun aColdTabRequestOpensThatTab() {
        ActivityScenario.launch<MainActivity>(WidgetIntents.tab(context, AppDestination.GAMES)).use {
            rule.waitUntil(UI_TIMEOUT_MILLIS) { selected(AppDestination.GAMES) }
        }
    }

    @Test
    fun aWarmSearchRequestOpensHomeSearchFromAnotherTab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rule.waitUntil(UI_TIMEOUT_MILLIS) { shown(SEARCH_CLOSED) }
            assertTrue(!shown(SEARCH_OPEN))
            scenario.onActivity { it.startActivity(WidgetIntents.tab(context, AppDestination.GAMES)) }
            rule.waitUntil(UI_TIMEOUT_MILLIS) { !shown(SEARCH_CLOSED) }
            scenario.onActivity { it.startActivity(WidgetIntents.shell(context, ShellRequest.SEARCH)) }
            rule.waitUntil(UI_TIMEOUT_MILLIS) { shown(SEARCH_OPEN) }
        }
    }
}

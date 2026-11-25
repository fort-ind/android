package com.fortind.zenith.ui.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.R
import com.fortind.zenith.diagnostics.AppEnvironment
import com.fortind.zenith.diagnostics.ProblemKind
import com.fortind.zenith.diagnostics.ProblemReport
import com.fortind.zenith.diagnostics.ProblemReportStore
import com.fortind.zenith.diagnostics.ProblemReports
import com.fortind.zenith.ui.theme.ZenithTheme
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProblemReportPromptTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val environment = AppEnvironment("1.0", 36, "7.1.1", 25, "LG VS985")

    private class MemoryStore(var saved: ProblemReport?) : ProblemReportStore {
        override fun read() = saved
        override fun write(report: ProblemReport) {
            saved = report
        }
        override fun clear() {
            saved = null
        }
        override fun exitFloor(): Long = 0L
        override fun setExitFloor(timeMillis: Long) = Unit
    }

    private fun show(store: MemoryStore): ProblemReports {
        val reports = ProblemReports(store, exits = null, environment)
        val host = SnackbarHostState()
        rule.setContent {
            ZenithTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    ProblemReportPrompt(reports, host)
                    SnackbarHost(host, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        return reports
    }

    private val snackbarText get() = context.getString(R.string.problem_report_snackbar, context.getString(R.string.app_name))

    @Test
    fun aNewReportIsAnnouncedOnceAndOpensFromTheSnackbar() {
        val store = MemoryStore(ProblemReport(ProblemKind.Crash, 1_700_000_000_000, environment, "java.lang.IllegalStateException: boom", thread = "main"))
        show(store)

        rule.waitUntilAtLeastOneExists(hasText(snackbarText), TIMEOUT_MS)
        assertTrue(store.saved?.announced == true)
        rule.onNodeWithText(context.getString(R.string.problem_report_view)).performClick()

        rule.onNodeWithText(context.getString(R.string.problem_report_title)).assertIsDisplayed()
        rule.onNodeWithText("java.lang.IllegalStateException: boom", substring = true).assertIsDisplayed()
        rule.onNodeWithText("LG VS985", substring = true).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.problem_report_thread, "main"), substring = true).assertIsDisplayed()

        rule.onNodeWithText(context.getString(R.string.problem_report_delete)).performClick()
        rule.waitUntilDoesNotExist(hasText(context.getString(R.string.problem_report_title)), TIMEOUT_MS)
        assertNull(store.saved)
    }

    @Test
    fun anAnnouncedReportStaysQuiet() {
        val store = MemoryStore(ProblemReport(ProblemKind.NotResponding, 1_700_000_000_000, environment, "stack", announced = true))
        val reports = show(store)

        rule.waitUntil(TIMEOUT_MS) { reports.report.value != null }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText(snackbarText)).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}

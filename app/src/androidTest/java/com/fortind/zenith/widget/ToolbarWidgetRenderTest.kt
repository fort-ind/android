package com.fortind.zenith.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.ComposerWindowActivity
import com.fortind.zenith.MainActivity
import com.fortind.zenith.R
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.settings.AppIcon
import com.fortind.zenith.ui.nav.AppDestination
import com.fortind.zenith.ui.nav.ShellRequest
import com.fortind.zenith.window.AppWindows
import com.fortind.zenith.window.ComposerWindowTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TAP_TIMEOUT_MILLIS = 15_000L
private const val SETTLE_MILLIS = 1_500L
private const val POLL_MILLIS = 100L

@RunWith(AndroidJUnit4::class)
class ToolbarWidgetRenderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val labels = ToolbarLabels(
        search = "search",
        post = "post",
        notifications = "notifications",
        profile = "profile",
        games = "games",
        signIn = "Sign in",
        signInDescription = "sign in",
    )
    private val errorLabels = ErrorLabels(title = "title", retry = "Retry", retryDescription = "retry", openApp = "Open")

    private class ContentWidget(private val content: @androidx.compose.runtime.Composable (Context) -> Unit) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact

        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent { WidgetTheme(AppIcon.DEFAULT) { content(context) } }
        }
    }

    private fun inflate(views: RemoteViews): View {
        var root: View? = null
        instrumentation.runOnMainSync { root = views.apply(context, FrameLayout(context)) }
        return checkNotNull(root)
    }

    private fun toolbar(size: DpSize, signedIn: Boolean): View {
        val widget = ContentWidget { context -> ToolbarWidgetContent(signedIn, labels, toolbarActions(context, composerOptions = null)) }
        return inflate(runBlocking { widget.compose(context, size = size) })
    }

    private fun View.descendants(): List<View> =
        listOf(this) + ((this as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { group.getChildAt(it).descendants() } } ?: emptyList())

    private fun View.descriptions(): Set<String> = descendants().mapNotNull { it.contentDescription?.toString() }.toSet()

    private fun View.texts(): Set<String> = descendants().filterIsInstance<TextView>().map { it.text.toString() }.filter { it.isNotEmpty() }.toSet()

    @Test
    fun oneCellIsASingleSearchTarget() {
        assertEquals(setOf("search"), toolbar(DpSize(57.dp, 102.dp), signedIn = true).descriptions())
    }

    @Test
    fun fourCellRowShowsFourActions() {
        assertEquals(
            setOf("search", "post", "notifications", "profile"),
            toolbar(DpSize(276.dp, 102.dp), signedIn = true).descriptions(),
        )
    }

    @Test
    fun midSquareIsAGridOfFour() {
        assertEquals(
            setOf("search", "post", "notifications", "profile"),
            toolbar(DpSize(150.dp, 150.dp), signedIn = true).descriptions(),
        )
    }

    @Test
    fun wideTwoRowsShowEverything() {
        assertEquals(
            setOf("search", "post", "notifications", "profile", "games"),
            toolbar(DpSize(411.dp, 183.dp), signedIn = true).descriptions(),
        )
    }

    @Test
    fun largeWidgetShowsEverythingAndTheSearchText() {
        val root = toolbar(DpSize(276.dp, 456.dp), signedIn = true)
        assertEquals(setOf("search", "post", "notifications", "profile", "games"), root.descriptions())
        assertTrue("search" in root.texts())
    }

    @Test
    fun signedOutOffersSearchGamesAndSignIn() {
        val root = toolbar(DpSize(150.dp, 150.dp), signedIn = false)
        assertEquals(setOf("search", "games", "sign in"), root.descriptions())
        assertTrue("Sign in" in root.texts())
    }

    @Test
    fun signedOutSingleCellIsTheSignInAction() {
        assertEquals(setOf("sign in"), toolbar(DpSize(57.dp, 102.dp), signedIn = false).descriptions())
    }

    @Test
    fun anUnknownSizeStillRenders() {
        assertEquals(setOf("search"), toolbar(DpSize.Zero, signedIn = true).descriptions())
    }

    private val errorWidget = ContentWidget { context ->
        WidgetLoadError(errorLabels, retry = retryTap(context, actionStartActivity(WidgetIntents.openApp(context))), openApp = openAppTap(context))
    }

    private fun error(size: DpSize): View = inflate(runBlocking { errorWidget.compose(context, size = size) })

    private fun assertEveryTargetHovers(root: View) {
        val buttons = root.descendants().filter { it.contentDescription != null }
        assertTrue(buttons.isNotEmpty())
        for (button in buttons) {
            assertTrue("${button.contentDescription} is not the clickable view", button.hasOnClickListeners())
            assertTrue("${button.contentDescription} has no ripple of its own", button.background is RippleDrawable)
            instrumentation.runOnMainSync { button.isHovered = true }
            assertTrue(android.R.attr.state_hovered in button.background.state)
        }
    }

    @Test
    fun everyButtonCarriesItsOwnRippleSoItCanShowHover() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val sizes = listOf(
            DpSize(411.dp, 183.dp) to true,
            DpSize(276.dp, 456.dp) to true,
            DpSize(150.dp, 150.dp) to false,
            DpSize(57.dp, 102.dp) to true,
            DpSize(57.dp, 102.dp) to false,
        )
        for ((size, signedIn) in sizes) assertEveryTargetHovers(toolbar(size, signedIn))
        assertEveryTargetHovers(error(DpSize(130.dp, 102.dp)))
        assertEveryTargetHovers(error(DpSize(276.dp, 220.dp)))
    }

    @Test
    fun tappingRetryAsksTheRunningWidgetToReload() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val updater = (context.applicationContext as ZenithApplication).container.widgetUpdater
        val subscribed = CompletableDeferred<Unit>()
        runBlocking {
            val retried = async(Dispatchers.Default) {
                withTimeoutOrNull(TAP_TIMEOUT_MILLIS) { updater.retries.onSubscription { subscribed.complete(Unit) }.first() }
            }
            subscribed.await()
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                val views = errorWidget.compose(context, size = DpSize(276.dp, 220.dp))
                scenario.onActivity { activity ->
                    val frame = FrameLayout(activity)
                    frame.addView(views.apply(activity, frame))
                    activity.setContentView(frame)
                    assertTrue(frame.descendants().first { it.contentDescription?.toString() == "Retry" }.performClick())
                }
                assertTrue("Retry did not reach the widget", retried.await() != null)
            }
        }
    }

    @Test
    fun loadErrorIsOneRetryTargetWhenSmallAndExplainsItselfWhenRoomy() {
        val small = error(DpSize(130.dp, 102.dp))
        assertEquals(setOf("retry"), small.descriptions())
        assertFalse("Retry" in small.texts())

        val roomy = error(DpSize(276.dp, 220.dp))
        assertEquals(setOf("title", "Retry", "Open"), roomy.texts())
    }

    @Test
    fun theRealWidgetComposesAtItsDropSize() {
        val root = inflate(runBlocking { ToolbarWidget().compose(context, size = DpSize(130.dp, 220.dp)) })
        assertTrue(root.descriptions().isNotEmpty())
    }

    @Test
    fun intentsCarryTheirRequests() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val search = WidgetIntents.shell(context, ShellRequest.SEARCH)
        assertEquals(MainActivity::class.java.name, search.component?.className)
        assertEquals("SEARCH", search.getStringExtra(MainActivity.EXTRA_SHELL_REQUEST))
        assertEquals(flags, search.flags and flags)
        assertEquals("SIGN_IN", WidgetIntents.shell(context, ShellRequest.SIGN_IN).getStringExtra(MainActivity.EXTRA_SHELL_REQUEST))
        assertEquals("GAMES", WidgetIntents.tab(context, AppDestination.GAMES).getStringExtra(MainActivity.EXTRA_TAB))
        val composer = WidgetIntents.composer(context)
        assertEquals(ComposerWindowActivity::class.java.name, composer.component?.className)
        assertEquals(ComposerWindowTarget.NewPost, AppWindows.composerTarget(composer.data))
        assertTrue(WidgetIntents.composerOptions(context, size = null) != null)
        assertTrue(WidgetIntents.fallbackOpenApp(context).data != null)
        val retry = WidgetIntents.retry(context)
        assertEquals(ToolbarWidgetReceiver::class.java.name, retry.component?.className)
        assertEquals(WidgetIntents.ACTION_RETRY, retry.action)
    }

    private fun finishAndWait(activity: Activity) {
        instrumentation.waitForIdleSync()
        Thread.sleep(SETTLE_MILLIS)
        instrumentation.runOnMainSync { activity.finish() }
        val deadline = SystemClock.elapsedRealtime() + TAP_TIMEOUT_MILLIS
        while (!activity.isDestroyed && SystemClock.elapsedRealtime() < deadline) Thread.sleep(POLL_MILLIS)
    }

    private fun tap(description: String, signedIn: Boolean, target: Class<out Activity>): Intent {
        val monitor = instrumentation.addMonitor(target.name, null, false)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                val widget = ContentWidget { context ->
                    ToolbarWidgetContent(signedIn, labels, toolbarActions(context, WidgetIntents.composerOptions(context, size = null)))
                }
                val views = runBlocking { widget.compose(context, size = DpSize(276.dp, 456.dp)) }
                scenario.onActivity { activity ->
                    val frame = FrameLayout(activity)
                    frame.addView(views.apply(activity, frame))
                    activity.setContentView(frame)
                    val described = frame.descendants().first { it.contentDescription?.toString() == description }
                    val clickable = generateSequence(described) { it.parent as? View }.firstOrNull { it.hasOnClickListeners() }
                        ?: described.descendants().first { it.hasOnClickListeners() }
                    assertTrue(clickable.performClick())
                }
                val started = monitor.waitForActivityWithTimeout(TAP_TIMEOUT_MILLIS)
                assertTrue("$description did not start ${target.simpleName}", started != null)
                val intent = Intent(started.intent)
                finishAndWait(started)
                return intent
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun tappingSearchStartsTheMainWindowWithASearchRequest() {
        val intent = tap("search", signedIn = true, target = MainActivity::class.java)
        assertEquals("SEARCH", intent.getStringExtra(MainActivity.EXTRA_SHELL_REQUEST))
    }

    @Test
    fun tappingNotificationsStartsTheMainWindowOnThatTab() {
        val intent = tap("notifications", signedIn = true, target = MainActivity::class.java)
        assertEquals("NOTIFICATIONS", intent.getStringExtra(MainActivity.EXTRA_TAB))
    }

    @Test
    fun tappingNewPostStartsTheComposerWindow() {
        val session = (context.applicationContext as ZenithApplication).container.sessionService
        val state = runBlocking { withTimeoutOrNull(TAP_TIMEOUT_MILLIS) { session.state.first { it != SessionState.Restoring } } }
        val fallback = if (state is SessionState.SignedIn) null else instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            val intent = tap("post", signedIn = true, target = ComposerWindowActivity::class.java)
            assertEquals(ComposerWindowTarget.NewPost, AppWindows.composerTarget(intent.data))
            if (fallback != null) {
                val main = fallback.waitForActivityWithTimeout(TAP_TIMEOUT_MILLIS)
                assertTrue("a stale New post tap while signed out did not open the main window", main != null)
                finishAndWait(main)
            }
        } finally {
            fallback?.let(instrumentation::removeMonitor)
        }
    }

    @Test
    fun tappingSignInStartsTheMainWindowWithASignInRequest() {
        val intent = tap("sign in", signedIn = false, target = MainActivity::class.java)
        assertEquals("SIGN_IN", intent.getStringExtra(MainActivity.EXTRA_SHELL_REQUEST))
    }

    @Test
    fun compositionErrorFallbackAndPreviewLayoutsInflate() {
        val fallback = inflate(RemoteViews(context.packageName, R.layout.widget_toolbar_error))
        assertEquals(setOf(context.getString(R.string.widget_error_fallback)), fallback.texts())
        val preview = inflate(RemoteViews(context.packageName, R.layout.widget_toolbar_preview))
        assertTrue(preview.descendants().size > 5)
    }
}

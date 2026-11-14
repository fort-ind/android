package com.fortind.zenith.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
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
import com.fortind.zenith.R
import com.fortind.zenith.settings.AppIcon
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val SETTLE_MILLIS = 1_500L
private const val COPY_TIMEOUT_SECONDS = 5L

@RunWith(AndroidJUnit4::class)
class ToolbarWidgetGalleryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val holdMillis = arguments.getString("holdMillis")?.toLong() ?: 0L
    private val wide = arguments.getString("wide") == "true"
    private val saveAs = arguments.getString("saveAs")

    private class ContentWidget(private val content: @Composable (Context) -> Unit) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact

        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent { WidgetTheme(AppIcon.DEFAULT) { content(context) } }
        }
    }

    private class Tile(val size: DpSize, val views: RemoteViews)

    private fun toolbar(width: Int, height: Int, signedIn: Boolean): Tile {
        val size = DpSize(width.dp, height.dp)
        val widget = ContentWidget { context ->
            ToolbarWidgetContent(signedIn, toolbarLabels(context), toolbarActions(context, composerOptions = null))
        }
        return Tile(size, runBlocking { widget.compose(context, size = size) })
    }

    private fun error(width: Int, height: Int): Tile {
        val size = DpSize(width.dp, height.dp)
        val widget = ContentWidget { context ->
            val openApp = actionStartActivity(WidgetIntents.openApp(context))
            WidgetLoadError(errorLabels(context), onRetry = openApp, onOpenApp = openApp)
        }
        return Tile(size, runBlocking { widget.compose(context, size = size) })
    }

    private fun layout(width: Int, height: Int, layoutId: Int) =
        Tile(DpSize(width.dp, height.dp), RemoteViews(context.packageName, layoutId))

    private fun show(rows: List<List<Tile>>) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var tiles = 0
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                fun px(dp: Float) = (dp * density).roundToInt()
                val column = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setBackgroundColor(Color.GRAY)
                    setPadding(px(8f), px(40f), px(8f), px(8f))
                }
                rows.forEach { row ->
                    val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    row.forEach { tile ->
                        val frame = FrameLayout(activity)
                        val inflated: View = tile.views.apply(activity, frame)
                        frame.addView(inflated, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        val params = LinearLayout.LayoutParams(px(tile.size.width.value), px(tile.size.height.value))
                        params.setMargins(px(6f), px(6f), px(6f), px(6f))
                        line.addView(frame, params)
                        tiles++
                    }
                    column.addView(line, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                activity.setContentView(column)
            }
            assertTrue(tiles > 0)
            saveAs?.let { name ->
                Thread.sleep(SETTLE_MILLIS)
                scenario.onActivity { activity -> capture(activity, name) }
            }
            Thread.sleep(holdMillis)
        }
    }

    private fun capture(activity: ComponentActivity, name: String) {
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        val thread = HandlerThread("widget-gallery-copy").apply { start() }
        PixelCopy.request(activity.window, bitmap, { code ->
            result = code
            copied.countDown()
        }, Handler(thread.looper))
        val hardware = copied.await(COPY_TIMEOUT_SECONDS, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS
        thread.quitSafely()
        if (!hardware) root.draw(Canvas(bitmap))
        val folder = File(activity.cacheDir, "widget-gallery").apply { mkdirs() }
        File(folder, if (hardware) "$name.png" else "$name-software.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun signedIn() = show(
        if (wide) {
            listOf(
                listOf(toolbar(57, 102, true), toolbar(130, 102, true), toolbar(276, 102, true), toolbar(349, 102, true)),
                listOf(toolbar(269, 51, true), toolbar(150, 150, true), toolbar(411, 183, true)),
                listOf(toolbar(130, 220, true), toolbar(203, 220, true), toolbar(276, 338, true), toolbar(57, 338, true)),
            )
        } else {
            listOf(
                listOf(toolbar(57, 102, true), toolbar(130, 102, true), toolbar(150, 150, true)),
                listOf(toolbar(276, 102, true)),
                listOf(toolbar(349, 102, true)),
                listOf(toolbar(269, 51, true)),
                listOf(toolbar(130, 220, true), toolbar(203, 220, true)),
            )
        },
    )

    @Test
    fun signedInLarge() = show(
        listOf(
            listOf(toolbar(276, 338, true), toolbar(57, 338, true)),
            listOf(toolbar(380, 183, true)),
        ),
    )

    @Test
    fun signedOut() = show(
        listOf(
            listOf(toolbar(57, 102, false), toolbar(130, 102, false), toolbar(150, 150, false)),
            listOf(toolbar(276, 102, false)),
            listOf(toolbar(269, 51, false)),
            listOf(toolbar(130, 220, false), toolbar(203, 220, false)),
            listOf(toolbar(380, 183, false)),
        ),
    )

    @Test
    fun errorsAndFallbacks() = show(
        listOf(
            listOf(error(57, 102), error(130, 102)),
            listOf(error(276, 220)),
            listOf(layout(180, 150, R.layout.widget_toolbar_error), layout(180, 180, R.layout.widget_toolbar_preview)),
        ),
    )
}

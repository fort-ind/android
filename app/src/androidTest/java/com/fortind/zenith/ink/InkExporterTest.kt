package com.fortind.zenith.ink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.net.toUri
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cacheDir = File(context.cacheDir, "ink-exporter-test")
    private val exporter = InkExporter({ cacheDir })
    private val sheet = SheetGeometry.drawing(SheetShape.Landscape)
    private val pixels = SheetGeometry.drawingExport(sheet)

    @After
    fun cleanUp() {
        cacheDir.deleteRecursively()
    }

    private fun stroke(tool: InkTool, color: InkColor, fromX: Float, fromY: Float, toX: Float, toY: Float): Stroke {
        val brush = checkNotNull(InkBrushes.brushFor(tool, ToolStyle(color, InkSize.Medium)))
        val inputs = MutableStrokeInputBatch()
        val steps = 30
        for (step in 0..steps) {
            val fraction = step / steps.toFloat()
            inputs.add(
                InputToolType.STYLUS,
                fromX + (toX - fromX) * fraction,
                fromY + (toY - fromY) * fraction,
                step * 8L,
            )
        }
        return Stroke(brush, inputs.toImmutable())
    }

    private fun export(strokes: List<Stroke>, backdrop: Int?): Bitmap {
        val picture = InkPicture(strokes, sheet, pixels, backdrop, photo = null, jpeg = false)
        val media = runBlocking { exporter.export(picture, "test") }
        assertEquals("test.png", media.name)
        assertEquals("image/png", media.mimeType)
        assertTrue(media.sizeBytes > 0)
        val file = File(checkNotNull(media.uri.toUri().path))
        return checkNotNull(BitmapFactory.decodeFile(file.path))
    }

    private fun Bitmap.at(sheetX: Float, sheetY: Float): Int =
        getPixel((sheetX * pixels.width / sheet.width).toInt(), (sheetY * pixels.height / sheet.height).toInt())

    @Test
    fun theExportHasTheDrawingSize() {
        val bitmap = export(emptyList(), Color.WHITE)
        assertEquals(1600, bitmap.width)
        assertEquals(1200, bitmap.height)
    }

    @Test
    fun aPenStrokeIsDrawnWhereItWasMade() {
        val bitmap = export(listOf(stroke(InkTool.Pen, InkColor.Red, 100f, 300f, 700f, 300f)), Color.WHITE)
        val onStroke = bitmap.at(400f, 300f)
        assertTrue("red channel", Color.red(onStroke) > 0xC0)
        assertTrue("green channel", Color.green(onStroke) < 0x60)
        assertEquals(Color.WHITE, bitmap.at(400f, 200f))
    }

    @Test
    fun whiteInkShowsOnBlack() {
        val bitmap = export(
            listOf(InkTool.Pen, InkTool.Marker).mapIndexed { index, tool ->
                stroke(tool, InkColor.White, 100f, 200f + index * 200f, 700f, 200f + index * 200f)
            },
            Color.BLACK,
        )
        listOf(200f, 400f).forEach { y ->
            val onStroke = bitmap.at(400f, y)
            assertTrue("white at $y was ${Integer.toHexString(onStroke)}", Color.red(onStroke) > 0xE0 && Color.green(onStroke) > 0xE0)
        }
    }

    @Test
    fun aHighlighterOverBlackIsStillVisible() {
        val bitmap = export(listOf(stroke(InkTool.Highlighter, InkColor.Yellow, 100f, 300f, 700f, 300f)), Color.BLACK)
        val onStroke = bitmap.at(400f, 300f)
        assertTrue("red channel ${Color.red(onStroke)}", Color.red(onStroke) > 0x30)
        assertTrue("green channel ${Color.green(onStroke)}", Color.green(onStroke) > 0x30)
    }

    @Test
    fun aHighlighterMultipliesOverInkOnWhite() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val pen = stroke(InkTool.Pen, InkColor.Black, 400f, 100f, 400f, 500f)
        val highlighter = stroke(InkTool.Highlighter, InkColor.Yellow, 100f, 300f, 700f, 300f)
        val bitmap = export(listOf(pen, highlighter), Color.WHITE)
        val crossing = bitmap.at(400f, 300f)
        assertTrue("crossing stays dark ${Integer.toHexString(crossing)}", Color.red(crossing) < 0x30 && Color.green(crossing) < 0x30)
        val beside = bitmap.at(250f, 300f)
        assertTrue("highlighter tints white", Color.blue(beside) < 0xE8 && Color.red(beside) > 0xE0)
    }

    @Test
    fun aTransparentBackdropKeepsItsAlpha() {
        val bitmap = export(listOf(stroke(InkTool.Pen, InkColor.Black, 100f, 300f, 700f, 300f)), null)
        assertTrue(bitmap.hasAlpha())
        assertEquals(0, Color.alpha(bitmap.at(400f, 100f)))
        assertTrue(Color.alpha(bitmap.at(400f, 300f)) > 0xE0)
    }

    @Test
    fun aHighlighterOnTransparentKeepsItsColor() {
        val bitmap = export(listOf(stroke(InkTool.Highlighter, InkColor.Yellow, 100f, 300f, 700f, 300f)), null)
        val onStroke = bitmap.at(400f, 300f)
        assertTrue("alpha ${Color.alpha(onStroke)}", Color.alpha(onStroke) in 0x40..0xB0)
        assertTrue(Color.red(onStroke) > 0xC0)
    }
}

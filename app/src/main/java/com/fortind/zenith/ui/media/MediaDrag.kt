package com.fortind.zenith.ui.media

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.scale
import com.fortind.zenith.media.RemoteMediaProvider
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.timeline.NoteFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

object AppDrag

fun DragAndDropEvent.startedInThisWindow(): Boolean = toAndroidDragEvent().localState === AppDrag

class DragPayload(val clipData: ClipData, val flags: Int)

fun isDraggableMedia(file: NoteFile): Boolean =
    (file.kind == MediaKind.Image || file.kind == MediaKind.Video) && file.url.startsWith("https://")

fun mediaPayload(context: Context, file: NoteFile): DragPayload? {
    if (!isDraggableMedia(file)) return null
    val target = saveTargetFor(file)
    val uri = RemoteMediaProvider.uriFor(context, file.url, file.mimeType, target.fileName)
    val description = ClipDescription(target.fileName, arrayOf(file.mimeType, ClipDescription.MIMETYPE_TEXT_PLAIN))
    return DragPayload(ClipData(description, ClipData.Item(file.url, null, uri)), View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
}

fun linkPayload(url: String, label: String?): DragPayload = DragPayload(ClipData.newPlainText(label ?: url, url), View.DRAG_FLAG_GLOBAL)

private fun DragPayload.transfer() = DragAndDropTransferData(clipData = clipData, localState = AppDrag, flags = flags)

fun Modifier.mediaDragSource(context: Context, file: NoteFile, enabled: Boolean = true): Modifier =
    if (enabled && isDraggableMedia(file)) dragAndDropSource(transferData = { _ -> mediaPayload(context, file)?.transfer() }) else this

fun Modifier.linkDragSource(url: String, label: String?): Modifier = dragAndDropSource(transferData = { _ -> linkPayload(url, label).transfer() })

@Stable
class TouchDragStarter internal constructor(
    private val view: View,
    private val scope: CoroutineScope,
    internal val layer: GraphicsLayer,
) {
    fun start(payload: DragPayload?) {
        if (payload == null) return
        scope.launch {
            val shadow = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull()
            view.startDragAndDrop(payload.clipData, BitmapShadow(shadow), AppDrag, payload.flags)
        }
    }
}

@Composable
fun rememberTouchDragStarter(): TouchDragStarter {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    return remember(view, scope, layer) { TouchDragStarter(view, scope, layer) }
}

fun Modifier.dragShadowSource(starter: TouchDragStarter): Modifier = drawWithContent {
    starter.layer.record { this@drawWithContent.drawContent() }
    drawLayer(starter.layer)
}

private class BitmapShadow(source: Bitmap?) : View.DragShadowBuilder() {
    private val bitmap = source?.let(::fit)

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        val width = bitmap?.width ?: FALLBACK_PX
        val height = bitmap?.height ?: FALLBACK_PX
        outShadowSize.set(width, height)
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun fit(source: Bitmap): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= MAX_SHADOW_PX || largest == 0) return source
        val ratio = MAX_SHADOW_PX.toFloat() / largest
        return source.scale((source.width * ratio).toInt().coerceAtLeast(1), (source.height * ratio).toInt().coerceAtLeast(1))
    }
}

private const val MAX_SHADOW_PX = 480
private const val FALLBACK_PX = 96

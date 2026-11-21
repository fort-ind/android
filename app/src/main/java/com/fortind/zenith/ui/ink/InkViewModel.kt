package com.fortind.zenith.ui.ink

import android.graphics.Bitmap
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.ink.InkColor
import com.fortind.zenith.ink.InkEraser
import com.fortind.zenith.ink.InkExporter
import com.fortind.zenith.ink.InkHistory
import com.fortind.zenith.ink.InkInputPolicy
import com.fortind.zenith.ink.InkPicture
import com.fortind.zenith.ink.InkSize
import com.fortind.zenith.ink.InkStyles
import com.fortind.zenith.ink.InkTool
import com.fortind.zenith.ink.MarkupPhotoLoader
import com.fortind.zenith.ink.PixelSize
import com.fortind.zenith.ink.SheetBackground
import com.fortind.zenith.ink.SheetGeometry
import com.fortind.zenith.ink.SheetShape
import com.fortind.zenith.ink.SheetSize
import com.fortind.zenith.ink.ToolStyle
import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.settings.SettingsStore
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InkTarget {
    data object Drawing : InkTarget

    data class Markup(val attachmentId: String, val uri: String, val name: String, val mimeType: String) : InkTarget
}

enum class InkPhase { Loading, Ready, LoadFailed, Exporting }

enum class InkPanel { Style, More }

data class InkUiState(
    val target: InkTarget,
    val phase: InkPhase,
    val shape: SheetShape,
    val background: SheetBackground,
    val strokes: List<Stroke> = emptyList(),
    val tool: InkTool = InkTool.Pen,
    val styles: Map<InkTool, ToolStyle> = InkStyles.defaults,
    val photo: Bitmap? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val confirmDiscard: Boolean = false,
    val penSeen: Boolean = false,
    val panel: InkPanel? = null,
) {
    val isMarkup: Boolean get() = target is InkTarget.Markup

    val sheet: SheetSize get() = photo?.let { SheetGeometry.markup(it.width, it.height) } ?: SheetGeometry.drawing(shape)

    val style: ToolStyle get() = styles.getValue(tool)

    val hasInk: Boolean get() = strokes.isNotEmpty()

    fun backdropArgb(themeArgb: Int): Int? = if (photo != null) null else background.argb(themeArgb)
}

sealed interface InkResult {
    data class Drawn(val media: LocalMedia) : InkResult

    data class MarkedUp(val attachmentId: String, val media: LocalMedia) : InkResult

    data object Failed : InkResult
}

class InkViewModel(
    private val settings: SettingsStore,
    private val exporter: InkExporter,
    private val photos: MarkupPhotoLoader,
) : ViewModel() {
    private val _state = MutableStateFlow<InkUiState?>(null)
    val state: StateFlow<InkUiState?> = _state.asStateFlow()

    val policy: StateFlow<InkInputPolicy> = settings.settings
        .map { InkInputPolicy(drawWithMouse = it.inkDrawWithMouse, touchStopsAfterPen = it.inkTouchStopsAfterPen) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, InkInputPolicy())

    private val _results = Channel<InkResult>(Channel.BUFFERED)
    val results: Flow<InkResult> = _results.receiveAsFlow()

    private val history = InkHistory<List<Stroke>>(emptyList())
    private val eraser = InkEraser()
    private var erasing = false
    private var work: Job? = null

    fun openDrawing() {
        work?.cancel()
        history.reset(emptyList())
        work = viewModelScope.launch {
            val saved = settings.settings.first()
            _state.value = InkUiState(
                target = InkTarget.Drawing,
                phase = InkPhase.Ready,
                shape = saved.drawingShape,
                background = saved.drawingBackground,
                styles = InkStyles.forBackground(InkStyles.defaults, saved.drawingBackground),
            )
        }
    }

    fun openMarkup(attachmentId: String, uri: String, name: String, mimeType: String, fallbackUri: String? = null) {
        work?.cancel()
        history.reset(emptyList())
        val target = InkTarget.Markup(attachmentId, uri, name, mimeType)
        _state.value = InkUiState(
            target = target,
            phase = InkPhase.Loading,
            shape = SheetShape.Landscape,
            background = SheetBackground.Transparent,
        )
        work = viewModelScope.launch {
            val photo = photos.load(listOfNotNull(uri, fallbackUri))
            _state.update { current ->
                if (current?.target != target) {
                    current
                } else if (photo == null) {
                    current.copy(phase = InkPhase.LoadFailed)
                } else {
                    current.copy(phase = InkPhase.Ready, photo = photo)
                }
            }
        }
    }

    fun selectTool(tool: InkTool) {
        _state.update { it?.copy(tool = tool) }
    }

    fun setColor(color: InkColor) {
        _state.update { state ->
            state?.copy(styles = state.styles + (state.tool to state.style.copy(color = color)))
        }
    }

    fun setSize(size: InkSize) {
        _state.update { state ->
            state?.copy(styles = state.styles + (state.tool to state.style.copy(size = size)))
        }
    }

    fun setShape(shape: SheetShape) {
        _state.update { it?.copy(shape = shape) }
        viewModelScope.launch { settings.setDrawingShape(shape) }
    }

    fun setBackground(background: SheetBackground) {
        _state.update { it?.copy(background = background, styles = InkStyles.forBackground(it.styles, background)) }
        viewModelScope.launch { settings.setDrawingBackground(background) }
    }

    fun openPanel(panel: InkPanel) {
        _state.update { it?.copy(panel = panel) }
    }

    fun closePanel() {
        _state.update { it?.copy(panel = null) }
    }

    fun notePen() {
        if (_state.value?.penSeen == false) _state.update { it?.copy(penSeen = true) }
    }

    fun onStrokesFinished(strokes: List<Stroke>) {
        val state = _state.value ?: return
        commit(state.strokes + strokes)
    }

    fun eraseStart(x: Float, y: Float) {
        erasing = true
        eraser.start(x, y)
        erase(x, y)
    }

    fun erase(x: Float, y: Float) {
        val state = _state.value ?: return
        val radius = InkStyles.width(InkTool.Eraser, state.styles.getValue(InkTool.Eraser).size)
        val kept = eraser.erase(state.strokes, x, y, radius)
        if (kept !== state.strokes) _state.value = state.copy(strokes = kept)
    }

    fun eraseEnd() {
        if (!erasing) return
        erasing = false
        eraser.end()
        val strokes = _state.value?.strokes ?: return
        if (strokes != history.current) commit(strokes)
    }

    fun clearAll() {
        if (_state.value?.hasInk == true) commit(emptyList())
    }

    fun undo() {
        if (history.canUndo) show(history.undo())
    }

    fun redo() {
        if (history.canRedo) show(history.redo())
    }

    fun requestCancel() {
        val state = _state.value ?: return
        when {
            state.confirmDiscard -> keepDrawing()
            state.panel != null -> closePanel()
            state.phase == InkPhase.Exporting -> Unit
            state.hasInk -> _state.update { it?.copy(confirmDiscard = true) }
            else -> close()
        }
    }

    fun keepDrawing() {
        _state.update { it?.copy(confirmDiscard = false) }
    }

    fun close() {
        work?.cancel()
        history.reset(emptyList())
        _state.value = null
    }

    fun attach(displayName: String, themeArgb: Int) {
        val state = _state.value ?: return
        if (state.phase != InkPhase.Ready) return
        val target = state.target
        val photo = state.photo
        val picture = InkPicture(
            strokes = state.strokes,
            sheet = state.sheet,
            pixels = photo?.let { PixelSize(it.width, it.height) } ?: SheetGeometry.drawingExport(state.sheet),
            backgroundArgb = state.backdropArgb(themeArgb),
            photo = photo,
            jpeg = target is InkTarget.Markup && target.mimeType != PNG_MIME,
        )
        _state.value = state.copy(phase = InkPhase.Exporting)
        work = viewModelScope.launch {
            val media = try {
                exporter.export(picture, displayName)
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
            if (media == null) {
                _state.update { it?.copy(phase = InkPhase.Ready) }
                _results.send(InkResult.Failed)
                return@launch
            }
            _results.send(
                when (target) {
                    InkTarget.Drawing -> InkResult.Drawn(media)
                    is InkTarget.Markup -> InkResult.MarkedUp(target.attachmentId, media)
                },
            )
            close()
        }
    }

    private fun commit(strokes: List<Stroke>) {
        history.push(strokes)
        show(strokes)
    }

    private fun show(strokes: List<Stroke>) {
        _state.update { it?.copy(strokes = strokes, canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    companion object {
        private const val PNG_MIME = "image/png"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                InkViewModel(
                    settings = app.container.settingsStore,
                    exporter = InkExporter({ app.cacheDir }),
                    photos = MarkupPhotoLoader(app),
                )
            }
        }
    }
}

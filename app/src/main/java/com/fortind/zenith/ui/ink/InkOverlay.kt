package com.fortind.zenith.ui.ink

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.strokes.Stroke
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import com.fortind.zenith.R
import com.fortind.zenith.ink.InkAction
import com.fortind.zenith.ink.InkBrushes
import com.fortind.zenith.ink.InkInputPolicy
import com.fortind.zenith.ink.InkPainter
import com.fortind.zenith.ink.InkPointer
import com.fortind.zenith.ink.InkStyles
import com.fortind.zenith.ink.InkTool
import com.fortind.zenith.ink.SheetBackground
import com.fortind.zenith.ink.SheetGeometry
import com.fortind.zenith.ui.composer.ModalOverlay
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.handCursor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InkOverlay(state: InkUiState, policy: InkInputPolicy, viewModel: InkViewModel) {
    val focus = remember { FocusRequester() }
    val title = stringResource(if (state.isMarkup) R.string.ink_mark_up else R.string.ink_draw)
    val themeArgb = MaterialTheme.colorScheme.primary.toArgb()
    val drawingName = stringResource(R.string.ink_drawing_name, remember { SimpleDateFormat(NAME_STAMP, Locale.US).format(Date()) })
    val markupName = (state.target as? InkTarget.Markup)?.let {
        stringResource(R.string.ink_markup_name, it.name.substringBeforeLast('.'))
    }
    val attach = { viewModel.attach(markupName ?: drawingName, themeArgb) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = title }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val command = event.isCtrlPressed || event.isMetaPressed
                when {
                    command && event.key == Key.Z && event.isShiftPressed -> viewModel.redo()
                    command && event.key == Key.Z -> viewModel.undo()
                    command && event.key == Key.Y -> viewModel.redo()
                    command && event.key == Key.Enter && state.phase == InkPhase.Ready -> attach()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
        val vertical = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ||
            !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(modifier = Modifier.fillMaxSize()) {
                InkTopBar(
                    state = state,
                    title = title,
                    onCancel = viewModel::requestCancel,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onMore = { viewModel.openPanel(InkPanel.More) },
                    onAttach = attach,
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.phase) {
                        InkPhase.Loading -> CenteredMessage(R.string.ink_loading, busy = true)
                        InkPhase.LoadFailed -> CenteredMessage(R.string.ink_load_failed, busy = false)
                        InkPhase.Ready, InkPhase.Exporting -> InkWorkspace(state, policy, viewModel, vertical)
                    }
                }
            }
            state.panel?.let { panel ->
                val actions = remember(viewModel) {
                    InkPanelActions(
                        onColor = viewModel::setColor,
                        onSize = viewModel::setSize,
                        onBackground = viewModel::setBackground,
                        onShape = viewModel::setShape,
                        onClearAll = viewModel::clearAll,
                        onClose = viewModel::closePanel,
                    )
                }
                InkPanelLayer(panel, state, actions, vertical)
            }
        }
    }
    DiscardInkPrompt(
        visible = state.confirmDiscard,
        onDiscard = viewModel::close,
        onKeep = viewModel::keepDrawing,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CenteredMessage(text: Int, busy: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (busy) CircularWavyProgressIndicator()
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InkTopBar(
    state: InkUiState,
    title: String,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMore: () -> Unit,
    onAttach: () -> Unit,
) {
    val ready = state.phase == InkPhase.Ready
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(TopBarHeight)) {
        val showTitle = maxWidth >= TitleMinWidth
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(end = Gutter),
        ) {
            TooltipIconButton(label = stringResource(R.string.ink_cancel), icon = Icons.Default.Close, onClick = onCancel)
            if (showTitle) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            TooltipIconButton(
                label = stringResource(R.string.ink_undo),
                icon = Icons.AutoMirrored.Filled.Undo,
                onClick = onUndo,
                enabled = ready && state.canUndo,
            )
            TooltipIconButton(
                label = stringResource(R.string.ink_redo),
                icon = Icons.AutoMirrored.Filled.Redo,
                onClick = onRedo,
                enabled = ready && state.canRedo,
            )
            TooltipIconButton(
                label = stringResource(R.string.ink_more),
                icon = Icons.Default.MoreVert,
                onClick = onMore,
                enabled = ready,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onAttach,
                enabled = ready,
                modifier = Modifier.handCursor(ready),
            ) {
                Text(stringResource(R.string.ink_attach))
            }
        }
    }
}

@Composable
private fun InkWorkspace(state: InkUiState, policy: InkInputPolicy, viewModel: InkViewModel, vertical: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        InkToolbar(
            state = state,
            onSelectTool = viewModel::selectTool,
            onOpenStyle = { viewModel.openPanel(InkPanel.Style) },
            vertical = vertical,
            modifier = Modifier
                .align(if (vertical) Alignment.CenterStart else Alignment.BottomCenter)
                .padding(16.dp),
        )
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (vertical) ToolbarClearance else SheetMargin,
                    end = SheetMargin,
                    top = SheetMargin,
                    bottom = if (vertical) SheetMargin else ToolbarClearance,
                ),
        ) {
            val density = LocalDensity.current
            val sheet = state.sheet
            val scale = with(density) { SheetGeometry.fitScale(sheet, maxWidth.toPx(), maxHeight.toPx()) }
            if (scale > 0f) {
                val width = with(density) { (sheet.width * scale).toDp() }
                val height = with(density) { (sheet.height * scale).toDp() }
                InkSheet(state = state, policy = policy, viewModel = viewModel, scale = scale, modifier = Modifier.size(width, height))
            }
        }
    }
}

@Composable
private fun InkSheet(
    state: InkUiState,
    policy: InkInputPolicy,
    viewModel: InkViewModel,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val painter = remember { InkPainter() }
    var pending by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    val strokes = state.strokes
    SideEffect {
        if (pending.isNotEmpty()) pending = pending.filter { candidate -> strokes.none { it === candidate } }
    }
    val visible = remember(strokes, pending) {
        if (pending.isEmpty()) strokes else strokes + pending.filter { candidate -> strokes.none { it === candidate } }
    }
    val brush = remember(state.tool, state.style) { InkBrushes.brushFor(state.tool, state.style) }
    val inputMatrix = remember(scale) { Matrix().apply { scale(1f / scale, 1f / scale) } }
    val currentBrush by rememberUpdatedState(brush)
    val routing = rememberUpdatedState(InputRouting(policy, state.tool, state.penSeen, scale))
    val themeColor = MaterialTheme.colorScheme.primary
    val multiplyHighlighters = InkStyles.highlighterMultiplies(state.backdropArgb(themeColor.toArgb()))
    val sheetLabel = stringResource(R.string.ink_sheet)
    Box(
        modifier = modifier
            .clipToBounds()
            .semantics { contentDescription = sheetLabel }
            .pointerHoverIcon(PointerIcon.Crosshair)
            .pointerInput(viewModel) { routeInkInput(routing, viewModel) },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawSheetBackground(state.background, themeColor, state.photo != null) }
        state.photo?.let { photo ->
            Image(
                bitmap = remember(photo) { photo.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            painter.draw(drawContext.canvas.nativeCanvas, visible, scale, multiplyHighlighters)
        }
        InProgressStrokes(
            defaultBrush = brush,
            nextBrush = { currentBrush },
            pointerEventToWorldTransform = inputMatrix,
            onStrokesFinished = { finished ->
                pending = pending + finished
                viewModel.onStrokesFinished(finished)
            },
        )
    }
}

private class InputRouting(val policy: InkInputPolicy, val tool: InkTool, val penSeen: Boolean, val scale: Float)

private suspend fun PointerInputScope.routeInkInput(
    routing: State<InputRouting>,
    viewModel: InkViewModel,
) {
    awaitPointerEventScope {
        val actions = HashMap<PointerId, InkAction>()
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val now = routing.value
            event.changes.fastForEach { change ->
                val pointer = classify(change, event)
                if (now.policy.isPen(pointer)) viewModel.notePen()
                val world = change.position / now.scale
                if (change.changedToDownIgnoreConsumed()) {
                    val action = now.policy.actionFor(pointer, now.tool, now.penSeen || now.policy.isPen(pointer))
                    actions[change.id] = action
                    if (action == InkAction.Erase) viewModel.eraseStart(world.x, world.y)
                } else if (actions[change.id] == InkAction.Erase && change.pressed) {
                    viewModel.erase(world.x, world.y)
                }
                val action = actions[change.id]
                if (action == InkAction.Erase || action == InkAction.Ignore) change.consume()
                if (change.changedToUpIgnoreConsumed() || (!change.pressed && action != null)) {
                    if (actions.remove(change.id) == InkAction.Erase) viewModel.eraseEnd()
                }
            }
        }
    }
}

private fun classify(change: PointerInputChange, event: PointerEvent): InkPointer = when (change.type) {
    PointerType.Eraser -> InkPointer.StylusEraser
    PointerType.Stylus -> {
        val buttons = event.motionEvent?.buttonState ?: 0
        if (buttons and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
            InkPointer.StylusButton
        } else {
            InkPointer.Stylus
        }
    }
    PointerType.Mouse -> {
        val buttons = event.buttons
        if (buttons.isSecondaryPressed || buttons.isTertiaryPressed || !buttons.isPrimaryPressed && change.pressed) {
            InkPointer.MouseOtherButton
        } else {
            InkPointer.Mouse
        }
    }
    else -> InkPointer.Touch
}

private operator fun Offset.div(value: Float): Offset = Offset(x / value, y / value)

private fun DrawScope.drawSheetBackground(background: SheetBackground, theme: Color, hasPhoto: Boolean) {
    if (hasPhoto) return
    when (background) {
        SheetBackground.White -> drawRect(Color.White)
        SheetBackground.Black -> drawRect(Color.Black)
        SheetBackground.Theme -> drawRect(theme)
        SheetBackground.Transparent -> {
            val cell = CheckerCell.toPx()
            drawRect(CheckerLight)
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else cell
                while (x < size.width) {
                    drawRect(CheckerDark, topLeft = Offset(x, y), size = Size(cell, cell))
                    x += cell * 2
                }
                y += cell
                row++
            }
        }
    }
}

@Composable
internal fun InkPanelLayer(panel: InkPanel, state: InkUiState, actions: InkPanelActions, vertical: Boolean) {
    val focus = remember { FocusRequester() }
    val title = stringResource(if (panel == InkPanel.More) R.string.ink_more else R.string.ink_style)
    LaunchedEffect(panel) { focus.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { actions.onClose() } },
    ) {
        val alignment = when {
            panel == InkPanel.More -> Alignment.TopEnd
            vertical -> Alignment.CenterStart
            else -> Alignment.BottomCenter
        }
        val placement = when {
            panel == InkPanel.More -> Modifier.padding(top = TopBarHeight, end = Gutter)
            vertical -> Modifier.padding(start = ToolbarClearance, top = TopBarHeight)
            else -> Modifier.padding(bottom = ToolbarClearance, top = TopBarHeight)
        }
        Surface(
            color = MenuDefaults.containerColor,
            shape = PanelShape,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
            modifier = Modifier
                .align(alignment)
                .then(placement)
                .widthIn(max = PanelMaxWidth)
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { paneTitle = title }
                .focusRequester(focus)
                .focusGroup(),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(PanelInset)) {
                when (panel) {
                    InkPanel.Style -> StylePanel(state, actions)
                    InkPanel.More -> MorePanel(state, actions)
                }
            }
        }
    }
}

@Composable
private fun DiscardInkPrompt(visible: Boolean, onDiscard: () -> Unit, onKeep: () -> Unit) {
    val title = stringResource(R.string.ink_discard_title)
    ModalOverlay(visible = visible, title = title, onDismiss = onKeep) {
        val keepFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { keepFocus.requestFocus() }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            TextButton(onClick = onDiscard, modifier = Modifier.handCursor()) { Text(stringResource(R.string.ink_discard)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onKeep, modifier = Modifier.focusRequester(keepFocus).handCursor()) {
                Text(stringResource(R.string.ink_keep_drawing))
            }
        }
    }
}

private const val NAME_STAMP = "yyyy-MM-dd HH.mm.ss"
private val Gutter = 16.dp
private val SheetMargin = Gutter
private val TitleMinWidth = 392.dp
private val TopBarHeight = 64.dp
private val PanelMaxWidth = 320.dp
private val ToolbarClearance = 96.dp
private val CheckerCell = 8.dp
internal val CheckerLight = Color(0xFFFFFFFF)
internal val CheckerDark = Color(0xFFE0E0E0)

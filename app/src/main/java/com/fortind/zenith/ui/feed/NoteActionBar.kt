package com.fortind.zenith.ui.feed

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.fortind.zenith.R
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.timeline.canRenote
import com.fortind.zenith.timeline.renoteVisibilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val COUNT_CACHE_SIZE = 64
private const val TIP_DELAY_MS = 600L
private const val PRESS_ALPHA = 0.12f
private const val HOVER_ALPHA = 0.08f
private const val DISABLED_ALPHA = 0.38f
private const val NONE = -1
private const val REPLY = 0
private const val RENOTE = 1
private const val REACT = 2
private const val MORE = 3
private const val CELLS = 4
private val CellMinSize = 48.dp
private val CellPadding = 12.dp
private val GlyphSize = 20.dp
private val GlyphGap = 4.dp
private val FocusStroke = 2.dp
private val TipGap = 8.dp

private val RenoteTargets = listOf(
    Triple(Visibility.Public, R.string.note_renote_public, Icons.Default.Public),
    Triple(Visibility.Home, R.string.note_renote_home, Icons.Default.Home),
    Triple(Visibility.Followers, R.string.note_renote_followers, Icons.Default.Lock),
)

@Immutable
class ActionGlyph(private val paths: List<Path>, private val viewport: Float, private val mirrors: Boolean) {
    fun DrawScope.draw(x: Float, y: Float, size: Float, color: Color) {
        val scale = size / viewport
        val flip = mirrors && layoutDirection == LayoutDirection.Rtl
        withTransform({
            if (flip) {
                translate(x + size, y)
                scale(-scale, scale, Offset.Zero)
            } else {
                translate(x, y)
                scale(scale, scale, Offset.Zero)
            }
        }) {
            paths.forEach { drawPath(it, color) }
        }
    }

    companion object {
        fun of(vector: ImageVector): ActionGlyph {
            val paths = ArrayList<Path>()
            fun collect(node: VectorNode) {
                when (node) {
                    is VectorGroup -> node.forEach { collect(it) }
                    is VectorPath -> paths += node.pathData.toPath().also { it.fillType = node.pathFillType }
                }
            }
            collect(vector.root)
            return ActionGlyph(paths, vector.viewportWidth, vector.autoMirror)
        }
    }
}

@Immutable
class NoteActionKit(
    val labels: NoteActionLabels,
    val reply: ActionGlyph,
    val renote: ActionGlyph,
    val react: ActionGlyph,
    val more: ActionGlyph,
    val measurer: TextMeasurer,
    val countStyle: TextStyle,
)

@Composable
fun rememberNoteActionKit(): NoteActionKit {
    val labels = rememberNoteActionLabels()
    val measurer = rememberTextMeasurer(COUNT_CACHE_SIZE)
    val style = MaterialTheme.typography.labelLarge
    return remember(labels, measurer, style) {
        NoteActionKit(
            labels = labels,
            reply = ActionGlyph.of(Icons.AutoMirrored.Outlined.Reply),
            renote = ActionGlyph.of(Icons.Default.Repeat),
            react = ActionGlyph.of(Icons.Outlined.AddReaction),
            more = ActionGlyph.of(Icons.Default.MoreHoriz),
            measurer = measurer,
            countStyle = style,
        )
    }
}

val LocalNoteActionKit = compositionLocalOf<NoteActionKit?> { null }

@Immutable
private class BarOverlay(val cell: Int, val rect: IntRect, val tip: Boolean)

@Immutable
private class BarActions(
    val onCell: (Int, IntRect) -> Unit,
    val onLong: (Int) -> Unit,
    val onTip: (Int, IntRect?) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteActionBar(note: Note, environment: NoteEnvironment, callbacks: NoteCallbacks) {
    val kit = LocalNoteActionKit.current ?: rememberNoteActionKit()
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val signedIn = environment.signedIn
    val renoted = note.id in environment.renoted
    val renoteEnabled = note.canRenote(environment.myUserId) || !signedIn
    var overlay by remember { mutableStateOf<BarOverlay?>(null) }
    val actions = remember(note, callbacks, signedIn, haptics) {
        BarActions(
            onCell = { cell, rect ->
                overlay = null
                when (cell) {
                    REPLY -> callbacks.reply(note)
                    RENOTE -> if (!signedIn) {
                        callbacks.renote(note)
                    } else {
                        callbacks.checkRenoted(note)
                        overlay = BarOverlay(cell, rect, tip = false)
                    }
                    REACT -> callbacks.openReactionPicker(note)
                    MORE -> overlay = BarOverlay(cell, rect, tip = false)
                }
            },
            onLong = { cell ->
                if (cell == REACT) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.reactDefault(note)
                }
            },
            onTip = { cell, rect ->
                val current = overlay
                if (current == null || current.tip) overlay = if (cell == NONE || rect == null) null else BarOverlay(cell, rect, tip = true)
            },
        )
    }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxWidth().then(
            ActionBarElement(
                kit = kit,
                actions = actions,
                replies = note.repliesCount,
                renotes = note.renoteCount,
                renoted = renoted,
                renoteEnabled = renoteEnabled,
                color = colors.onSurfaceVariant,
                renoteColor = when {
                    !renoteEnabled -> colors.onSurface.copy(alpha = DISABLED_ALPHA)
                    renoted -> colors.primary
                    else -> colors.onSurfaceVariant
                },
                accent = colors.primary,
            ),
        ),
    ) {
        val open = overlay ?: return@Box
        val menuOffset = with(density) { DpOffset(open.rect.left.toDp(), 0.dp) }
        when {
            open.tip -> BarTooltip(open, kit.labels, renoted)
            open.cell == RENOTE -> DropdownMenu(expanded = true, onDismissRequest = { overlay = null }, offset = menuOffset) {
                RenoteMenuContent(note, environment, callbacks, kit.labels, renoted) { overlay = null }
            }
            open.cell == MORE -> DropdownMenu(expanded = true, onDismissRequest = { overlay = null }, offset = menuOffset) {
                NoteMenuItems(note, environment, callbacks) { overlay = null }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarTooltip(overlay: BarOverlay, labels: NoteActionLabels, renoted: Boolean) {
    val gap = with(LocalDensity.current) { TipGap.roundToPx() }
    val provider = remember(overlay.rect, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val centered = anchorBounds.left + overlay.rect.center.x - popupContentSize.width / 2
                val x = centered.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
                val above = anchorBounds.top + overlay.rect.top - popupContentSize.height - gap
                val y = if (above >= 0) above else anchorBounds.top + overlay.rect.bottom + gap
                return IntOffset(x, y)
            }
        }
    }
    val label = when (overlay.cell) {
        REPLY -> labels.reply
        RENOTE -> if (renoted) labels.unrenote else labels.renote
        REACT -> labels.react
        else -> labels.more
    }
    Popup(popupPositionProvider = provider) {
        Surface(shape = TooltipDefaults.plainTooltipContainerShape, color = TooltipDefaults.plainTooltipContainerColor) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = TooltipDefaults.plainTooltipContentColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RenoteMenuContent(
    note: Note,
    environment: NoteEnvironment,
    callbacks: NoteCallbacks,
    labels: NoteActionLabels,
    renoted: Boolean,
    dismiss: () -> Unit,
) {
    val allowed = note.renoteVisibilities(environment.myUserId)
    Text(
        text = stringResource(R.string.note_renote_to),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
    RenoteTargets.forEach { (visibility, labelRes, icon) ->
        DropdownMenuItem(
            text = { Text(stringResource(labelRes)) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            onClick = {
                dismiss()
                callbacks.renoteAs(note, visibility)
            },
            enabled = !renoted && visibility in allowed,
            modifier = Modifier.testTag("renote:${visibility.name}"),
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(labels.quote) },
        leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
        onClick = {
            dismiss()
            callbacks.quote(note)
        },
    )
    if (renoted) {
        DropdownMenuItem(
            text = { Text(labels.unrenote) },
            onClick = {
                dismiss()
                callbacks.unrenote(note)
            },
        )
    }
}

private data class ActionBarElement(
    val kit: NoteActionKit,
    val actions: BarActions,
    val replies: Int,
    val renotes: Int,
    val renoted: Boolean,
    val renoteEnabled: Boolean,
    val color: Color,
    val renoteColor: Color,
    val accent: Color,
) : ModifierNodeElement<ActionBarNode>() {
    override fun create() = ActionBarNode(this)

    override fun update(node: ActionBarNode) = node.update(this)
}

private class ActionBarNode(element: ActionBarElement) : DelegatingNode(), LayoutModifierNode, DrawModifierNode, SemanticsModifierNode, KeyInputModifierNode {
    private var spec = element
    private var replyText: TextLayoutResult? = null
    private var renoteText: TextLayoutResult? = null
    private var replyWidth = 0f
    private var renoteWidth = 0f
    private var width = 0
    private var cellPx = 0f
    private var height = 0
    private var pressed = NONE
    private var hovered = NONE
    private var focused = false
    private var focusedCell = REPLY
    private var tipJob: Job? = null

    private val focusTarget = delegate(
        FocusTargetModifierNode(onFocusChange = { _, now ->
            focused = now.isFocused
            if (focused) focusedCell = REPLY
            invalidateDraw()
        }),
    )

    private val touch = delegate(
        SuspendingPointerInputModifierNode(
            PointerInputEventHandler {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val cell = cellAt(down.position)
                    if (cell == NONE || (cell == RENOTE && !spec.renoteEnabled)) return@awaitEachGesture
                    cancelTip()
                    pressed = cell
                    invalidateDraw()
                    try {
                        var timedOut = true
                        val up = if (cell == REACT) {
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation().also { timedOut = false } }
                        } else {
                            waitForUpOrCancellation().also { timedOut = false }
                        }
                        when {
                            up != null -> {
                                up.consume()
                                spec.actions.onCell(cell, rect(cell))
                            }
                            timedOut -> {
                                spec.actions.onLong(cell)
                                waitForUpOrCancellation()?.consume()
                            }
                        }
                    } finally {
                        pressed = NONE
                        invalidateDraw()
                    }
                }
            },
        ),
    )

    private val hover = delegate(
        SuspendingPointerInputModifierNode(
            PointerInputEventHandler {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val pointing = change.type == PointerType.Mouse || change.type == PointerType.Stylus
                        val cell = if (!pointing || event.type == PointerEventType.Exit) NONE else cellAt(change.position)
                        if (cell != hovered) {
                            hovered = cell
                            invalidateDraw()
                            scheduleTip(cell)
                        }
                    }
                }
            },
        ),
    )

    fun update(element: ActionBarElement) {
        spec = element
        invalidateMeasurement()
        invalidateDraw()
        invalidateSemantics()
    }

    private fun scheduleTip(cell: Int) {
        cancelTip()
        if (cell == NONE) return
        tipJob = coroutineScope.launch {
            delay(TIP_DELAY_MS)
            spec.actions.onTip(cell, rect(cell))
        }
    }

    private fun cancelTip() {
        tipJob?.cancel()
        tipJob = null
        spec.actions.onTip(NONE, null)
    }

    override fun onDetach() {
        tipJob?.cancel()
        tipJob = null
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val kit = spec.kit
        replyText = if (spec.replies > 0) kit.measurer.measure(AnnotatedString(spec.replies.toString()), kit.countStyle) else null
        renoteText = if (spec.renotes > 0) kit.measurer.measure(AnnotatedString(spec.renotes.toString()), kit.countStyle) else null
        cellPx = CellMinSize.toPx()
        val minCell = cellPx
        replyWidth = cellWidth(replyText, minCell)
        renoteWidth = cellWidth(renoteText, minCell)
        width = constraints.maxWidth
        height = CellMinSize.roundToPx()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        return layout(width, height) { placeable.place(0, 0) }
    }

    private fun MeasureScope.cellWidth(text: TextLayoutResult?, minCell: Float): Float {
        val content = CellPadding.toPx() * 2 + GlyphSize.toPx() + (text?.let { GlyphGap.toPx() + it.size.width } ?: 0f)
        return maxOf(minCell, content)
    }

    private fun sizeOf(cell: Int): Float = when (cell) {
        REPLY -> replyWidth
        RENOTE -> renoteWidth
        else -> cellPx
    }

    private fun leftOf(cell: Int): Float {
        val left = when (cell) {
            REPLY -> 0f
            RENOTE -> replyWidth
            REACT -> replyWidth + renoteWidth
            else -> width - cellPx
        }
        return if (requireLayoutDirection() == LayoutDirection.Rtl) width - left - sizeOf(cell) else left
    }

    private fun rect(cell: Int): IntRect {
        val left = leftOf(cell)
        val size = sizeOf(cell)
        return IntRect(left.toInt(), 0, (left + size).toInt(), height)
    }

    private fun cellAt(position: Offset): Int {
        for (cell in 0 until CELLS) {
            val left = leftOf(cell)
            val size = sizeOf(cell)
            if (position.x >= left && position.x < left + size) return cell
        }
        return NONE
    }

    override fun ContentDrawScope.draw() {
        val glyph = GlyphSize.toPx()
        val pad = CellPadding.toPx()
        val gap = GlyphGap.toPx()
        val top = (size.height - glyph) / 2f
        val rtl = layoutDirection == LayoutDirection.Rtl
        val radius = CornerRadius(size.height / 2f)
        for (cell in 0 until CELLS) {
            val left = leftOf(cell)
            val cellSize = sizeOf(cell)
            val enabled = cell != RENOTE || spec.renoteEnabled
            val tone = if (cell == RENOTE) spec.renoteColor else spec.color
            if (enabled && cell == pressed) drawRoundRect(spec.color.copy(alpha = PRESS_ALPHA), Offset(left, 0f), Size(cellSize, size.height), radius)
            else if (enabled && cell == hovered) drawRoundRect(spec.color.copy(alpha = HOVER_ALPHA), Offset(left, 0f), Size(cellSize, size.height), radius)
            if (focused && cell == focusedCell) {
                val inset = FocusStroke.toPx() / 2f
                drawRoundRect(
                    spec.accent,
                    Offset(left + inset, inset),
                    Size(cellSize - inset * 2, size.height - inset * 2),
                    radius,
                    style = Stroke(FocusStroke.toPx()),
                )
            }
            val text = when (cell) {
                REPLY -> replyText
                RENOTE -> renoteText
                else -> null
            }
            val glyphX = when {
                cell >= REACT -> left + (cellSize - glyph) / 2f
                rtl -> left + cellSize - pad - glyph
                else -> left + pad
            }
            val painter = when (cell) {
                REPLY -> spec.kit.reply
                RENOTE -> spec.kit.renote
                REACT -> spec.kit.react
                else -> spec.kit.more
            }
            with(painter) { draw(glyphX, top, glyph, tone) }
            text?.let {
                val textX = if (rtl) glyphX - gap - it.size.width else glyphX + glyph + gap
                drawText(it, tone, Offset(textX, (size.height - it.size.height) / 2f))
            }
        }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        val labels = spec.kit.labels
        val reply = if (spec.replies > 0) "${labels.reply}, ${spec.replies}" else labels.reply
        val renoteLabel = if (spec.renoted) labels.unrenote else labels.renote
        val renote = if (spec.renotes > 0) "$renoteLabel, ${spec.renotes}" else renoteLabel
        contentDescription = "$reply. $renote. ${labels.react}"
        customActions = listOf(CustomAccessibilityAction(labels.more) { spec.actions.onCell(MORE, rect(MORE)); true })
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!focused || event.type != KeyEventType.KeyDown) return false
        val rtl = requireLayoutDirection() == LayoutDirection.Rtl
        return when (event.key) {
            Key.DirectionRight -> moveFocus(if (rtl) -1 else 1)
            Key.DirectionLeft -> moveFocus(if (rtl) 1 else -1)
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> {
                if (focusedCell != RENOTE || spec.renoteEnabled) spec.actions.onCell(focusedCell, rect(focusedCell))
                true
            }
            else -> false
        }
    }

    private fun moveFocus(step: Int): Boolean {
        val next = focusedCell + step
        if (next !in 0 until CELLS) return false
        focusedCell = next
        invalidateDraw()
        return true
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false
}

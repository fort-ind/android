package com.fortind.zenith.ui.ink

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop169
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.ink.InkColor
import com.fortind.zenith.ink.InkSize
import com.fortind.zenith.ink.InkTool
import com.fortind.zenith.ink.SheetBackground
import com.fortind.zenith.ink.SheetShape
import com.fortind.zenith.ui.feed.LabelTooltip
import com.fortind.zenith.ui.focusRing
import com.fortind.zenith.ui.handCursor

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkToolbar(state: InkUiState, viewModel: InkViewModel, vertical: Boolean, modifier: Modifier = Modifier) {
    val items: @Composable () -> Unit = {
        InkTool.entries.forEach { tool ->
            ToolButton(
                tool = tool,
                checked = state.tool == tool,
                onClick = { if (state.tool == tool) viewModel.openPanel(InkPanel.Style) else viewModel.selectTool(tool) },
            )
        }
        if (vertical) {
            HorizontalDivider(modifier = Modifier.width(32.dp).padding(vertical = 4.dp))
        } else {
            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp))
        }
        StyleButton(state = state, onClick = { viewModel.openPanel(InkPanel.Style) })
    }
    val colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    if (vertical) {
        VerticalFloatingToolbar(expanded = true, colors = colors, modifier = modifier) { items() }
    } else {
        HorizontalFloatingToolbar(expanded = true, colors = colors, modifier = modifier) { items() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolButton(tool: InkTool, checked: Boolean, onClick: () -> Unit) {
    val label = stringResource(tool.labelRes())
    LabelTooltip(label) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = { onClick() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.iconToggleButtonVibrantColors(),
            modifier = Modifier.handCursor(),
        ) {
            Icon(tool.icon(), contentDescription = label)
        }
    }
}

@Composable
private fun StyleButton(state: InkUiState, onClick: () -> Unit) {
    val label = stringResource(R.string.ink_style)
    val stateText = if (state.tool == InkTool.Eraser) {
        stringResource(state.style.size.labelRes())
    } else {
        stringResource(R.string.ink_style_state, stringResource(state.style.color.labelRes()), stringResource(state.style.size.labelRes()))
    }
    LabelTooltip(label) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.handCursor().semantics { stateDescription = stateText },
        ) {
            Swatch(color = state.swatchColor(), dot = state.tool == InkTool.Eraser, size = state.style.size, label = label)
        }
    }
}

@Composable
private fun Swatch(color: Color, dot: Boolean, size: InkSize, label: String) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = label }
            .border(1.dp, outline, CircleShape)
            .background(if (dot) Color.Transparent else color, CircleShape),
    ) {
        if (dot) {
            val diameter = when (size) {
                InkSize.Small -> 6.dp
                InkSize.Medium -> 11.dp
                InkSize.Large -> 18.dp
            }
            Box(Modifier.size(diameter).background(MaterialTheme.colorScheme.onSurface, CircleShape))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun StylePanel(state: InkUiState, viewModel: InkViewModel) {
    Column {
        val showColors = state.tool != InkTool.Eraser
        val groups = if (showColors) 2 else 1
        if (showColors) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, groups), modifier = Modifier.fillMaxWidth()) {
                MenuDefaults.DropdownMenuGroupLabel { Text(stringResource(R.string.ink_color)) }
                FlowRow(
                    maxItemsInEachRow = SwatchesPerRow,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup(),
                ) {
                    InkColor.entries.forEach { color ->
                        ColorChoice(
                            color = color,
                            selected = state.style.color == color,
                            onClick = { viewModel.setColor(color) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        }
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(groups - 1, groups), modifier = Modifier.fillMaxWidth()) {
            MenuDefaults.DropdownMenuGroupLabel { Text(stringResource(R.string.ink_size)) }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                InkSize.entries.forEachIndexed { index, size ->
                    SegmentedButton(
                        selected = state.style.size == size,
                        onClick = { viewModel.setSize(size) },
                        shape = SegmentedButtonDefaults.itemShape(index, InkSize.entries.size),
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(stringResource(size.labelRes()))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChoice(color: InkColor, selected: Boolean, onClick: () -> Unit) {
    val fill = Color(color.argb)
    val colors = MaterialTheme.colorScheme
    val label = stringResource(color.labelRes())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .focusRing(colors.primary, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .handCursor()
            .semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.primary else colors.outline, CircleShape)
                .padding(if (selected) 4.dp else 1.dp)
                .background(fill, CircleShape),
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (Color(color.argb).luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorePanel(state: InkUiState, viewModel: InkViewModel) {
    val onDismiss = viewModel::closePanel
    Column {
        val groups = if (state.isMarkup) 1 else 3
        var group = 0
        if (!state.isMarkup) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(group++, groups), modifier = Modifier.fillMaxWidth()) {
                MenuDefaults.DropdownMenuGroupLabel { Text(stringResource(R.string.ink_background)) }
                SheetBackground.entries.forEachIndexed { index, background ->
                    SelectableDropdownMenuItem(
                        selected = state.background == background,
                        onClick = {
                            viewModel.setBackground(background)
                            onDismiss()
                        },
                        text = { Text(stringResource(background.labelRes())) },
                        shapes = MenuDefaults.itemShape(index, SheetBackground.entries.size),
                        leadingIcon = { BackgroundSwatch(background) },
                        selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                        modifier = Modifier.handCursor(),
                    )
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(group++, groups), modifier = Modifier.fillMaxWidth()) {
                MenuDefaults.DropdownMenuGroupLabel { Text(stringResource(R.string.ink_shape)) }
                SheetShape.entries.forEachIndexed { index, shape ->
                    SelectableDropdownMenuItem(
                        selected = state.shape == shape,
                        onClick = {
                            viewModel.setShape(shape)
                            onDismiss()
                        },
                        text = { Text(stringResource(shape.labelRes())) },
                        shapes = MenuDefaults.itemShape(index, SheetShape.entries.size),
                        leadingIcon = { Icon(shape.icon(), contentDescription = null) },
                        selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                        modifier = Modifier.handCursor(),
                    )
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        }
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(group, groups), modifier = Modifier.fillMaxWidth()) {
            DropdownMenuItem(
                onClick = {
                    viewModel.clearAll()
                    onDismiss()
                },
                text = { Text(stringResource(R.string.ink_clear_all)) },
                shape = MenuDefaults.standaloneItemShape,
                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                enabled = state.hasInk,
                modifier = Modifier.handCursor(state.hasInk),
            )
        }
    }
}

@Composable
private fun BackgroundSwatch(background: SheetBackground) {
    val outline = MaterialTheme.colorScheme.outline
    val fill = when (background) {
        SheetBackground.White -> Color.White
        SheetBackground.Black -> Color.Black
        SheetBackground.Theme -> MaterialTheme.colorScheme.primary
        SheetBackground.Transparent -> CheckerLight
    }
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(1.dp, outline, CircleShape),
    ) {
        drawRect(fill)
        if (background == SheetBackground.Transparent) {
            val half = Size(size.width / 2, size.height / 2)
            drawRect(CheckerDark, topLeft = Offset(size.width / 2, 0f), size = half)
            drawRect(CheckerDark, topLeft = Offset(0f, size.height / 2), size = half)
        }
    }
}

private fun InkUiState.swatchColor(): Color = Color(style.color.argb)

@StringRes
private fun InkTool.labelRes(): Int = when (this) {
    InkTool.Pen -> R.string.ink_tool_pen
    InkTool.Marker -> R.string.ink_tool_marker
    InkTool.Highlighter -> R.string.ink_tool_highlighter
    InkTool.Eraser -> R.string.ink_tool_eraser
}

private fun InkTool.icon(): ImageVector = when (this) {
    InkTool.Pen -> Icons.Default.Edit
    InkTool.Marker -> Icons.Default.Brush
    InkTool.Highlighter -> Icons.Default.BorderColor
    InkTool.Eraser -> EraserIcon
}

@StringRes
private fun InkSize.labelRes(): Int = when (this) {
    InkSize.Small -> R.string.ink_size_small
    InkSize.Medium -> R.string.ink_size_medium
    InkSize.Large -> R.string.ink_size_large
}

@StringRes
private fun InkColor.labelRes(): Int = when (this) {
    InkColor.Black -> R.string.ink_color_black
    InkColor.White -> R.string.ink_color_white
    InkColor.Gray -> R.string.ink_color_gray
    InkColor.Red -> R.string.ink_color_red
    InkColor.Orange -> R.string.ink_color_orange
    InkColor.Yellow -> R.string.ink_color_yellow
    InkColor.Green -> R.string.ink_color_green
    InkColor.Blue -> R.string.ink_color_blue
    InkColor.Purple -> R.string.ink_color_purple
    InkColor.Pink -> R.string.ink_color_pink
}

@StringRes
private fun SheetBackground.labelRes(): Int = when (this) {
    SheetBackground.White -> R.string.ink_background_white
    SheetBackground.Black -> R.string.ink_background_black
    SheetBackground.Theme -> R.string.ink_background_theme
    SheetBackground.Transparent -> R.string.ink_background_transparent
}

@StringRes
private fun SheetShape.labelRes(): Int = when (this) {
    SheetShape.Landscape -> R.string.ink_shape_landscape
    SheetShape.Wide -> R.string.ink_shape_wide
    SheetShape.Square -> R.string.ink_shape_square
    SheetShape.Portrait -> R.string.ink_shape_portrait
}

private fun SheetShape.icon(): ImageVector = when (this) {
    SheetShape.Landscape -> Icons.Default.CropLandscape
    SheetShape.Wide -> Icons.Default.Crop169
    SheetShape.Square -> Icons.Default.CropSquare
    SheetShape.Portrait -> Icons.Default.CropPortrait
}

val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Eraser", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 13f)
            lineTo(9f, 8f)
            lineTo(16f, 15f)
            lineTo(11f, 20f)
            close()
        }
        .path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(14f, 3f)
            lineTo(21f, 10f)
            lineTo(11f, 20f)
            lineTo(4f, 13f)
            close()
            moveTo(11f, 21f)
            lineTo(20f, 21f)
        }
        .build()
}

private const val SwatchesPerRow = 5

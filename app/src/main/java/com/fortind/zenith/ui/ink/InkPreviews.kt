package com.fortind.zenith.ui.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fortind.zenith.ink.InkTool
import com.fortind.zenith.ink.SheetBackground
import com.fortind.zenith.ink.SheetShape
import com.fortind.zenith.ui.theme.ZenithTheme

private val PenState = InkUiState(
    target = InkTarget.Drawing,
    phase = InkPhase.Ready,
    shape = SheetShape.Portrait,
    background = SheetBackground.White,
    canUndo = true,
)

private val EraserState = PenState.copy(tool = InkTool.Eraser)

@Composable
private fun PreviewSurface(dark: Boolean, content: @Composable () -> Unit) {
    ZenithTheme(darkTheme = dark, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, content = content)
    }
}

@Composable
private fun ToolbarPreview(dark: Boolean, state: InkUiState, vertical: Boolean) {
    PreviewSurface(dark) {
        Box(
            contentAlignment = if (vertical) Alignment.CenterStart else Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize(),
        ) {
            InkToolbar(
                state = state,
                onSelectTool = {},
                onOpenStyle = {},
                vertical = vertical,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun TopBarPreview(dark: Boolean) {
    PreviewSurface(dark) {
        InkTopBar(state = PenState, title = "Draw", onCancel = {}, onUndo = {}, onRedo = {}, onMore = {}, onAttach = {})
    }
}

private val NoActions = InkPanelActions({}, {}, {}, {}, {}, {})

@Composable
private fun ScreenPreview(dark: Boolean, panel: InkPanel?) {
    PreviewSurface(dark) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                InkTopBar(state = PenState, title = "Draw", onCancel = {}, onUndo = {}, onRedo = {}, onMore = {}, onAttach = {})
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .background(Color.White),
                    )
                    InkToolbar(
                        state = PenState,
                        onSelectTool = {},
                        onOpenStyle = {},
                        vertical = false,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    )
                }
            }
            panel?.let { InkPanelLayer(it, PenState, NoActions, vertical = false) }
        }
    }
}

@Preview(name = "Bottom toolbar, dark", widthDp = 411, heightDp = 112)
@Composable
private fun BottomToolbarDarkPreview() = ToolbarPreview(dark = true, state = PenState, vertical = false)

@Preview(name = "Bottom toolbar, light", widthDp = 411, heightDp = 112)
@Composable
private fun BottomToolbarLightPreview() = ToolbarPreview(dark = false, state = PenState, vertical = false)

@Preview(name = "Bottom toolbar, eraser", widthDp = 411, heightDp = 112)
@Composable
private fun BottomToolbarEraserPreview() = ToolbarPreview(dark = true, state = EraserState, vertical = false)

@Preview(name = "Side toolbar, dark", widthDp = 112, heightDp = 400)
@Composable
private fun SideToolbarDarkPreview() = ToolbarPreview(dark = true, state = PenState, vertical = true)

@Preview(name = "Side toolbar, light", widthDp = 112, heightDp = 400)
@Composable
private fun SideToolbarLightPreview() = ToolbarPreview(dark = false, state = PenState, vertical = true)

@Preview(name = "Top bar, 411 dp", widthDp = 411, heightDp = 64)
@Composable
private fun TopBarWidePreview() = TopBarPreview(dark = true)

@Preview(name = "Top bar, 360 dp", widthDp = 360, heightDp = 64)
@Composable
private fun TopBarNarrowPreview() = TopBarPreview(dark = false)

@Preview(name = "Screen with More, dark", widthDp = 411, heightDp = 820)
@Composable
private fun MoreDarkPreview() = ScreenPreview(dark = true, panel = InkPanel.More)

@Preview(name = "Screen with More, light", widthDp = 411, heightDp = 820)
@Composable
private fun MoreLightPreview() = ScreenPreview(dark = false, panel = InkPanel.More)

@Preview(name = "Screen with Style, dark", widthDp = 411, heightDp = 820)
@Composable
private fun StyleDarkPreview() = ScreenPreview(dark = true, panel = InkPanel.Style)

@Preview(name = "Screen, no panel, dark", widthDp = 411, heightDp = 820)
@Composable
private fun ScreenDarkPreview() = ScreenPreview(dark = true, panel = null)

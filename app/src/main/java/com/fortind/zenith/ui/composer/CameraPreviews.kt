package com.fortind.zenith.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fortind.zenith.media.CaptureMode
import com.fortind.zenith.ui.theme.ZenithTheme

private val BrightScene = Color(0xFFE8E4D8)
private val DarkScene = Color(0xFF101418)

@Composable
private fun ChipsPreview(scene: Color, vertical: Boolean, selected: CaptureMode, enabled: Boolean = true) {
    ZenithTheme(darkTheme = true, dynamicColor = false) {
        Box(
            contentAlignment = if (vertical) Alignment.CenterStart else Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize().background(scene).padding(16.dp),
        ) {
            CaptureModeChips(
                modes = CaptureMode.entries,
                selected = selected,
                vertical = vertical,
                enabled = enabled,
                onSelect = {},
            )
        }
    }
}

@Preview(widthDp = 400, heightDp = 96)
@Composable
private fun ChipsPortraitBright() = ChipsPreview(BrightScene, vertical = false, selected = CaptureMode.Auto)

@Preview(widthDp = 400, heightDp = 96)
@Composable
private fun ChipsPortraitDark() = ChipsPreview(DarkScene, vertical = false, selected = CaptureMode.Night)

@Preview(widthDp = 160, heightDp = 380)
@Composable
private fun ChipsLandscape() = ChipsPreview(BrightScene, vertical = true, selected = CaptureMode.Hdr)

@Preview(widthDp = 400, heightDp = 96)
@Composable
private fun ChipsDuringCapture() =
    ChipsPreview(DarkScene, vertical = false, selected = CaptureMode.Night, enabled = false)

package com.fortind.zenith.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.media.CaptureMode
import com.fortind.zenith.media.CaptureTimer
import com.fortind.zenith.media.Stabilization
import com.fortind.zenith.media.VideoOptions
import com.fortind.zenith.media.VideoSupport
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

@Composable
private fun FramingPreview(scene: Color) {
    ZenithTheme(darkTheme = true, dynamicColor = false) {
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize().background(scene).padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NightHint(onClick = {})
                ExposureSlider(
                    index = 2,
                    range = -6..6,
                    value = 0.67f,
                    onIndexChange = {},
                    interactionSource = remember { MutableInteractionSource() },
                )
                ZoomStops(stops = listOf(0.5f, 1f, 2f, 5f), ratio = 1.7f, onSelect = {})
                TimerBadge(CaptureTimer.Three)
            }
        }
    }
}

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun FramingBright() = FramingPreview(BrightScene)

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun FramingDark() = FramingPreview(DarkScene)

@Composable
private fun PanelPreview(scene: Color, recording: Boolean) {
    ZenithTheme(darkTheme = true, dynamicColor = false) {
        Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize().background(scene).padding(16.dp)) {
            CameraSettingsPanel(
                timer = CaptureTimer.Three,
                onTimerChange = {},
                timerEnabled = true,
                effectStrength = null,
                video = VideoSettings(
                    options = VideoOptions(stabilize = true, lowLight = true),
                    support = VideoSupport(fps60 = true, stabilization = Stabilization.Preview, fps60WithStabilization = false, lowLight = true),
                    recording = recording,
                    torchOn = true,
                    onChange = {},
                ),
                torchBrightness = SliderSetting(R.string.camera_torch_brightness, 3, 1..5) {},
                focusRequester = remember { FocusRequester() },
            )
        }
    }
}

@Preview(widthDp = 400, heightDp = 560)
@Composable
private fun PanelBright() = PanelPreview(BrightScene, recording = false)

@Preview(widthDp = 400, heightDp = 560)
@Composable
private fun PanelRecording() = PanelPreview(DarkScene, recording = true)

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun Countdown() {
    ZenithTheme(darkTheme = true, dynamicColor = false) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(BrightScene)) {
            CountdownNumber(3)
        }
    }
}

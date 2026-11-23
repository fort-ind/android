package com.fortind.zenith.ui.composer

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.media.CaptureTimer
import com.fortind.zenith.media.VideoOption
import com.fortind.zenith.media.VideoOptions
import com.fortind.zenith.media.VideoSupport
import com.fortind.zenith.ui.handCursor
import kotlin.math.roundToInt

internal class SliderSetting(val label: Int, val value: Int, val range: IntRange, val onChange: (Int) -> Unit)

internal class VideoSettings(
    val options: VideoOptions,
    val support: VideoSupport,
    val recording: Boolean,
    val torchOn: Boolean,
    val onChange: (VideoOptions) -> Unit,
)

@Composable
internal fun CameraSettingsPanel(
    timer: CaptureTimer,
    onTimerChange: (CaptureTimer) -> Unit,
    timerEnabled: Boolean,
    effectStrength: SliderSetting?,
    video: VideoSettings?,
    torchBrightness: SliderSetting?,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = CameraPanelScrim,
        contentColor = Color.White,
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.camera_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            TimerChoice(timer, onTimerChange, timerEnabled)
            effectStrength?.let { SliderRow(it) }
            video?.let { settings ->
                VideoOption.entries.filter { settings.support.offers(it) }.forEach { option ->
                    VideoSwitchRow(option, settings)
                }
            }
            torchBrightness?.let { SliderRow(it) }
        }
    }
}

@Composable
private fun TimerChoice(timer: CaptureTimer, onTimerChange: (CaptureTimer) -> Unit, enabled: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 20.dp),
    ) {
        Text(stringResource(R.string.camera_timer), style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            val choices = CaptureTimer.entries
            choices.forEachIndexed { index, choice ->
                val short = if (choice == CaptureTimer.Off) {
                    stringResource(R.string.camera_timer_off)
                } else {
                    stringResource(R.string.camera_timer_short, choice.seconds)
                }
                val long = if (choice == CaptureTimer.Off) {
                    short
                } else {
                    pluralStringResource(R.plurals.camera_timer_long, choice.seconds, choice.seconds)
                }
                ToggleButton(
                    checked = timer == choice,
                    onCheckedChange = { onTimerChange(choice) },
                    enabled = enabled,
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .handCursor(enabled)
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = long
                        },
                ) {
                    Text(short)
                }
            }
        }
    }
}

@Composable
private fun VideoSwitchRow(option: VideoOption, settings: VideoSettings) {
    val checked = settings.options.isOn(option)
    val enabled = !settings.recording
    val names = settings.options.turnedOffBy(option, settings.support).map { stringResource(it.labelRes()) }
    val detail = when {
        settings.recording -> stringResource(R.string.camera_settings_recording)
        option == VideoOption.LowLight && checked && settings.torchOn -> stringResource(R.string.camera_low_light_paused)
        !checked && names.size == 1 -> stringResource(R.string.camera_turns_off_one, names[0])
        !checked && names.size >= 2 -> stringResource(R.string.camera_turns_off_two, names[0], names[1])
        else -> stringResource(option.detailRes())
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { settings.onChange(settings.options.with(option, it, settings.support)) },
            )
            .handCursor(enabled)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(option.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.6f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
            ),
        )
    }
}

@Composable
private fun SliderRow(setting: SliderSetting) {
    val label = stringResource(setting.label)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 20.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        CameraSlider(
            value = setting.value.toFloat(),
            range = setting.range.first.toFloat()..setting.range.last.toFloat(),
            steps = 0,
            onChange = { setting.onChange(it.roundToInt()) },
            modifier = Modifier
                .fillMaxWidth()
                .handCursor()
                .semantics {
                    contentDescription = label
                    stateDescription = setting.value.toString()
                },
        )
    }
}

internal fun VideoOption.labelRes(): Int = when (this) {
    VideoOption.Fps60 -> R.string.camera_fps60
    VideoOption.Stabilize -> R.string.camera_stabilize
    VideoOption.LowLight -> R.string.camera_low_light
}

private fun VideoOption.detailRes(): Int = when (this) {
    VideoOption.Fps60 -> R.string.camera_fps60_detail
    VideoOption.Stabilize -> R.string.camera_stabilize_detail
    VideoOption.LowLight -> R.string.camera_low_light_detail
}

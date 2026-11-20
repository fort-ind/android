@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.composer.PollDraft
import com.fortind.zenith.composer.SCHEDULE_CW_LIMIT
import com.fortind.zenith.composer.SCHEDULE_POLL_CHOICE_LIMIT
import com.fortind.zenith.composer.ScheduleProblem
import com.fortind.zenith.composer.hourOf
import com.fortind.zenith.composer.localDateAsUtcMillis
import com.fortind.zenith.composer.minuteOf
import com.fortind.zenith.composer.nextWholeHour
import com.fortind.zenith.composer.scheduleProblem
import com.fortind.zenith.composer.scheduleTimeProblem

@Composable
internal fun PostSplitButton(
    label: String,
    posting: Boolean,
    enabled: Boolean,
    scheduleOpen: Boolean,
    onPost: () -> Unit,
    onToggleSchedule: () -> Unit,
) {
    val optionsLabel = stringResource(R.string.schedule_post_options)
    SplitButtonLayout(
        modifier = Modifier.heightIn(min = 48.dp).testTag("composer:post"),
        leadingButton = {
            SplitButtonDefaults.LeadingButton(onClick = onPost, enabled = enabled) {
                if (posting) {
                    LoadingIndicator(modifier = Modifier.size(24.dp), color = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.composer_posting))
                } else {
                    Text(label)
                }
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = scheduleOpen,
                onCheckedChange = { onToggleSchedule() },
                enabled = enabled,
                modifier = Modifier.testTag("composer:schedule-options"),
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = optionsLabel,
                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                )
            }
        },
    )
}

@Composable
internal fun ScheduleMenuSheet(
    visible: Boolean,
    state: ComposerUiState,
    viewModel: ComposerViewModel,
    onSchedule: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.schedule_menu_title)
    BottomSheetOverlay(visible = visible, title = title, onDismiss = onDismiss) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstFocus.requestFocus() }
        SheetTitle(title)
        val problem = scheduleProblem(
            localOnly = state.localOnly,
            contentWarning = if (state.cwEnabled) viewModel.cw.text.toString() else null,
            poll = viewModel.pollDraft(state),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            SegmentedListItem(
                onClick = onSchedule,
                enabled = problem == null,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = sheetItemColors(),
                leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
                supportingContent = { Text(problem?.let { scheduleProblemText(it) } ?: stringResource(R.string.schedule_menu_body)) },
                modifier = Modifier.focusRequester(firstFocus).testTag("composer:schedule"),
            ) {
                Text(stringResource(R.string.schedule_menu_item))
            }
        }
    }
}

@Composable
internal fun ScheduleDateSheet(
    visible: Boolean,
    nowMillis: Long,
    poll: () -> PollDraft?,
    onChoose: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember(nowMillis) { nextWholeHour(nowMillis) }
    DateTimeSheet(
        visible = visible,
        title = stringResource(R.string.schedule_title),
        confirmLabel = stringResource(R.string.schedule_confirm),
        nowMillis = nowMillis,
        initialDateUtcMillis = localDateAsUtcMillis(first),
        initialHour = hourOf(first),
        initialMinute = minuteOf(first),
        problemFor = { chosen ->
            when (scheduleTimeProblem(chosen, nowMillis, poll())) {
                ScheduleProblem.TooSoon -> R.string.schedule_problem_soon
                ScheduleProblem.PollEndsBeforePost -> R.string.schedule_problem_poll_ends
                else -> null
            }
        },
        onChoose = onChoose,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun ScheduledEditNotice() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("composer:scheduled-notice"),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Text(stringResource(R.string.schedule_editing_notice), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun scheduleProblemText(problem: ScheduleProblem): String = when (problem) {
    ScheduleProblem.LocalOnly -> stringResource(R.string.schedule_problem_local_only)
    ScheduleProblem.ContentWarningTooLong -> pluralStringResource(R.plurals.schedule_problem_cw, SCHEDULE_CW_LIMIT, SCHEDULE_CW_LIMIT)
    ScheduleProblem.PollChoiceTooLong ->
        pluralStringResource(R.plurals.schedule_problem_poll_choice, SCHEDULE_POLL_CHOICE_LIMIT, SCHEDULE_POLL_CHOICE_LIMIT)
    ScheduleProblem.PollEndsBeforePost -> stringResource(R.string.schedule_problem_poll_ends)
    ScheduleProblem.TooSoon -> stringResource(R.string.schedule_problem_soon)
}

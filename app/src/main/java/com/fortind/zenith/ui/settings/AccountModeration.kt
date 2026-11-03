@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.settings

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.R
import com.fortind.zenith.account.ModerationEntry
import com.fortind.zenith.account.ModerationKind
import com.fortind.zenith.account.SettingsEditor
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.account.AccountSettingsViewModel
import com.fortind.zenith.ui.account.ListStatus
import com.fortind.zenith.ui.account.ModerationViewModel
import com.fortind.zenith.ui.account.busyKey
import com.fortind.zenith.ui.rememberReduceMotion

private const val WORDS_TAB = 3
private const val INSTANCES_TAB = 4

@Composable
internal fun ModerationContent(viewModel: ModerationViewModel, settings: AccountSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settings.uiState.collectAsStateWithLifecycle()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var confirmingUnblock by rememberSaveable { mutableStateOf<String?>(null) }
    val kinds = ModerationKind.entries
    val kind = kinds.getOrNull(selectedIndex)
    val list = kind?.let(state::list)
    val reduceMotion = rememberReduceMotion()

    LaunchedEffect(kind, list?.status) {
        if (kind != null && list?.status == ListStatus.Idle) viewModel.load(kind)
    }

    if (state.needsPermission || settingsState.needsPermission) {
        ReauthorizePrompt()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 0.dp) {
            kinds.forEachIndexed { index, item ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index },
                    text = { Text(stringResource(item.tabRes()), maxLines = 1) },
                )
            }
            Tab(
                selected = selectedIndex == WORDS_TAB,
                onClick = { selectedIndex = WORDS_TAB },
                text = { Text(stringResource(R.string.moderation_tab_words), maxLines = 1) },
            )
            Tab(
                selected = selectedIndex == INSTANCES_TAB,
                onClick = { selectedIndex = INSTANCES_TAB },
                text = { Text(stringResource(R.string.moderation_tab_instances), maxLines = 1) },
            )
        }
        when {
            selectedIndex == WORDS_TAB -> MuteWordsTab(settings)
            selectedIndex == INSTANCES_TAB -> MuteInstancesTab(settings)
            kind == null || list == null -> Unit
            list.status == ListStatus.Failed -> Notice(
                text = stringResource(R.string.moderation_load_failed),
                actionRes = R.string.moderation_retry,
                onAction = { viewModel.refresh(kind) },
            )
            list.status != ListStatus.Ready -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                if (reduceMotion) Text(stringResource(R.string.moderation_loading)) else LoadingIndicator()
            }
            list.items.isEmpty() -> Notice(text = stringResource(kind.emptyRes()))
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    list.items.forEachIndexed { index, entry ->
                        ModerationRow(
                            entry = entry,
                            index = index,
                            count = list.items.size,
                            busy = busyKey(entry) in state.busy,
                            onAction = {
                                if (entry.kind == ModerationKind.Block) confirmingUnblock = entry.id else viewModel.remove(entry)
                            },
                        )
                    }
                }
                if (list.loadingMore) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else if (list.loadMoreFailed) {
                    TextButton(onClick = { viewModel.loadMore(kind) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.moderation_load_more_failed))
                    }
                } else if (!list.endReached) {
                    TextButton(onClick = { viewModel.loadMore(kind) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.moderation_load_more))
                    }
                }
            }
        }
    }

    val target = confirmingUnblock?.let { id -> list?.items?.firstOrNull { it.id == id } }
    if (confirmingUnblock != null && target == null) confirmingUnblock = null
    target?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmingUnblock = null },
            title = { Text(stringResource(R.string.moderation_unblock_title, entry.user.displayName)) },
            text = { Text(stringResource(R.string.moderation_unblock_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUnblock = null
                        viewModel.remove(entry)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.moderation_unblock_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmingUnblock = null }) { Text(stringResource(R.string.note_dialog_cancel)) } },
        )
    }
}

@Composable
private fun MuteWordsTab(settings: AccountSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.mute_words_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsTextEditor(
            state = settings.editor(SettingsEditor.MutedWords),
            labelRes = R.string.mute_soft_title,
            supportingRes = R.string.mute_soft_body,
            multiline = true,
            onSave = { settings.save(SettingsEditor.MutedWords) },
        )
        SettingsTextEditor(
            state = settings.editor(SettingsEditor.HardMutedWords),
            labelRes = R.string.mute_hard_title,
            supportingRes = R.string.mute_hard_body,
            multiline = true,
            onSave = { settings.save(SettingsEditor.HardMutedWords) },
        )
    }
}

@Composable
private fun MuteInstancesTab(settings: AccountSettingsViewModel) {
    SettingsTextEditor(
        state = settings.editor(SettingsEditor.MutedInstances),
        labelRes = R.string.mute_instances_title,
        supportingRes = R.string.mute_instances_body,
        multiline = true,
        onSave = { settings.save(SettingsEditor.MutedInstances) },
    )
}

@Composable
private fun ModerationRow(entry: ModerationEntry, index: Int, count: Int, busy: Boolean, onAction: () -> Unit) {
    val context = LocalContext.current
    val expires = entry.expiresAtMillis
    SettingsRow(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leading = { Avatar(name = entry.user.displayName, url = entry.user.avatarUrl, size = 44.dp) },
        headline = { Text(entry.user.displayName, maxLines = 1) },
        supporting = {
            Column {
                Text(entry.user.handle, maxLines = 1)
                if (entry.kind == ModerationKind.Mute && expires != null) {
                    Text(
                        text = stringResource(
                            R.string.moderation_muted_until,
                            DateUtils.formatDateTime(context, expires, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailing = {
            TextButton(onClick = onAction, enabled = !busy) { Text(stringResource(entry.kind.actionRes())) }
        },
    )
}

@Composable
private fun Notice(text: String, @StringRes actionRes: Int? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionRes != null) TextButton(onClick = onAction) { Text(stringResource(actionRes)) }
    }
}

@StringRes
private fun ModerationKind.tabRes(): Int = when (this) {
    ModerationKind.Mute -> R.string.moderation_tab_muted
    ModerationKind.RenoteMute -> R.string.moderation_tab_renotes
    ModerationKind.Block -> R.string.moderation_tab_blocked
}

@StringRes
private fun ModerationKind.emptyRes(): Int = when (this) {
    ModerationKind.Mute -> R.string.moderation_empty_muted
    ModerationKind.RenoteMute -> R.string.moderation_empty_renotes
    ModerationKind.Block -> R.string.moderation_empty_blocked
}

@StringRes
private fun ModerationKind.actionRes(): Int = when (this) {
    ModerationKind.Mute -> R.string.moderation_action_unmute
    ModerationKind.RenoteMute -> R.string.moderation_action_show_renotes
    ModerationKind.Block -> R.string.moderation_action_unblock
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.account.AccountChoice
import com.fortind.zenith.account.AccountFlag
import com.fortind.zenith.account.OlderNotesCutoff
import com.fortind.zenith.account.OlderNotesStep
import com.fortind.zenith.account.SettingsEditor
import com.fortind.zenith.ui.account.AccountSettingsUiState
import com.fortind.zenith.ui.account.AccountSettingsViewModel

private const val NEVER_VALUE = "never"

internal enum class PrivacyGroup(
    @param:StringRes val titleRes: Int,
    @param:StringRes val supportingRes: Int,
    val icon: ImageVector,
) {
    FOLLOWERS(R.string.privacy_followers_title, R.string.privacy_followers_supporting, Icons.Default.Group),
    VISIBILITY(R.string.privacy_visibility_title, R.string.privacy_visibility_supporting, Icons.Default.Visibility),
    DISCOVERY(R.string.privacy_discovery_title, R.string.privacy_discovery_supporting, Icons.Default.TravelExplore),
    OLDER_NOTES(R.string.privacy_older_title, R.string.privacy_older_supporting, Icons.Default.History),
    CHAT(R.string.privacy_chat_title, R.string.privacy_chat_supporting, Icons.AutoMirrored.Filled.Chat),
}

@Composable
internal fun PrivacyOverview(onOpen: (PrivacyGroup) -> Unit) {
    val groups = PrivacyGroup.entries
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        groups.forEachIndexed { index, group ->
            SettingsRow(
                shapes = segmentedShapes(index, groups.size),
                action = RowAction.Click({ onOpen(group) }),
                leading = { Icon(group.icon, contentDescription = null) },
                headline = { Text(stringResource(group.titleRes)) },
                supporting = { Text(stringResource(group.supportingRes)) },
            )
        }
    }
}

@Composable
internal fun PrivacyGroupContent(group: PrivacyGroup, state: AccountSettingsUiState, viewModel: AccountSettingsViewModel) {
    if (state.needsPermission) {
        ReauthorizePrompt()
        return
    }
    when (group) {
        PrivacyGroup.FOLLOWERS -> FollowersGroup(state, viewModel)
        PrivacyGroup.VISIBILITY -> VisibilityGroup(state, viewModel)
        PrivacyGroup.DISCOVERY -> DiscoveryGroup(state, viewModel)
        PrivacyGroup.OLDER_NOTES -> OlderNotesGroup(state, viewModel)
        PrivacyGroup.CHAT -> ChatGroup(state, viewModel)
    }
}

@Composable
private fun FollowersGroup(state: AccountSettingsUiState, viewModel: AccountSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            AccountFlagRow(
                flag = AccountFlag.Locked,
                state = state,
                titleRes = R.string.privacy_locked_title,
                supportingRes = R.string.privacy_locked_body,
                shapes = segmentedShapes(0, 3),
                onChange = { viewModel.setFlag(AccountFlag.Locked, it) },
            )
            AccountFlagRow(
                flag = AccountFlag.AutoAcceptFollowed,
                state = state,
                titleRes = R.string.privacy_auto_accept_title,
                supportingRes = R.string.privacy_auto_accept_body,
                shapes = segmentedShapes(1, 3),
                onChange = { viewModel.setFlag(AccountFlag.AutoAcceptFollowed, it) },
                enabled = state.flag(AccountFlag.Locked),
            )
            AccountFlagRow(
                flag = AccountFlag.CarefulBot,
                state = state,
                titleRes = R.string.privacy_careful_bot_title,
                supportingRes = R.string.privacy_careful_bot_body,
                shapes = segmentedShapes(2, 3),
                onChange = { viewModel.setFlag(AccountFlag.CarefulBot, it) },
            )
        }
        SettingsTextEditor(
            state = viewModel.editor(SettingsEditor.FollowedMessage),
            labelRes = R.string.privacy_followed_message_title,
            supportingRes = R.string.privacy_followed_message_body,
            multiline = false,
            onSave = { viewModel.save(SettingsEditor.FollowedMessage) },
        )
    }
}

@Composable
private fun VisibilityGroup(state: AccountSettingsUiState, viewModel: AccountSettingsViewModel) {
    val options = listOf(
        ChoiceOption("public", stringResource(R.string.privacy_visibility_public)),
        ChoiceOption("followers", stringResource(R.string.privacy_visibility_followers)),
        ChoiceOption("private", stringResource(R.string.privacy_visibility_private)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        AccountChoiceRow(
            title = stringResource(R.string.privacy_following_visibility),
            selected = state.choice(AccountChoice.FollowingVisibility),
            options = options,
            onSelect = { viewModel.setChoice(AccountChoice.FollowingVisibility, it) },
            shapes = segmentedShapes(0, 4),
            enabled = !state.busy(AccountChoice.FollowingVisibility.key),
        )
        AccountChoiceRow(
            title = stringResource(R.string.privacy_followers_visibility),
            selected = state.choice(AccountChoice.FollowersVisibility),
            options = options,
            onSelect = { viewModel.setChoice(AccountChoice.FollowersVisibility, it) },
            shapes = segmentedShapes(1, 4),
            enabled = !state.busy(AccountChoice.FollowersVisibility.key),
        )
        AccountFlagRow(
            flag = AccountFlag.PublicReactions,
            state = state,
            titleRes = R.string.privacy_public_reactions_title,
            supportingRes = R.string.privacy_public_reactions_body,
            shapes = segmentedShapes(2, 4),
            onChange = { viewModel.setFlag(AccountFlag.PublicReactions, it) },
        )
        AccountFlagRow(
            flag = AccountFlag.HideOnlineStatus,
            state = state,
            titleRes = R.string.privacy_hide_online_title,
            supportingRes = R.string.privacy_hide_online_body,
            shapes = segmentedShapes(3, 4),
            onChange = { viewModel.setFlag(AccountFlag.HideOnlineStatus, it) },
        )
    }
}

@Composable
private fun DiscoveryGroup(state: AccountSettingsUiState, viewModel: AccountSettingsViewModel) {
    var confirmingSignin by rememberSaveable { mutableStateOf(false) }
    val rows = listOf(
        Triple(AccountFlag.Explorable, R.string.privacy_explorable_title, R.string.privacy_explorable_body),
        Triple(AccountFlag.NoCrawle, R.string.privacy_no_crawle_title, R.string.privacy_no_crawle_body),
        Triple(AccountFlag.PreventAiLearning, R.string.privacy_prevent_ai_title, R.string.privacy_prevent_ai_body),
        Triple(AccountFlag.NoIndex, R.string.privacy_no_index_title, R.string.privacy_no_index_body),
        Triple(AccountFlag.EnableRss, R.string.privacy_rss_title, R.string.privacy_rss_body),
        Triple(AccountFlag.RequireSignin, R.string.privacy_require_signin_title, R.string.privacy_require_signin_body),
    )
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { index, (flag, title, body) ->
            AccountFlagRow(
                flag = flag,
                state = state,
                titleRes = title,
                supportingRes = body,
                shapes = segmentedShapes(index, rows.size),
                onChange = { value ->
                    if (flag == AccountFlag.RequireSignin && value) confirmingSignin = true else viewModel.setFlag(flag, value)
                },
            )
        }
    }
    if (confirmingSignin) {
        AlertDialog(
            onDismissRequest = { confirmingSignin = false },
            title = { Text(stringResource(R.string.privacy_require_signin_confirm_title)) },
            text = { Text(stringResource(R.string.privacy_require_signin_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSignin = false
                        viewModel.setFlag(AccountFlag.RequireSignin, true)
                    },
                ) {
                    Text(stringResource(R.string.privacy_require_signin_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignin = false }) { Text(stringResource(R.string.note_dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun OlderNotesGroup(state: AccountSettingsUiState, viewModel: AccountSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        OlderNotesRow(
            cutoff = OlderNotesCutoff.FollowersOnly,
            state = state,
            viewModel = viewModel,
            titleRes = R.string.privacy_older_followers_title,
            bodyRes = R.string.privacy_older_followers_body,
            index = 0,
        )
        OlderNotesRow(
            cutoff = OlderNotesCutoff.Hidden,
            state = state,
            viewModel = viewModel,
            titleRes = R.string.privacy_older_hidden_title,
            bodyRes = R.string.privacy_older_hidden_body,
            index = 1,
        )
    }
}

@Composable
private fun OlderNotesRow(
    cutoff: OlderNotesCutoff,
    state: AccountSettingsUiState,
    viewModel: AccountSettingsViewModel,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    index: Int,
) {
    val current = state.cutoff(cutoff)
    val known = OlderNotesStep.entries.any { it.seconds == current }
    val options = buildList {
        add(ChoiceOption(NEVER_VALUE, stringResource(R.string.privacy_older_never)))
        OlderNotesStep.entries.forEach { step ->
            add(ChoiceOption(step.seconds.toString(), stringResource(step.labelRes())))
        }
        if (current != null && !known) {
            add(ChoiceOption(current.toString(), stringResource(R.string.privacy_older_custom), selectable = false))
        }
    }
    AccountChoiceRow(
        title = stringResource(titleRes),
        selected = current?.toString() ?: NEVER_VALUE,
        options = options,
        onSelect = { viewModel.setCutoff(cutoff, it.toLongOrNull()) },
        shapes = segmentedShapes(index, 2),
        enabled = !state.busy(cutoff.key),
        supporting = stringResource(bodyRes),
    )
}

@StringRes
private fun OlderNotesStep.labelRes(): Int = when (this) {
    OlderNotesStep.OneHour -> R.string.privacy_older_one_hour
    OlderNotesStep.OneDay -> R.string.privacy_older_one_day
    OlderNotesStep.ThreeDays -> R.string.privacy_older_three_days
    OlderNotesStep.OneWeek -> R.string.privacy_older_one_week
    OlderNotesStep.OneMonth -> R.string.privacy_older_one_month
    OlderNotesStep.ThreeMonths -> R.string.privacy_older_three_months
    OlderNotesStep.OneYear -> R.string.privacy_older_one_year
}

@Composable
private fun ChatGroup(state: AccountSettingsUiState, viewModel: AccountSettingsViewModel) {
    AccountChoiceRow(
        title = stringResource(R.string.privacy_chat_scope),
        selected = state.choice(AccountChoice.ChatScope),
        options = listOf(
            ChoiceOption("everyone", stringResource(R.string.privacy_chat_everyone)),
            ChoiceOption("followers", stringResource(R.string.privacy_chat_followers)),
            ChoiceOption("following", stringResource(R.string.privacy_chat_following)),
            ChoiceOption("mutual", stringResource(R.string.privacy_chat_mutual)),
            ChoiceOption("none", stringResource(R.string.privacy_chat_none)),
        ),
        onSelect = { viewModel.setChoice(AccountChoice.ChatScope, it) },
        shapes = segmentedShapes(0, 1),
        enabled = !state.busy(AccountChoice.ChatScope.key),
        supporting = stringResource(R.string.privacy_chat_scope_body),
    )
}

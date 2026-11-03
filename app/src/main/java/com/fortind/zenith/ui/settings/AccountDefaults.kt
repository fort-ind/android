@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.R
import com.fortind.zenith.account.AccountChoice
import com.fortind.zenith.account.AccountFlag
import com.fortind.zenith.account.CW_PRIORITY_DEFAULT
import com.fortind.zenith.account.CW_PRIORITY_DEFAULT_PARENT
import com.fortind.zenith.account.CW_PRIORITY_PARENT
import com.fortind.zenith.account.CW_PRIORITY_PARENT_DEFAULT
import com.fortind.zenith.account.NOTIFICATION_RULE_LIST
import com.fortind.zenith.account.NotificationFilterType
import com.fortind.zenith.account.NotificationRule
import com.fortind.zenith.account.PROFILE_LANGUAGE_CODES
import com.fortind.zenith.account.SettingsEditor
import com.fortind.zenith.ui.account.AccountSettingsUiState
import com.fortind.zenith.ui.account.AccountSettingsViewModel
import java.text.Collator
import java.util.Locale

private const val NO_LANGUAGE = "none"

@Composable
internal fun PostingAccountDefaults(viewModel: AccountSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.settings == null) return
    SectionLabel(R.string.posting_account_section)
    if (state.needsPermission) {
        ReauthorizePrompt()
        return
    }
    SettingsTextEditor(
        state = viewModel.editor(SettingsEditor.DefaultCw),
        labelRes = R.string.posting_default_cw_title,
        supportingRes = R.string.posting_default_cw_body,
        multiline = false,
        onSave = { viewModel.save(SettingsEditor.DefaultCw) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        AccountChoiceRow(
            title = stringResource(R.string.posting_cw_priority_title),
            selected = state.choice(AccountChoice.DefaultCwPriority),
            options = listOf(
                ChoiceOption(CW_PRIORITY_PARENT, stringResource(R.string.posting_cw_priority_parent)),
                ChoiceOption(CW_PRIORITY_DEFAULT, stringResource(R.string.posting_cw_priority_default)),
                ChoiceOption(CW_PRIORITY_PARENT_DEFAULT, stringResource(R.string.posting_cw_priority_parent_default)),
                ChoiceOption(CW_PRIORITY_DEFAULT_PARENT, stringResource(R.string.posting_cw_priority_default_parent)),
            ),
            onSelect = { viewModel.setChoice(AccountChoice.DefaultCwPriority, it) },
            shapes = segmentedShapes(0, 5),
            enabled = !state.busy(AccountChoice.DefaultCwPriority.key),
            supporting = stringResource(R.string.posting_cw_priority_body),
        )
        AccountFlagRow(
            flag = AccountFlag.DefaultSensitive,
            state = state,
            titleRes = R.string.posting_default_sensitive_title,
            supportingRes = R.string.posting_default_sensitive_body,
            shapes = segmentedShapes(1, 5),
            onChange = { viewModel.setFlag(AccountFlag.DefaultSensitive, it) },
        )
        AccountFlagRow(
            flag = AccountFlag.AlwaysMarkNsfw,
            state = state,
            titleRes = R.string.posting_always_sensitive_title,
            supportingRes = R.string.posting_always_sensitive_body,
            shapes = segmentedShapes(2, 5),
            onChange = { viewModel.setFlag(AccountFlag.AlwaysMarkNsfw, it) },
        )
        AccountFlagRow(
            flag = AccountFlag.AutoSensitive,
            state = state,
            titleRes = R.string.posting_auto_sensitive_title,
            supportingRes = R.string.posting_auto_sensitive_body,
            shapes = segmentedShapes(3, 5),
            onChange = { viewModel.setFlag(AccountFlag.AutoSensitive, it) },
        )
        LanguageRow(state, viewModel)
    }
}

@Composable
private fun LanguageRow(state: AccountSettingsUiState, viewModel: AccountSettingsViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val none = stringResource(R.string.posting_language_none)
    val current = state.language
    val options = remember(locale, none, current) {
        val collator = Collator.getInstance(locale)
        val named = (PROFILE_LANGUAGE_CODES + listOfNotNull(current)).distinct()
            .map { code -> ChoiceOption(code, languageName(code, locale)) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        listOf(ChoiceOption(NO_LANGUAGE, none)) + named
    }
    AccountChoiceRow(
        title = stringResource(R.string.posting_language_title),
        selected = current ?: NO_LANGUAGE,
        options = options,
        onSelect = { viewModel.setLanguage(it.takeUnless { code -> code == NO_LANGUAGE }) },
        shapes = segmentedShapes(4, 5),
        enabled = !state.languageBusy,
        supporting = stringResource(R.string.posting_language_body),
    )
}

private fun languageName(code: String, locale: Locale): String =
    Locale.forLanguageTag(code).getDisplayName(locale).ifBlank { code }

@Composable
internal fun NotificationFilters(viewModel: AccountSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.settings == null) return
    SectionLabel(R.string.notification_filters_section)
    Text(
        text = stringResource(R.string.notification_filters_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (state.needsPermission) {
        ReauthorizePrompt()
        return
    }
    val rules = NotificationRule.entries.map { ChoiceOption(it.apiName, stringResource(it.labelRes())) }
    val listRule = ChoiceOption(NOTIFICATION_RULE_LIST, stringResource(R.string.notification_rule_list), selectable = false)
    val types = NotificationFilterType.entries
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        types.forEachIndexed { index, type ->
            val selected = state.notificationRule(type)
            AccountChoiceRow(
                title = stringResource(type.labelRes()),
                selected = selected,
                options = if (selected == NOTIFICATION_RULE_LIST) rules + listRule else rules,
                onSelect = { value ->
                    NotificationRule.entries.firstOrNull { it.apiName == value }?.let { viewModel.setNotificationRule(type, it) }
                },
                shapes = segmentedShapes(index, types.size),
                enabled = !state.notificationRulesBusy,
            )
        }
    }
}

@StringRes
private fun NotificationRule.labelRes(): Int = when (this) {
    NotificationRule.All -> R.string.notification_rule_all
    NotificationRule.Following -> R.string.notification_rule_following
    NotificationRule.Follower -> R.string.notification_rule_follower
    NotificationRule.MutualFollow -> R.string.notification_rule_mutual
    NotificationRule.FollowingOrFollower -> R.string.notification_rule_either
    NotificationRule.Never -> R.string.notification_rule_never
}

@StringRes
private fun NotificationFilterType.labelRes(): Int = when (this) {
    NotificationFilterType.Follow -> R.string.notification_filter_follow
    NotificationFilterType.Mention -> R.string.notification_filter_mention
    NotificationFilterType.Reply -> R.string.notification_filter_reply
    NotificationFilterType.Renote -> R.string.notification_filter_renote
    NotificationFilterType.Quote -> R.string.notification_filter_quote
    NotificationFilterType.Reaction -> R.string.notification_filter_reaction
    NotificationFilterType.PollEnded -> R.string.notification_filter_poll
    NotificationFilterType.FollowRequest -> R.string.notification_filter_request
}

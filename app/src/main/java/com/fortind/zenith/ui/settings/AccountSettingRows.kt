@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.account.AccountFlag
import com.fortind.zenith.account.SettingsEditorCheck
import com.fortind.zenith.ui.account.AccountSettingsEvent
import com.fortind.zenith.ui.account.AccountSettingsUiState
import com.fortind.zenith.ui.account.AccountSettingsViewModel
import com.fortind.zenith.ui.account.SettingSaveFailure
import com.fortind.zenith.ui.account.SettingsEditorState

internal data class ChoiceOption(val value: String, val label: String, val selectable: Boolean = true)

@Composable
internal fun AccountFlagRow(
    flag: AccountFlag,
    state: AccountSettingsUiState,
    @StringRes titleRes: Int,
    @StringRes supportingRes: Int,
    shapes: ListItemShapes,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val checked = state.flag(flag)
    val rowEnabled = enabled && !state.busy(flag.key)
    SettingsRow(
        shapes = shapes,
        action = RowAction.Toggle(checked = checked, onCheckedChange = onChange, enabled = rowEnabled),
        headline = { Text(stringResource(titleRes)) },
        supporting = { Text(stringResource(supportingRes)) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = rowEnabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        },
    )
}

@Composable
internal fun AccountChoiceRow(
    title: String,
    selected: String?,
    options: List<ChoiceOption>,
    onSelect: (String) -> Unit,
    shapes: ListItemShapes,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }?.label.orEmpty()
    Box(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(
            shapes = shapes,
            action = RowAction.Click(onClick = { open = true }, enabled = enabled),
            headline = { Text(title) },
            supporting = {
                Column {
                    Text(current, color = MaterialTheme.colorScheme.primary)
                    if (supporting != null) Text(supporting)
                }
            },
            trailing = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    enabled = option.selectable,
                    trailingIcon = if (option.value == selected) {
                        { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.settings_choice_selected)) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        if (option.value != selected) onSelect(option.value)
                    },
                )
            }
        }
    }
}

@Composable
internal fun SettingsTextEditor(
    state: SettingsEditorState,
    @StringRes labelRes: Int,
    @StringRes supportingRes: Int,
    multiline: Boolean,
    onSave: () -> Unit,
) {
    val problem = state.shownProblem
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            state = state.text,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
            label = { Text(stringResource(labelRes)) },
            isError = problem != null,
            lineLimits = if (multiline) {
                TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 10)
            } else {
                TextFieldLineLimits.SingleLine
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = if (multiline) ImeAction.Default else ImeAction.Done,
            ),
            onKeyboardAction = if (multiline) null else {
                { onSave() }
            },
            supportingText = {
                Text(
                    text = problem?.let { editorProblemText(it) } ?: stringResource(supportingRes),
                    modifier = if (problem != null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                )
            },
        )
        if (state.dirty) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = state::revert, enabled = !state.saving) { Text(stringResource(R.string.settings_editor_revert)) }
                Button(onClick = onSave, enabled = !state.saving) { Text(stringResource(R.string.account_save)) }
            }
        }
    }
}

@Composable
private fun editorProblemText(problem: SettingsEditorCheck): String = when (problem) {
    is SettingsEditorCheck.BadPattern -> stringResource(R.string.settings_editor_bad_pattern, problem.line)
    is SettingsEditorCheck.BadHost -> stringResource(R.string.settings_editor_bad_host, problem.line)
    is SettingsEditorCheck.TooLong -> pluralStringResource(R.plurals.settings_editor_too_long, problem.limit, problem.limit)
    is SettingsEditorCheck.Valid -> ""
}

@Composable
internal fun AccountSettingsEventsEffect(viewModel: AccountSettingsViewModel, snackbarHostState: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                AccountSettingsEvent.EditorSaved -> R.string.settings_saved
                is AccountSettingsEvent.Failed -> event.failure.messageRes()
            }
            snackbarHostState.showSnackbar(resources.getString(message))
        }
    }
}

@StringRes
private fun SettingSaveFailure.messageRes(): Int = when (this) {
    SettingSaveFailure.Offline -> R.string.settings_save_failed_offline
    SettingSaveFailure.RateLimited -> R.string.settings_save_failed_rate
    SettingSaveFailure.InvalidPattern -> R.string.settings_save_failed_pattern
    SettingSaveFailure.TooManyWords -> R.string.settings_save_failed_too_many
    SettingSaveFailure.CwTooLong -> R.string.settings_save_failed_cw
}

@Composable
internal fun segmentedShapes(index: Int, count: Int): ListItemShapes = ListItemDefaults.segmentedShapes(index = index, count = count)

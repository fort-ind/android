package com.fortind.zenith.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.account.reportTextLimit
import com.fortind.zenith.people.FollowState

@Composable
fun ConfirmFollowChangeDialog(state: FollowState, name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val requested = state == FollowState.Requested
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (requested) R.string.person_withdraw_title else R.string.person_unfollow_title, name)) },
        text = { Text(stringResource(if (requested) R.string.person_withdraw_body else R.string.person_unfollow_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (requested) R.string.person_withdraw_confirm else R.string.person_unfollow_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) } },
    )
}

@Composable
fun ConfirmUnblockDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_unblock_title, name)) },
        text = { Text(stringResource(R.string.person_unblock_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.person_unblock_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) } },
    )
}

@Composable
fun ReportDialog(name: String, prefix: String, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val limit = reportTextLimit(prefix)
    val used = text.codePointCount(0, text.length)
    val canSend = text.isNotBlank() && used <= limit
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.report_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.report_label)) },
                    supportingText = { Text(stringResource(R.string.report_counter, used, limit)) },
                    isError = used > limit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .padding(top = 4.dp)
                        .onPreviewKeyEvent { event ->
                            if (canSend && event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.Enter) {
                                onSend(text)
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(text) }, enabled = canSend) { Text(stringResource(R.string.report_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) } },
    )
}

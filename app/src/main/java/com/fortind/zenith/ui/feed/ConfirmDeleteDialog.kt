package com.fortind.zenith.ui.feed

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fortind.zenith.R

@Composable
fun ConfirmDeleteDialog(redraft: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (redraft) R.string.note_redraft_title else R.string.note_delete_title)) },
        text = { Text(stringResource(if (redraft) R.string.note_redraft_body else R.string.note_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(if (redraft) R.string.note_redraft_confirm else R.string.note_delete_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) } },
    )
}

package com.fortind.zenith.ui.feed

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.fortind.zenith.R

enum class MuteDuration(val millis: Long?, @param:StringRes val labelRes: Int) {
    Hour(60L * 60 * 1000, R.string.mute_duration_hour),
    Day(24L * 60 * 60 * 1000, R.string.mute_duration_day),
    Week(7L * 24 * 60 * 60 * 1000, R.string.mute_duration_week),
    Forever(null, R.string.mute_duration_forever),
}

@Composable
fun MuteDurationDialog(name: String, onSelect: (MuteDuration) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mute_dialog_title, name)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.mute_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MuteDuration.entries.forEach { duration ->
                    ListItem(
                        onClick = { onSelect(duration) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(stringResource(duration.labelRes))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) } },
    )
}

@Composable
fun ConfirmBlockDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_dialog_title, name)) },
        text = { Text(stringResource(R.string.block_dialog_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.block_dialog_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) } },
    )
}

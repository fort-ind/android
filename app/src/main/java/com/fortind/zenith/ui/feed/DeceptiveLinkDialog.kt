package com.fortind.zenith.ui.feed

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fortind.zenith.R
import com.fortind.zenith.ui.mfm.DeceptiveLink

@Composable
fun DeceptiveLinkDialog(link: DeceptiveLink, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_check_title)) },
        text = { Text(stringResource(R.string.link_check_body, link.shownHost, link.realHost)) },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.link_check_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) } },
    )
}

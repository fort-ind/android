package com.fortind.zenith.ui.social

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.R
import com.fortind.zenith.diagnostics.ProblemKind
import com.fortind.zenith.diagnostics.ProblemReport
import com.fortind.zenith.diagnostics.ProblemReports
import java.text.DateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ProblemReportPrompt(reports: ProblemReports, snackbarHostState: SnackbarHostState) {
    val report by reports.report.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    var open by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reports) { reports.load() }

    val reportTime = report?.timeMillis
    LaunchedEffect(reportTime) {
        val pending = reports.report.value?.takeUnless { it.announced } ?: return@LaunchedEffect
        reports.markAnnounced()
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.problem_report_snackbar, resources.getString(R.string.app_name)),
            actionLabel = resources.getString(R.string.problem_report_view),
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed && reports.report.value?.timeMillis == pending.timeMillis) open = true
    }

    val shown = report
    if (open && shown != null) {
        ProblemReportDialog(
            report = shown,
            onDismiss = { open = false },
            onDelete = {
                open = false
                scope.launch { reports.delete() }
            },
        )
    }
}

@Composable
private fun ProblemReportDialog(report: ProblemReport, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.problem_report_title)
    val text = remember(report, resources) { problemReportText(report, resources) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(CopiedLabelTime)
            copied = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, text)))
                            copied = true
                        }
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(stringResource(if (copied) R.string.problem_report_copied else R.string.problem_report_copy))
                }
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, title)
                            .putExtra(Intent.EXTRA_TEXT, text)
                        try {
                            context.startActivity(Intent.createChooser(send, resources.getString(R.string.problem_report_share_title)))
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                ) {
                    Text(stringResource(R.string.problem_report_share))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.problem_report_delete)) }
        },
    )
}

internal fun problemReportText(report: ProblemReport, resources: Resources): String {
    val environment = report.environment
    val kind = resources.getString(
        when (report.kind) {
            ProblemKind.Crash -> R.string.problem_report_kind_crash
            ProblemKind.NotResponding -> R.string.problem_report_kind_not_responding
            ProblemKind.NativeCrash -> R.string.problem_report_kind_native
        }
    )
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.timeMillis))
    val header = resources.getString(
        R.string.problem_report_header,
        resources.getString(R.string.app_name),
        environment.appVersion,
        environment.appBuild,
        environment.androidRelease,
        environment.sdk,
        environment.device,
        kind,
        time,
    )
    val thread = report.thread?.let { resources.getString(R.string.problem_report_thread, it) }
    val details = report.details.ifBlank { resources.getString(R.string.problem_report_no_details) }
    return listOfNotNull(header, thread, details).joinToString("\n\n")
}

private val CopiedLabelTime = 1_500.milliseconds

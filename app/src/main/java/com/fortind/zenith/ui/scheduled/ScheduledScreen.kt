@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fortind.zenith.ui.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortind.zenith.R
import com.fortind.zenith.schedule.ScheduledNote
import com.fortind.zenith.ui.PrimaryButton
import com.fortind.zenith.ui.StatusMessage
import com.fortind.zenith.ui.absoluteTime
import com.fortind.zenith.ui.composer.icon
import com.fortind.zenith.ui.composer.labelRes
import com.fortind.zenith.ui.feed.FeedLoading
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.people.PageKeyTarget
import com.fortind.zenith.ui.relativeTimeFromNow
import com.fortind.zenith.ui.rememberNow
import com.fortind.zenith.ui.scrollbar
import com.fortind.zenith.ui.settings.ReauthorizePrompt
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import com.fortind.zenith.ui.social.SocialUi
import kotlinx.coroutines.launch

private val RowMinWidth = 360.dp

@Composable
fun ScheduledScreen(
    ui: SocialUi,
    onBack: () -> Unit,
    viewModel: ScheduledViewModel = viewModel(factory = ScheduledViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by rememberNow()
    val resources = LocalResources.current
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(viewModel) { viewModel.refresh(quiet = true) }
    LaunchedEffect(viewModel, ui.snackbar) {
        viewModel.events.collect { event ->
            val message = when (event) {
                ScheduledEvent.Deleted -> R.string.scheduled_deleted
                ScheduledEvent.RateLimited -> R.string.feed_rate_limited
                ScheduledEvent.Failed -> R.string.scheduled_delete_failed
            }
            launch { ui.snackbar.showSnackbar(resources.getString(message)) }
        }
    }
    DisposableEffect(ui.pageKeys, viewModel) {
        val target = PageKeyTarget(onRefresh = { viewModel.refresh(quiet = true) }, onMoveTab = {})
        ui.pageKeys.target = target
        onDispose { if (ui.pageKeys.target === target) ui.pageKeys.target = null }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduled_title)) },
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.person_back), icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                },
                actions = {
                    TooltipIconButton(
                        label = stringResource(R.string.person_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh(quiet = true) },
                    )
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.status) {
                ScheduledStatus.Idle, ScheduledStatus.Loading -> FeedLoading(Modifier.fillMaxWidth().padding(top = 48.dp))
                ScheduledStatus.NeedsPermission -> Centered {
                    ReauthorizePrompt(R.string.scheduled_permission_title, R.string.scheduled_permission_body)
                }
                ScheduledStatus.Failed, ScheduledStatus.SignedOut -> Centered {
                    StatusMessage(
                        shape = MaterialShapes.Sunny,
                        title = stringResource(R.string.scheduled_failed_title),
                        body = stringResource(R.string.scheduled_failed_body),
                    ) { PrimaryButton(stringResource(R.string.feed_retry), { viewModel.refresh(quiet = false) }) }
                }
                ScheduledStatus.Ready -> if (state.items.isEmpty()) {
                    Centered {
                        StatusMessage(
                            shape = MaterialShapes.Cookie9Sided,
                            title = stringResource(R.string.scheduled_empty_title),
                            body = stringResource(R.string.scheduled_empty_body),
                        ) {}
                    }
                } else {
                    ScheduledGrid(
                        state = state,
                        now = now,
                        gridState = gridState,
                        onEdit = { ui.composer.editScheduled(it) },
                        onDelete = { deleting = it.id },
                    )
                }
            }
        }
    }

    val pending = deleting?.let { id -> state.items.firstOrNull { it.id == id } }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.scheduled_delete_title)) },
            text = { Text(stringResource(R.string.scheduled_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.delete(pending)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.scheduled_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.note_dialog_cancel)) } },
        )
    } else if (deleting != null) {
        deleting = null
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = 480.dp)) { content() }
    }
}

@Composable
private fun ScheduledGrid(
    state: ScheduledUiState,
    now: Long,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onEdit: (ScheduledNote) -> Unit,
    onDelete: (ScheduledNote) -> Unit,
) {
    val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(RowMinWidth),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().scrollbar(gridState).testTag("scheduled:list"),
    ) {
        item(key = "count", contentType = "count", span = fullSpan) {
            val max = state.max
            Text(
                text = if (max != null) {
                    stringResource(R.string.scheduled_count_of_max, state.items.size, max)
                } else {
                    pluralStringResource(R.plurals.scheduled_count, state.items.size, state.items.size)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(state.items, key = { it.id }, contentType = { "scheduled" }) { item ->
            ScheduledRow(
                item = item,
                now = now,
                busy = item.id in state.busy,
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun ScheduledRow(item: ScheduledNote, now: Long, busy: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val environment = LocalNoteEnvironment.current
    val note = item.note
    val whenText = absoluteTime(item.scheduledAtMillis)
    val textEnvironment = remember(note.id, environment.mfm) { environment.mfm.withEmoji(environment.noteEmoji(note)) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("scheduled:row"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(whenText, style = MaterialTheme.typography.titleSmall)
                    Text(
                        relativeTimeFromNow(item.scheduledAtMillis, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    note.visibility.icon(),
                    contentDescription = stringResource(note.visibility.labelRes()),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            RowDetails(item)
            note.cw?.let { warning ->
                Text(warning, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (note.textMfm.isNotEmpty()) {
                MfmContent(note.textMfm, textEnvironment, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (item.canEdit) {
                    val editDescription = stringResource(R.string.scheduled_edit_description, whenText)
                    TextButton(
                        onClick = onEdit,
                        enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = editDescription },
                    ) { Text(stringResource(R.string.scheduled_edit)) }
                }
                val deleteDescription = stringResource(R.string.scheduled_delete_description, whenText)
                TextButton(
                    onClick = onDelete,
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.semantics { contentDescription = deleteDescription },
                ) { Text(stringResource(R.string.scheduled_delete)) }
            }
        }
    }
}

@Composable
private fun RowDetails(item: ScheduledNote) {
    val note = item.note
    val details = buildList {
        note.reply?.let { add(stringResource(R.string.scheduled_reply_to, it.user.handle)) }
        note.renote?.let { add(stringResource(R.string.scheduled_quote_of, it.user.handle)) }
        if (note.poll != null) add(stringResource(R.string.scheduled_poll))
        if (item.fileIds.isNotEmpty()) add(pluralStringResource(R.plurals.scheduled_attachments, item.fileIds.size, item.fileIds.size))
    }
    if (details.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        details.forEach { detail ->
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

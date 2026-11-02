@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.fortind.zenith.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.ui.PrimaryButton
import com.fortind.zenith.ui.StatusMessage

private val ThreadMaxWidth = 720.dp

@Composable
fun ThreadPane(
    thread: ThreadState?,
    environment: NoteEnvironment,
    callbacks: NoteCallbacks,
    twoPane: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    onToggleBranch: (String) -> Unit,
    onLoadMoreBranch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.thread_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TooltipIconButton(
                        label = stringResource(if (twoPane) R.string.thread_close else R.string.thread_back),
                        icon = if (twoPane) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                    )
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            val focal = thread?.note
            if (focal != null && !focal.deleted) ReplyBar(focal) { callbacks.reply(focal) }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.TopCenter) {
            when {
                thread == null -> Unit
                thread.note == null && thread.loading -> FeedLoading()
                thread.note == null -> ThreadFailed(onRetry)
                else -> ThreadContent(thread, thread.note, environment, callbacks, onLoadMoreReplies, onToggleBranch, onLoadMoreBranch)
            }
        }
    }
}

@Composable
private fun ThreadContent(
    thread: ThreadState,
    focal: Note,
    environment: NoteEnvironment,
    callbacks: NoteCallbacks,
    onLoadMoreReplies: () -> Unit,
    onToggleBranch: (String) -> Unit,
    onLoadMoreBranch: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val rows = remember(thread.replies, thread.branches) { thread.replyRows() }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val focalIndex = thread.ancestors.size
    LaunchedEffect(thread.noteId, thread.loading) {
        if (!thread.loading && focalIndex > 0) listState.requestScrollToItem(focalIndex)
    }
    val itemModifier = Modifier.widthIn(max = ThreadMaxWidth).fillMaxWidth()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(thread.ancestors, key = { "ancestor:${it.id}" }, contentType = { "ancestor" }) { note ->
            Column(modifier = itemModifier) {
                NoteCard(note, environment, callbacks)
                ThreadDivider()
            }
        }
        item(key = "focal:${focal.id}", contentType = "focal") {
            Column(modifier = itemModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SelectionContainer {
                    NoteCard(focal, environment, callbacks, focal = true)
                }
                Text(
                    text = rememberAbsoluteTime(focal.createdAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ThreadDivider()
            }
        }
        if (thread.loading && thread.replies.isEmpty()) {
            item(key = "replies:loading", contentType = "loading") { FeedLoading() }
        }
        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            Column(modifier = itemModifier) {
                when (row) {
                    is ReplyRow.Item -> NoteCard(
                        note = row.note,
                        environment = environment,
                        callbacks = callbacks,
                        modifier = Modifier.threadIndent(row.depth, lineColor),
                        showReplyContext = false,
                    )
                    is ReplyRow.Toggle -> BranchToggle(row, lineColor) { onToggleBranch(row.parentId) }
                    is ReplyRow.More -> BranchMore(row, lineColor) { onLoadMoreBranch(row.parentId) }
                    is ReplyRow.Continue -> BranchContinue(row, lineColor) { callbacks.openThread(row.note) }
                    is ReplyRow.Divider -> ThreadDivider()
                }
            }
        }
        if (!thread.repliesEnd && thread.replies.isNotEmpty()) {
            item(key = "replies:more", contentType = "more") {
                val loadMore by rememberUpdatedState(onLoadMoreReplies)
                LaunchedEffect(thread.repliesLoadingMore, thread.replies.size) { if (!thread.repliesLoadingMore) loadMore() }
                FeedLoading()
            }
        }
    }
}

private val ThreadIndent = 16.dp
private val ThreadLineOffset = 36.dp
private val BranchLabelStart = 68.dp

private fun Modifier.threadIndent(depth: Int, lineColor: Color): Modifier =
    if (depth == 0) {
        this
    } else {
        this
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                repeat(depth) { level ->
                    val x = ThreadIndent.toPx() * level + ThreadLineOffset.toPx()
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth)
                }
            }
            .padding(start = ThreadIndent * depth)
    }

@Composable
private fun BranchRow(
    depth: Int,
    lineColor: Color,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RectangleShape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().threadIndent(depth, lineColor),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(min = 48.dp).padding(start = BranchLabelStart - ThreadIndent, end = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun BranchToggle(row: ReplyRow.Toggle, lineColor: Color, onClick: () -> Unit) {
    val onClickState by rememberUpdatedState(onClick)
    if (row.auto) LaunchedEffect(row.parentId) { onClickState() }
    val color = if (row.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    BranchRow(row.depth, lineColor, onClick) {
        if (row.loading) {
            LoadingIndicator(modifier = Modifier.size(24.dp))
        } else {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = when {
                row.failed -> stringResource(R.string.thread_branch_failed)
                row.loading -> pluralStringResource(R.plurals.thread_loading_replies, row.count, row.count)
                else -> pluralStringResource(R.plurals.thread_show_replies, row.count, row.count)
            },
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun BranchMore(row: ReplyRow.More, lineColor: Color, onClick: () -> Unit) {
    val onClickState by rememberUpdatedState(onClick)
    if (!row.failed) {
        LaunchedEffect(row.parentId, row.loading) { if (!row.loading) onClickState() }
    }
    BranchRow(row.depth, lineColor, onClick) {
        if (row.failed) {
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        } else {
            LoadingIndicator(modifier = Modifier.size(24.dp))
        }
        Text(
            text = stringResource(if (row.failed) R.string.thread_branch_failed else R.string.thread_more_branch_replies),
            style = MaterialTheme.typography.labelLarge,
            color = if (row.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BranchContinue(row: ReplyRow.Continue, lineColor: Color, onClick: () -> Unit) {
    BranchRow(row.depth, lineColor, onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.thread_continue),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReplyBar(note: Note, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = ThreadMaxWidth).fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.thread_reply_hint, note.user.handle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ThreadDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ThreadFailed(onRetry: () -> Unit) {
    Box(modifier = Modifier.widthIn(max = 480.dp).padding(16.dp)) {
        StatusMessage(
            shape = MaterialShapes.Cookie9Sided,
            title = stringResource(R.string.thread_failed),
            body = stringResource(R.string.feed_failed_body),
        ) {
            PrimaryButton(stringResource(R.string.feed_retry), onRetry)
        }
    }
}

@Composable
fun ThreadPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = 420.dp)) {
            StatusMessage(
                shape = MaterialShapes.Pill,
                title = stringResource(R.string.thread_placeholder_title),
                body = stringResource(R.string.thread_placeholder_body),
            ) {}
        }
    }
}

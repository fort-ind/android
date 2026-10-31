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
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                else -> ThreadContent(thread, thread.note, environment, callbacks, onLoadMoreReplies)
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
) {
    val listState = rememberLazyListState()
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
        items(thread.replies, key = { "reply:${it.id}" }, contentType = { "reply" }) { note ->
            Column(modifier = itemModifier) {
                NoteCard(note, environment, callbacks)
                ThreadDivider()
            }
        }
        if (!thread.repliesEnd && thread.replies.isNotEmpty()) {
            item(key = "replies:more", contentType = "more") {
                if (thread.repliesLoadingMore) {
                    FeedLoading()
                } else {
                    TextButton(onClick = onLoadMoreReplies) { Text(stringResource(R.string.thread_load_more_replies)) }
                }
            }
        }
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

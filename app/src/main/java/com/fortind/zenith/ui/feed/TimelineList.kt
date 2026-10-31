package com.fortind.zenith.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.TimelineKind
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val LOAD_MORE_THRESHOLD = 5
private const val VISIBLE_DEBOUNCE_MS = 500L
private const val NOTE_PREFIX_EXCLUDED = "feed:"

class TimelineListCallbacks(
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onRefresh: () -> Unit,
    val onShowPending: () -> Unit,
    val onLogIn: () -> Unit,
    val onAtTop: (Boolean) -> Unit,
    val onVisibleNotes: (Set<String>) -> Unit,
)

@OptIn(FlowPreview::class)
@Composable
fun TimelineList(
    kind: TimelineKind?,
    timeline: TimelineState,
    listState: LazyListState,
    environment: NoteEnvironment,
    noteCallbacks: NoteCallbacks,
    callbacks: TimelineListCallbacks,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    banner: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val topThreshold = with(LocalDensity.current) { 48.dp.roundToPx() }
    val current by rememberUpdatedState(callbacks)
    val atTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < topThreshold }
    }
    val leadingItems = (if (header != null) 1 else 0) + (if (banner != null) 1 else 0)

    LaunchedEffect(listState) {
        snapshotFlow { atTop }.collect { current.onAtTop(it) }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { current.onLoadMore() }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { (it.key as? String)?.takeUnless { key -> key.startsWith(NOTE_PREFIX_EXCLUDED) } }.toSet()
        }
            .distinctUntilChanged()
            .debounce(VISIBLE_DEBOUNCE_MS)
            .collect { current.onVisibleNotes(it) }
    }
    val firstId = timeline.items.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (firstId != null && listState.firstVisibleItemIndex <= leadingItems && atTop) listState.requestScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .testTag("feed:list")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.F5 || (event.isCtrlPressed && event.key == Key.R) -> {
                        current.onRefresh()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.MoveHome -> {
                        current.onShowPending()
                        scope.launch { listState.scrollToItem(0) }
                        true
                    }
                    !event.isCtrlPressed && event.key == Key.Period -> {
                        current.onShowPending()
                        true
                    }
                    !event.isCtrlPressed && event.key == Key.J -> focusManager.moveFocus(FocusDirection.Down)
                    !event.isCtrlPressed && event.key == Key.K -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            },
    ) {
        if (header != null) {
            item(key = "feed:header", contentType = "header") {
                Box(modifier = Modifier.widthIn(max = FeedMaxWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { header() }
            }
        }
        if (banner != null) {
            item(key = "feed:banner", contentType = "banner") {
                Box(modifier = Modifier.widthIn(max = FeedMaxWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { banner() }
            }
        }
        val itemModifier = Modifier.widthIn(max = FeedMaxWidth).fillMaxWidth()
        when {
            timeline.items.isEmpty() && (timeline.status == TimelineStatus.Loading || timeline.status == TimelineStatus.Idle) ->
                item(key = "feed:loading", contentType = "loading") { FeedLoading() }
            timeline.items.isEmpty() -> item(key = "feed:message", contentType = "message") {
                FeedMessage(timeline.status, kind, current.onRetry, current.onLogIn)
            }
            else -> {
                itemsIndexed(timeline.items, key = { _, note -> note.id }, contentType = { _, note -> noteContentType(note) }) { index, note ->
                    Column(modifier = if (reduceMotion) itemModifier else itemModifier.animateItem()) {
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        NoteCard(note = note, environment = environment, callbacks = noteCallbacks)
                    }
                }
                item(key = "feed:footer", contentType = "footer") { FeedFooter(timeline, current.onRetry) }
            }
        }
    }
}

private fun noteContentType(note: Note): String = when {
    note.isPureRenote -> "renote"
    note.target.files.isNotEmpty() -> "note-media"
    note.target.renote != null -> "quote"
    else -> "note"
}

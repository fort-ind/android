package com.fortind.zenith.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.R
import com.fortind.zenith.discovery.TrendingHashtag
import com.fortind.zenith.ui.discovery.CollectionsStatus
import com.fortind.zenith.ui.discovery.DiscoveryViewModel
import com.fortind.zenith.ui.handCursor
import com.fortind.zenith.ui.notifications.NotificationActions
import com.fortind.zenith.ui.notifications.NotificationItem
import com.fortind.zenith.ui.notifications.NotificationsUiState
import com.fortind.zenith.ui.notifications.NotificationsViewModel
import com.fortind.zenith.ui.notifications.rememberNotificationActions
import com.fortind.zenith.ui.rememberNow
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.scrollbar
import kotlinx.coroutines.delay

val SideColumnWidth = 360.dp
private const val SIDE_NOTIFICATIONS = 6
private const val SIDE_TRENDING = 10
private const val TRENDING_POLL_MS = 10 * 60 * 1_000L

@Composable
fun SideColumn(
    notifications: NotificationsViewModel,
    showNotifications: Boolean,
    discovery: DiscoveryViewModel,
    snackbar: SnackbarHostState,
    onOpenTag: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onSeeAllNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notificationState by notifications.uiState.collectAsStateWithLifecycle()
    val discoveryState by discovery.uiState.collectAsStateWithLifecycle()
    val actions = rememberNotificationActions(snackbar, notifications, onOpenNote, onOpenPerson)
    val now by rememberNow()
    val listState = rememberLazyListState()
    LaunchedEffect(discovery) {
        while (true) {
            discovery.loadTrending()
            delay(TRENDING_POLL_MS)
        }
    }
    val ready = (notificationState as? NotificationsUiState.Ready)?.takeIf { showNotifications }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = modifier.testTag("side:column").scrollbar(listState),
    ) {
        if (ready != null) notificationSection(ready, now, actions, onSeeAllNotifications)
        trendingSection(discoveryState.trendingStatus, discoveryState.trending.take(SIDE_TRENDING), onOpenTag, onRetry = discovery::loadTrending)
    }
}

private fun LazyListScope.notificationSection(
    state: NotificationsUiState.Ready,
    now: Long,
    actions: NotificationActions,
    onSeeAll: () -> Unit,
) {
    item(key = "notifications:header", contentType = "header") {
        SectionHeader(stringResource(R.string.notifications_title)) {
            TextButton(onClick = onSeeAll, modifier = Modifier.handCursor()) { Text(stringResource(R.string.side_see_all)) }
        }
    }
    val recent = state.items.take(SIDE_NOTIFICATIONS)
    if (recent.isEmpty()) {
        item(key = "notifications:empty", contentType = "message") {
            SectionMessage(stringResource(if (state.loading) R.string.side_loading else R.string.notifications_empty_title))
        }
    }
    itemsIndexed(recent, key = { _, notification -> "notification:${notification.id}" }, contentType = { _, _ -> "notification" }) { index, notification ->
        NotificationItem(
            notification = notification,
            followProgress = state.followRequests[notification.id],
            shapes = ListItemDefaults.segmentedShapes(index = index, count = recent.size),
            now = now,
            actions = actions,
        )
    }
}

private fun LazyListScope.trendingSection(
    status: CollectionsStatus,
    tags: List<TrendingHashtag>,
    onOpenTag: (String) -> Unit,
    onRetry: () -> Unit,
) {
    item(key = "trending:header", contentType = "header") { SectionHeader(stringResource(R.string.side_trending_title)) {} }
    when {
        status == CollectionsStatus.Failed -> item(key = "trending:failed", contentType = "message") {
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp).handCursor()) {
                Text(stringResource(R.string.side_trending_failed))
            }
        }
        tags.isEmpty() && status == CollectionsStatus.Ready -> item(key = "trending:empty", contentType = "message") {
            SectionMessage(stringResource(R.string.side_trending_empty))
        }
        tags.isEmpty() -> item(key = "trending:loading", contentType = "message") { SectionLoading() }
        else -> itemsIndexed(tags, key = { _, tag -> "trending:${tag.tag}" }, contentType = { _, _ -> "tag" }) { index, tag ->
            SegmentedListItem(
                onClick = { onOpenTag(tag.tag) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = tags.size),
                leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
                supportingContent = { Text(pluralStringResource(R.plurals.side_trending_people, tag.usersCount, tag.usersCount)) },
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(R.string.tag_title, tag.tag), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action()
    }
}

@Composable
private fun SectionMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionLoading() {
    if (rememberReduceMotion()) {
        SectionMessage(stringResource(R.string.side_loading))
    } else {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
    }
}

package com.fortind.zenith.ui.notifications

import android.content.ClipData
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.R
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.notifications.FortNotification
import com.fortind.zenith.ui.PrimaryButton
import com.fortind.zenith.ui.StatusMessage
import com.fortind.zenith.ui.launchCustomTab
import com.fortind.zenith.ui.login.AuthPageEffects
import com.fortind.zenith.ui.login.ErrorMessage
import com.fortind.zenith.ui.login.LoginViewModel
import com.fortind.zenith.ui.rememberNow
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.theme.isThemeDark
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val ContentMaxWidth = 720.dp
private const val LOAD_MORE_THRESHOLD = 5

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                NotificationsEvent.MarkReadFailed -> resources.getString(R.string.notifications_mark_read_failed)
                NotificationsEvent.FollowRequestFailed -> resources.getString(R.string.notifications_follow_request_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val actions = rememberNotificationActions(snackbarHostState, viewModel)

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            NotificationsUiState.Loading -> LoadingState()
            NotificationsUiState.Guest -> GuestState(onLogIn = viewModel::logIn)
            NotificationsUiState.NeedsPermission -> PermissionState()
            is NotificationsUiState.Ready -> ReadyContent(
                state = current,
                unreadCount = unreadCount,
                onFilter = viewModel::setFilter,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                onMarkAllRead = viewModel::markAllAsRead,
                actions = actions,
            )
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun rememberNotificationActions(
    snackbarHostState: SnackbarHostState,
    viewModel: NotificationsViewModel,
): NotificationActions {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val darkTheme = isThemeDark()
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val scope = rememberCoroutineScope()
    val noBrowserMessage = stringResource(R.string.login_error_no_browser)
    val linkCopiedMessage = stringResource(R.string.notifications_link_copied)

    return remember(context, clipboard, darkTheme, toolbarColor, noBrowserMessage, linkCopiedMessage, viewModel) {
        NotificationActions(
            open = { notification ->
                notification.webUrl?.let { url ->
                    if (!launchCustomTab(context, url, darkTheme, toolbarColor)) {
                        scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                    }
                }
            },
            copyLink = { notification ->
                notification.webUrl?.let { url ->
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(url, url)))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            snackbarHostState.showSnackbar(linkCopiedMessage)
                        }
                    }
                }
            },
            accept = { viewModel.respondToFollowRequest(it, accept = true) },
            reject = { viewModel.respondToFollowRequest(it, accept = false) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReadyContent(
    state: NotificationsUiState.Ready,
    unreadCount: Int,
    onFilter: (NotificationFilter) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMarkAllRead: () -> Unit,
    actions: NotificationActions,
) {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val gutter = if (wide) 24.dp else 16.dp
    val refreshing = state.loading && state.items.isNotEmpty()
    val pullState = rememberPullToRefreshState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val isRefreshKey = event.key == Key.F5 || (event.isCtrlPressed && event.key == Key.R)
                if (event.type == KeyEventType.KeyDown && isRefreshKey) {
                    onRefresh()
                    true
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(
            filter = state.filter,
            unreadCount = unreadCount,
            gutter = gutter,
            onFilter = onFilter,
            onRefresh = onRefresh,
            onMarkAllRead = onMarkAllRead,
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            when {
                state.items.isEmpty() && state.loading -> LoadingState()
                state.items.isEmpty() && state.failed -> FailedState(onRetry = onRetry)
                state.items.isEmpty() -> EmptyState(filtered = state.filter != NotificationFilter.All)
                else -> NotificationList(
                    state = state,
                    gutter = gutter,
                    onLoadMore = onLoadMore,
                    onRetry = onRetry,
                    actions = actions,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(
    filter: NotificationFilter,
    unreadCount: Int,
    gutter: Dp,
    onFilter: (NotificationFilter) -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = gutter)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.notifications_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .semantics { heading() },
            )
            TooltipIconButton(
                label = stringResource(R.string.notifications_mark_all_read),
                onClick = onMarkAllRead,
                enabled = unreadCount > 0,
                icon = Icons.Default.DoneAll,
            )
            TooltipIconButton(
                label = stringResource(R.string.notifications_refresh),
                onClick = onRefresh,
                icon = Icons.Default.Refresh,
            )
        }
        FilterChips(selected = filter, onSelect = onFilter)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}

@Composable
private fun FilterChips(selected: NotificationFilter, onSelect: (NotificationFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NotificationFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes)) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@get:StringRes
private val NotificationFilter.labelRes: Int
    get() = when (this) {
        NotificationFilter.All -> R.string.notifications_filter_all
        NotificationFilter.Mentions -> R.string.notifications_filter_mentions
        NotificationFilter.Reactions -> R.string.notifications_filter_reactions
        NotificationFilter.Renotes -> R.string.notifications_filter_renotes
        NotificationFilter.Follows -> R.string.notifications_filter_follows
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationList(
    state: NotificationsUiState.Ready,
    gutter: Dp,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    actions: NotificationActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val now by rememberNow()
    val items = state.items

    LaunchedEffect(listState, state.filter) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            (lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { currentOnLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        itemsIndexed(
            items = items,
            key = { _, notification: FortNotification -> notification.id },
            contentType = { _, _ -> "notification" },
        ) { index, notification ->
            NotificationItem(
                notification = notification,
                followProgress = state.followRequests[notification.id],
                shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size),
                now = now,
                actions = actions,
                modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
            )
        }
        if (state.loadingMore) {
            item(key = "loading-more", contentType = "footer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!reduceMotion) LoadingIndicator()
                }
            }
        }
        if (state.failed) {
            item(key = "load-more-failed", contentType = "footer") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.notifications_load_more_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.notifications_retry)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!rememberReduceMotion()) LoadingIndicator()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(filtered: Boolean) {
    CenteredMessage {
        StatusMessage(
            shape = MaterialShapes.Cookie9Sided,
            title = stringResource(
                if (filtered) R.string.notifications_empty_filtered_title else R.string.notifications_empty_title
            ),
            body = stringResource(
                if (filtered) R.string.notifications_empty_filtered_body else R.string.notifications_empty_body
            ),
            actions = {},
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FailedState(onRetry: () -> Unit) {
    CenteredMessage {
        StatusMessage(
            shape = MaterialShapes.Sunny,
            title = stringResource(R.string.notifications_failed_title),
            body = stringResource(R.string.notifications_failed_body),
        ) {
            PrimaryButton(
                label = stringResource(R.string.notifications_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GuestState(onLogIn: () -> Unit) {
    CenteredMessage {
        StatusMessage(
            shape = MaterialShapes.Cookie9Sided,
            title = stringResource(R.string.notifications_guest_title),
            body = stringResource(R.string.notifications_guest_body),
        ) {
            PrimaryButton(
                label = stringResource(R.string.notifications_guest_log_in),
                onClick = onLogIn,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionState() {
    val login: LoginViewModel = viewModel(key = "reauthorize", factory = LoginViewModel.Factory)
    val loginState by login.uiState.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    AuthPageEffects(login)

    CenteredMessage {
        StatusMessage(
            shape = MaterialShapes.Slanted,
            title = stringResource(R.string.notifications_permission_title),
            body = stringResource(R.string.notifications_permission_body, FortInstance.HOST),
        ) {
            loginState.error?.let { ErrorMessage(it) }
            if (loginState.signingIn && !reduceMotion) {
                LoadingIndicator()
                TextButton(onClick = login::onCancel) { Text(stringResource(R.string.login_cancel)) }
            } else if (loginState.signingIn) {
                Text(stringResource(R.string.login_signing_in), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = login::onCancel) { Text(stringResource(R.string.login_cancel)) }
            } else {
                PrimaryButton(
                    label = stringResource(R.string.notifications_permission_grant),
                    onClick = login::onSignInClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.widthIn(max = 480.dp)) { content() }
    }
}

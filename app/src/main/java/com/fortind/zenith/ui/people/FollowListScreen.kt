package com.fortind.zenith.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.R
import com.fortind.zenith.mfm.MfmText
import com.fortind.zenith.people.FollowListKind
import com.fortind.zenith.people.FollowState
import com.fortind.zenith.people.UserDetail
import com.fortind.zenith.people.UserListRow
import com.fortind.zenith.people.followState
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.PrimaryButton
import com.fortind.zenith.ui.StatusMessage
import com.fortind.zenith.ui.contextMenuTrigger
import com.fortind.zenith.ui.feed.FeedLoading
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.settings.ReauthorizePrompt
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import com.fortind.zenith.ui.social.SocialUi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val LOAD_MORE_THRESHOLD = 6
private val RowMinWidth = 340.dp
private val ValidHost = Regex("[A-Za-z0-9.-]{1,253}")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FollowListScreen(
    userId: String,
    initialKind: FollowListKind,
    ui: SocialUi,
    onBack: () -> Unit,
    onOpenPerson: (UserDetail) -> Unit,
) {
    val people by ui.people.uiState.collectAsStateWithLifecycle()
    val owner = people.person(userId).detail
    val isOwn = userId == people.myUserId
    var kind by rememberSaveable(userId) { mutableStateOf(initialKind) }
    val showRequests = isOwn && (initialKind == FollowListKind.Requests || owner?.isLocked == true || (people.pending?.count ?: 0) > 0)
    val kinds = remember(showRequests) {
        if (showRequests) FollowListKind.entries.toList() else listOf(FollowListKind.Following, FollowListKind.Followers)
    }
    val key = FollowListKey(userId, kind)
    val list = people.list(key)
    val environment = LocalNoteEnvironment.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(ui.people, key, list.status) {
        if (list.status == UserListStatus.Idle) ui.people.loadList(key)
    }
    LaunchedEffect(ui.people, userId) { ui.people.open(userId) }
    val currentKey by rememberUpdatedState(key)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { ui.people.loadMore(currentKey) }
    }
    val currentKinds by rememberUpdatedState(kinds)
    val currentKind by rememberUpdatedState(kind)
    DisposableEffect(ui.pageKeys, userId) {
        val target = PageKeyTarget(
            onRefresh = { ui.people.loadList(currentKey) },
            onMoveTab = { step ->
                val index = currentKinds.indexOf(currentKind)
                kind = currentKinds[(index + step).mod(currentKinds.size)]
            },
        )
        ui.pageKeys.target = target
        onDispose { if (ui.pageKeys.target === target) ui.pageKeys.target = null }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { owner?.user?.let { PersonTitle(it, environment) } },
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.person_back), icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                },
                actions = {
                    TooltipIconButton(
                        label = stringResource(R.string.person_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = { ui.people.loadList(key) },
                    )
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            SegmentTabs(
                count = kinds.size,
                isSelected = { kinds[it] == kind },
                onSelect = { kind = kinds[it] },
                label = { stringResource(kinds[it].labelRes()) },
                description = { stringResource(kinds[it].labelRes()) },
                testTag = { "follows:tab:${kinds[it].name}" },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when (list.status) {
                    UserListStatus.Idle, UserListStatus.Loading -> FeedLoading(Modifier.fillMaxWidth().padding(top = 48.dp))
                    UserListStatus.Hidden -> ListMessage(stringResource(R.string.follows_hidden_title), stringResource(R.string.follows_hidden_body))
                    UserListStatus.NeedsPermission -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.widthIn(max = 480.dp)) { ReauthorizePrompt() }
                    }
                    UserListStatus.Failed -> ListMessage(
                        title = stringResource(R.string.follows_failed_title),
                        body = stringResource(R.string.follows_failed_body),
                        action = stringResource(R.string.feed_retry),
                        onAction = { ui.people.loadList(key) },
                    )
                    UserListStatus.Ready -> UserGrid(
                        rows = list.rows,
                        loadMoreFailed = list.loadMoreFailed,
                        loadingMore = list.loadingMore,
                        kind = kind,
                        owner = owner,
                        myUserId = people.myUserId,
                        people = people,
                        environment = environment,
                        ui = ui,
                        gridState = gridState,
                        onOpenPerson = onOpenPerson,
                        onRetryMore = { ui.people.loadMore(key) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListMessage(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.widthIn(max = 480.dp)) {
            StatusMessage(shape = MaterialShapes.Sunny, title = title, body = body) {
                if (action != null) PrimaryButton(action, onAction)
            }
        }
    }
}

@Composable
private fun UserGrid(
    rows: List<UserListRow>,
    loadMoreFailed: Boolean,
    loadingMore: Boolean,
    kind: FollowListKind,
    owner: UserDetail?,
    myUserId: String?,
    people: PeopleUiState,
    environment: NoteEnvironment,
    ui: SocialUi,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenPerson: (UserDetail) -> Unit,
    onRetryMore: () -> Unit,
) {
    val fullSpan: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(RowMinWidth),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("follows:list"),
    ) {
        val remoteHost = owner?.user?.host?.takeIf(ValidHost::matches)
        if (remoteHost != null && kind != FollowListKind.Requests) {
            item(key = "notice", contentType = "notice", span = fullSpan) {
                RemoteListNotice(owner, remoteHost, kind, ui)
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty", contentType = "empty", span = fullSpan) {
                Text(
                    text = stringResource(kind.emptyRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        items(rows, key = { it.pagingId }, contentType = { "user" }) { row ->
            UserRow(
                detail = row.detail,
                myUserId = myUserId,
                busy = people.person(row.detail.id).busy,
                request = kind == FollowListKind.Requests,
                answer = people.answers[row.detail.id],
                environment = environment,
                ui = ui,
                onOpen = { onOpenPerson(row.detail) },
            )
        }
        if (loadingMore) {
            item(key = "more", contentType = "more", span = fullSpan) { FeedLoading(Modifier.padding(16.dp)) }
        }
        if (loadMoreFailed) {
            item(key = "retry", contentType = "retry", span = fullSpan) {
                TextButton(onClick = onRetryMore) { Text(stringResource(R.string.feed_retry)) }
            }
        }
    }
}

@Composable
private fun RemoteListNotice(owner: UserDetail, host: String, kind: FollowListKind, ui: SocialUi) {
    val path = if (kind == FollowListKind.Following) "following" else "followers"
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.follows_remote_notice, owner.user.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { ui.peopleActions.openInBrowser("https://$host/@${owner.user.username}/$path") }) {
            Text(stringResource(R.string.follows_remote_open, host))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UserRow(
    detail: UserDetail,
    myUserId: String?,
    busy: Boolean,
    request: Boolean,
    answer: RequestAnswer?,
    environment: NoteEnvironment,
    ui: SocialUi,
    onOpen: () -> Unit,
) {
    var menuOffset by remember { mutableStateOf<DpOffset?>(null) }
    val nameEnvironment = remember(detail.user, environment.mfm) { environment.mfm.withEmoji(environment.userEmoji(detail.user)) }
    Box {
        SegmentedListItem(
            onClick = onOpen,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            onLongClick = { menuOffset = DpOffset.Zero },
            onLongClickLabel = stringResource(R.string.person_more),
            leadingContent = { Avatar(name = detail.user.displayName, url = detail.user.avatarUrl, size = 48.dp) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(detail.user.fullHandle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (request) RequestActions(detail, answer, ui)
                }
            },
            trailingContent = if (request) {
                null
            } else {
                { RowFollowButton(detail, myUserId, busy, ui) }
            },
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .contextMenuTrigger(onOpen = { menuOffset = it })
                .testTag("follows:row"),
        ) {
            MfmContent(
                nodes = detail.user.nameMfm.ifEmpty { listOf(MfmText(detail.user.username)) },
                environment = nameEnvironment,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
        }
        menuOffset?.let { offset ->
            DropdownMenu(expanded = true, onDismissRequest = { menuOffset = null }, offset = offset) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.person_menu_view)) },
                    onClick = {
                        menuOffset = null
                        onOpen()
                    },
                )
                PersonMenuItems(detail.user, detail, myUserId, myUserId != null, ui.peopleActions) { menuOffset = null }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowFollowButton(detail: UserDetail, myUserId: String?, busy: Boolean, ui: SocialUi) {
    val state = detail.followState(myUserId)
    if (state == FollowState.Hidden) return
    val label = stringResource(state.labelRes())
    val spoken = stringResource(R.string.person_follow_row_description, label, detail.user.displayName)
    val semantic = Modifier.semantics { contentDescription = spoken }
    if (state.invitesFollow) {
        Button(onClick = { ui.peopleActions.toggleFollow(detail) }, enabled = !busy, modifier = semantic) { Text(label, maxLines = 1) }
    } else {
        FilledTonalButton(onClick = { ui.peopleActions.toggleFollow(detail) }, enabled = !busy, modifier = semantic) { Text(label, maxLines = 1) }
    }
}

@Composable
private fun RequestActions(detail: UserDetail, answer: RequestAnswer?, ui: SocialUi) {
    when (answer) {
        RequestAnswer.Accepted -> Text(stringResource(R.string.notifications_accepted), style = MaterialTheme.typography.labelLarge)
        RequestAnswer.Rejected -> Text(stringResource(R.string.notifications_rejected), style = MaterialTheme.typography.labelLarge)
        RequestAnswer.Gone -> Text(stringResource(R.string.follows_request_gone), style = MaterialTheme.typography.labelLarge)
        else -> androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val inFlight = answer != null
            Button(onClick = { ui.people.answerRequest(detail.id, accept = true) }, enabled = !inFlight) {
                Text(stringResource(R.string.notifications_accept))
            }
            FilledTonalButton(onClick = { ui.people.answerRequest(detail.id, accept = false) }, enabled = !inFlight) {
                Text(stringResource(R.string.notifications_reject))
            }
        }
    }
}

private fun FollowListKind.labelRes(): Int = when (this) {
    FollowListKind.Following -> R.string.follows_tab_following
    FollowListKind.Followers -> R.string.follows_tab_followers
    FollowListKind.Requests -> R.string.follows_tab_requests
}

private fun FollowListKind.emptyRes(): Int = when (this) {
    FollowListKind.Following -> R.string.follows_empty_following
    FollowListKind.Followers -> R.string.follows_empty_followers
    FollowListKind.Requests -> R.string.follows_empty_requests
}

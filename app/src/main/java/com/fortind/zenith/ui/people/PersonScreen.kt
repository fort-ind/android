package com.fortind.zenith.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.R
import com.fortind.zenith.people.FollowListKind
import com.fortind.zenith.timeline.NoteListKey
import com.fortind.zenith.timeline.UserNotesTab
import com.fortind.zenith.ui.PrimaryButton
import com.fortind.zenith.ui.StatusMessage
import com.fortind.zenith.ui.feed.FeedLoading
import com.fortind.zenith.ui.feed.FeedMaxWidth
import com.fortind.zenith.ui.feed.TimelineList
import com.fortind.zenith.ui.feed.TimelineListCallbacks
import com.fortind.zenith.ui.feed.TimelineStatus
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.home.ReauthorizeBanner
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import com.fortind.zenith.ui.social.SocialUi

val AboutColumnWidth: Dp = 300.dp
val PersonListWidth: Dp = 440.dp

enum class AboutPlacement { Header, DetailPane, Column }

enum class PersonTab(val labelRes: Int, val descriptionRes: Int, val notesTab: UserNotesTab?) {
    Notes(R.string.person_tab_notes, R.string.person_tab_notes, UserNotesTab.Notes),
    Replies(R.string.person_tab_replies, R.string.person_tab_replies_description, UserNotesTab.NotesAndReplies),
    Media(R.string.person_tab_media, R.string.person_tab_media, UserNotesTab.Media),
    Bookmarks(R.string.person_tab_bookmarks, R.string.person_tab_bookmarks, null),
}

@Composable
fun rememberAboutPlacement(): AboutPlacement {
    val size = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        size.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> AboutPlacement.Column
        size.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) -> AboutPlacement.DetailPane
        else -> AboutPlacement.Header
    }
}

private fun PersonTab.listKey(userId: String): NoteListKey = when (val tab = notesTab) {
    null -> NoteListKey.Bookmarks
    else -> NoteListKey.UserNotes(userId, tab)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonPage(
    userId: String,
    ui: SocialUi,
    onBack: (() -> Unit)?,
    onOpenList: (FollowListKind) -> Unit,
    own: OwnProfileActions? = null,
) {
    val people by ui.people.uiState.collectAsStateWithLifecycle()
    val feed by ui.feed.uiState.collectAsStateWithLifecycle()
    val person = people.person(userId)
    val detail = person.detail
    val user = person.user
    val environment = LocalNoteEnvironment.current
    val reduceMotion = rememberReduceMotion()
    val placement = rememberAboutPlacement()
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) && placement == AboutPlacement.Header
    val tabs = remember(own != null) { if (own != null) PersonTab.entries.toList() else PersonTab.entries.filter { it != PersonTab.Bookmarks } }
    var tab by rememberSaveable(userId) { mutableStateOf(PersonTab.Notes) }
    val listState = rememberSaveable(userId, saver = LazyListState.Saver) { LazyListState() }
    val key = tab.listKey(userId)
    val timeline = feed.list(key)
    val pinnedKey = NoteListKey.Pinned(userId)
    val status = person.status

    LaunchedEffect(ui.people, userId) { ui.people.open(userId) }
    LaunchedEffect(ui.people, userId, status) {
        if (status == PersonStatus.Idle) ui.people.open(userId)
    }
    LaunchedEffect(ui.people, own != null) { if (own != null) ui.people.refreshPending() }
    val listIdle = timeline.status == TimelineStatus.Idle
    LaunchedEffect(ui.feed, key, listIdle, feed.signedIn) {
        if (listIdle && (key != NoteListKey.Bookmarks || feed.signedIn)) ui.feed.loadList(key)
    }
    val pinnedIdle = feed.list(pinnedKey).status == TimelineStatus.Idle
    LaunchedEffect(ui.feed, detail?.pinnedNotes, pinnedIdle) {
        detail?.pinnedNotes?.let { ui.feed.seedList(pinnedKey, it) }
    }
    LaunchedEffect(tab) {
        if (listState.firstVisibleItemIndex > TABS_ITEM_INDEX) listState.scrollToItem(TABS_ITEM_INDEX)
    }

    val refresh = {
        ui.people.refresh(userId)
        ui.feed.loadList(key)
    }
    val currentRefresh by rememberUpdatedState(refresh)
    val currentTabs by rememberUpdatedState(tabs)
    val currentTab by rememberUpdatedState(tab)
    val moveTab = { step: Int ->
        val index = currentTabs.indexOf(currentTab)
        tab = currentTabs[(index + step).mod(currentTabs.size)]
    }
    DisposableEffect(ui.pageKeys, userId) {
        val target = PageKeyTarget(onRefresh = { currentRefresh() }, onMoveTab = { moveTab(it) })
        ui.pageKeys.target = target
        onDispose { if (ui.pageKeys.target === target) ui.pageKeys.target = null }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.Tab) {
                    moveTab(if (event.isShiftPressed) -1 else 1)
                    true
                } else {
                    false
                }
            }
            .testTag("person:page"),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { user?.let { PersonTitle(it, environment) } },
                navigationIcon = {
                    if (onBack != null) {
                        TooltipIconButton(
                            label = stringResource(R.string.person_back),
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onBack,
                        )
                    }
                },
                actions = {
                    TooltipIconButton(label = stringResource(R.string.person_refresh), icon = Icons.Default.Refresh, onClick = refresh)
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                user == null && status == PersonStatus.NotFound -> PersonMessage(
                    title = stringResource(R.string.person_not_found_title),
                    body = stringResource(R.string.person_not_found_body),
                )
                user == null && status == PersonStatus.Failed -> PersonMessage(
                    title = stringResource(R.string.person_failed_title),
                    body = stringResource(R.string.person_failed_body),
                    actionLabel = stringResource(R.string.feed_retry),
                    onAction = { ui.people.refresh(userId) },
                )
                user == null -> FeedLoading(Modifier.fillMaxWidth().padding(top = 48.dp))
                else -> {
                    val header = @Composable {
                        PersonHeader(
                            user = user,
                            detail = detail,
                            environment = environment,
                            wide = wide,
                            busy = person.busy,
                            own = own,
                            pending = people.pending,
                            actions = ui.peopleActions,
                            onOpenList = onOpenList,
                            aboutInHeader = placement == AboutPlacement.Header,
                        )
                    }
                    val limited = detail != null && (detail.isSuspended || detail.hasMoved)
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (placement == AboutPlacement.Column && detail != null) {
                            Column(
                                modifier = Modifier
                                    .width(AboutColumnWidth)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(stringResource(R.string.person_about_title), style = MaterialTheme.typography.titleMedium)
                                PersonAboutContent(detail, environment)
                            }
                            VerticalDivider()
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (limited) {
                                LimitedAccount(header, detail, ui.peopleActions)
                            } else {
                                val listCallbacks = remember(ui.feed, key) {
                                    TimelineListCallbacks(
                                        onLoadMore = { ui.feed.loadMoreList(key) },
                                        onRetry = { ui.feed.loadList(key) },
                                        onRefresh = { currentRefresh() },
                                        onShowPending = {},
                                        onLogIn = ui.feed::logIn,
                                        onAtTop = {},
                                        onVisibleNotes = {},
                                    )
                                }
                                TimelineList(
                                    kind = null,
                                    timeline = timeline,
                                    listState = listState,
                                    environment = environment,
                                    noteCallbacks = ui.callbacks,
                                    callbacks = listCallbacks,
                                    reduceMotion = reduceMotion,
                                    fullHeader = header,
                                    stickyHeader = {
                                        PersonTabs(tabs = tabs, selected = tab, onSelect = { tab = it })
                                    },
                                    pinned = if (tab == PersonTab.Notes) feed.list(pinnedKey).items else emptyList(),
                                    banner = if (feed.needsPermission) {
                                        { ReauthorizeBanner(onDismiss = ui.feed::permissionPromptHandled) }
                                    } else {
                                        null
                                    },
                                    emptyBody = if (tab == PersonTab.Bookmarks) R.string.bookmarks_empty_body else R.string.person_notes_empty,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val TABS_ITEM_INDEX = 1

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonTabs(tabs: List<PersonTab>, selected: PersonTab, onSelect: (PersonTab) -> Unit, modifier: Modifier = Modifier) {
    SegmentTabs(
        count = tabs.size,
        isSelected = { tabs[it] == selected },
        onSelect = { onSelect(tabs[it]) },
        label = { stringResource(tabs[it].labelRes) },
        description = { stringResource(tabs[it].descriptionRes) },
        testTag = { "person:tab:${tabs[it].name}" },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentTabs(
    count: Int,
    isSelected: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    label: @Composable (Int) -> String,
    description: @Composable (Int) -> String,
    testTag: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .widthIn(max = FeedMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            repeat(count) { index ->
                val spoken = description(index)
                ToggleButton(
                    checked = isSelected(index),
                    onCheckedChange = { onSelect(index) },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = ToggleButtonDefaults.contentPaddingFor(ToggleButtonDefaults.MinHeight),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(testTag(index))
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = spoken
                        },
                ) {
                    Text(label(index), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LimitedAccount(header: @Composable () -> Unit, detail: com.fortind.zenith.people.UserDetail, actions: PeopleActions) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.widthIn(max = FeedMaxWidth).fillMaxWidth()) { header() }
        Spacer(Modifier.size(24.dp))
        Box(modifier = Modifier.widthIn(max = 480.dp).padding(horizontal = 16.dp)) {
            StatusMessage(
                shape = MaterialShapes.Slanted,
                title = stringResource(if (detail.isSuspended) R.string.person_suspended_title else R.string.person_moved_title),
                body = stringResource(if (detail.isSuspended) R.string.person_suspended_body else R.string.person_moved_body),
            ) {
                PrimaryButton(stringResource(R.string.person_menu_open_browser), { actions.openInBrowser(detail.user.profileUrl) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonMessage(title: String, body: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.widthIn(max = 480.dp)) {
            StatusMessage(shape = MaterialShapes.Cookie9Sided, title = title, body = body) {
                if (actionLabel != null) PrimaryButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
fun PersonByHandlePage(
    username: String,
    host: String?,
    ui: SocialUi,
    onBack: () -> Unit,
    onOpenList: (String, FollowListKind) -> Unit,
) {
    val people by ui.people.uiState.collectAsStateWithLifecycle()
    val handle = PeopleViewModel.handleKey(username, host)
    val userId = people.handles[handle]
    LaunchedEffect(ui.people, handle) { ui.people.openHandle(username, host) }
    if (userId != null) {
        PersonPage(userId = userId, ui = ui, onBack = onBack, onOpenList = { kind -> onOpenList(userId, kind) })
    } else if (handle in people.failedHandles) {
        PersonMessage(
            title = stringResource(R.string.person_not_found_title),
            body = stringResource(R.string.person_not_found_body),
            actionLabel = stringResource(R.string.feed_retry),
            onAction = { ui.people.openHandle(username, host) },
        )
    } else {
        FeedLoading(Modifier.fillMaxWidth().padding(top = 48.dp))
    }
}

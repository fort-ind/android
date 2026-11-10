@file:OptIn(
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package com.fortind.zenith.ui.nav

import androidx.compose.ui.platform.LocalWindowInfo
import com.fortind.zenith.window.AppWindows
import com.fortind.zenith.ui.contextMenuTrigger
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.foundation.layout.isCaptionBarVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.annotation.StringRes
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.ui.graphics.vector.ImageVector
import com.fortind.zenith.timeline.TimelineKind
import com.fortind.zenith.ui.discovery.HashtagsPage
import com.fortind.zenith.ui.discovery.SearchResultsPage
import com.fortind.zenith.ui.discovery.TagPage
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneMotionDefaults
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.R
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.ui.feed.ThreadPane
import com.fortind.zenith.ui.feed.ThreadPlaceholder
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.games.GamesScreen
import com.fortind.zenith.ui.games.GamesViewModel
import com.fortind.zenith.ui.home.HomeDetailPlaceholder
import com.fortind.zenith.ui.home.HomeScreen
import com.fortind.zenith.ui.notifications.NotificationsScreen
import com.fortind.zenith.ui.notifications.NotificationsViewModel
import com.fortind.zenith.ui.people.FollowListScreen
import com.fortind.zenith.ui.people.PeopleViewModel
import com.fortind.zenith.ui.people.PersonByHandlePage
import com.fortind.zenith.ui.people.PersonLayout
import com.fortind.zenith.ui.people.PersonListWidth
import com.fortind.zenith.ui.people.PersonPage
import com.fortind.zenith.ui.people.PersonPostsPane
import com.fortind.zenith.ui.people.rememberPersonLayout
import com.fortind.zenith.ui.profile.ProfileScreen
import com.fortind.zenith.ui.predictiveBackScale
import com.fortind.zenith.ui.predictiveBackSeekFraction
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.motion.EntryVisibilityScope
import com.fortind.zenith.ui.motion.LocalSharedTransitionScope
import com.fortind.zenith.ui.motion.rememberThreadBounds
import com.fortind.zenith.ui.motion.threadBounds
import com.fortind.zenith.ui.settings.SettingsScreen
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import com.fortind.zenith.ui.social.SocialHost
import com.fortind.zenith.ui.social.SocialUi
import kotlinx.coroutines.launch

private const val MAX_BADGE_COUNT = 99
private val ListPaneWidth = 440.dp
private val RailActionMargin = 20.dp
private val CollapsedRailWidth = 96.dp
private val SecondaryRailMinHeight = 720.dp
private val DefaultSplitOffset = 414.dp
private val DetailCollapsedAnchor = PaneExpansionAnchor.Proportion(1f)
private val PaneExpansionAnchors = listOf(
    PaneExpansionAnchor.Proportion(0f),
    PaneExpansionAnchor.Offset.fromStart(DefaultSplitOffset),
    DetailCollapsedAnchor,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppShell(
    window: ShellWindow,
    requestedDestination: AppDestination?,
    onRequestedDestinationHandled: () -> Unit,
    requestedRoute: AppRoute?,
    onRequestedRouteHandled: () -> Unit,
    requestedThreadId: String?,
    onRequestedThreadHandled: () -> Unit,
    requestedReplyId: String?,
    onRequestedReplyHandled: () -> Unit,
    shortcuts: AppShortcutDispatcher,
) {
    val poppedOut = window.poppedOutTab != null
    val stacks = rememberAppBackStacks(window.poppedOutTab)
    stacks.onLeaveFixedTab = window.onLeaveTab
    val canPopOut = !poppedOut && WindowInsets.isCaptionBarVisible
    val app = LocalContext.current.applicationContext as ZenithApplication
    val scope = rememberCoroutineScope()
    val settings by app.container.settingsStore.settings.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val gamesViewModel: GamesViewModel = viewModel(factory = GamesViewModel.Factory)
    val notificationsViewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory)
    val unreadCount by notificationsViewModel.unreadCount.collectAsStateWithLifecycle()

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val sizeClass = adaptiveInfo.windowSizeClass
    val directive = remember(adaptiveInfo) { calculatePaneScaffoldDirective(adaptiveInfo) }
    val paneExpansion = rememberPaneExpansionState(anchors = PaneExpansionAnchors)
    val detailCollapsed = paneExpansion.currentAnchor == DetailCollapsedAnchor
    val twoPane = directive.maxHorizontalPartitions > 1 && !detailCollapsed
    val hasRail = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val hasWideRail = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)
    val composeInRail = !poppedOut && hasRail && sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val railExpanded = hasWideRail && settings?.railCollapsed != true
    val suiteType = when {
        poppedOut -> NavigationSuiteType.None
        railExpanded -> NavigationSuiteType.WideNavigationRailExpanded
        hasRail -> NavigationSuiteType.WideNavigationRailCollapsed
        else -> NavigationSuiteType.ShortNavigationBarCompact
    }

    LaunchedEffect(requestedDestination, requestedRoute) {
        if (requestedDestination != null) {
            when {
                requestedRoute == AppRoute.Settings(openEditProfile = true) -> stacks.openEditProfile()
                requestedRoute != null && requestedRoute != requestedDestination.root -> stacks.openIn(requestedDestination, requestedRoute)
                else -> stacks.select(requestedDestination)
            }
            onRequestedRouteHandled()
            onRequestedDestinationHandled()
        }
    }
    LaunchedEffect(requestedThreadId) {
        if (requestedThreadId != null) {
            stacks.openIn(AppDestination.HOME, AppRoute.Thread(requestedThreadId))
            onRequestedThreadHandled()
        }
    }
    LifecycleStartEffect(notificationsViewModel) {
        notificationsViewModel.setForeground(true)
        onStopOrDispose { notificationsViewModel.setForeground(false) }
    }

    val navigationState = rememberNavigationSuiteScaffoldState()
    val hideBar = !hasRail && stacks.top.isDetailPage
    LaunchedEffect(hideBar) { if (hideBar) navigationState.hide() else navigationState.show() }

    SocialHost(
        requestedReplyId = requestedReplyId,
        onRequestedReplyHandled = onRequestedReplyHandled,
        selectedNoteId = stacks.openThreadId?.takeIf { twoPane },
        onOpenThread = { note -> stacks.push(AppRoute.Thread(note.id)) },
        onOpenPerson = { userId -> stacks.push(AppRoute.Person(userId)) },
        onOpenOwnProfile = { stacks.select(AppDestination.PROFILE) },
        onOpenHandle = { handle -> stacks.push(AppRoute.PersonHandle(handle.username, handle.host)) },
        onOpenTag = { tag -> stacks.push(AppRoute.Tag(tag)) },
    ) { ui ->
        val feedState by ui.feed.uiState.collectAsStateWithLifecycle()
        val feedActive = stacks.tab == AppDestination.HOME || stacks.current.any { it is AppRoute.Thread }
        LifecycleStartEffect(ui.feed, feedActive) {
            ui.feed.setActive(feedActive)
            onStopOrDispose { ui.feed.setActive(false) }
        }
        LaunchedEffect(stacks.tab) { ui.playback.stop() }
        val openThreadId = stacks.openThreadId
        LaunchedEffect(openThreadId) {
            if (openThreadId != null) ui.feed.openThread(openThreadId) else ui.feed.closeThread()
        }

        val tabBackEnabled = !poppedOut && !stacks.atHomeRoot && stacks.current.size == 1
        val tabBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = tabBackState,
            isBackEnabled = tabBackEnabled,
            onBackCompleted = { stacks.back() },
        )
        val tabBackProgress = (tabBackState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress
        val tabBackPreview = tabBackProgress?.takeIf { tabBackEnabled }?.let(::predictiveBackSeekFraction)

        DisposableEffect(shortcuts, ui, stacks, notificationsViewModel) {
            shortcuts.handler = handler@{ shortcut ->
                if (ui.viewer.isOpen) {
                    if (shortcut == AppShortcut.Dismiss) ui.viewer.close()
                    return@handler true
                }
                when (shortcut) {
                    is AppShortcut.Destination -> {
                        stacks.select(shortcut.destination)
                        true
                    }
                    AppShortcut.NewPost -> {
                        ui.startNewPost()
                        true
                    }
                    AppShortcut.Refresh -> {
                        val page = ui.pageKeys.target
                        when {
                            page != null -> {
                                page.onRefresh()
                                true
                            }
                            stacks.tab == AppDestination.HOME -> {
                                ui.feed.refresh()
                                true
                            }
                            stacks.tab == AppDestination.NOTIFICATIONS -> {
                                notificationsViewModel.refresh()
                                true
                            }
                            else -> false
                        }
                    }
                    AppShortcut.Find -> {
                        if (stacks.tab == AppDestination.SETTINGS) {
                            false
                        } else {
                            stacks.showRoot(AppDestination.HOME)
                            ui.discovery.requestSearch()
                            true
                        }
                    }
                    AppShortcut.Dismiss -> stacks.pop()
                    is AppShortcut.MoveTab -> ui.pageKeys.target?.let { page ->
                        page.onMoveTab(shortcut.step)
                        true
                    } ?: false
                }
            }
            onDispose { shortcuts.handler = null }
        }

        NavigationSuiteScaffold(
            navigationSuiteType = suiteType,
            state = navigationState,
            primaryActionContent = {
                if (hasWideRail || composeInRail) {
                    RailActions(
                        expanded = railExpanded,
                        showToggle = hasWideRail,
                        showCompose = composeInRail,
                        onCompose = ui.startNewPost,
                        onToggle = { scope.launch { app.container.settingsStore.setRailCollapsed(railExpanded) } },
                    )
                }
            },
            navigationItems = {
                AppDestination.entries.forEach { destination ->
                    val poppable = canPopOut && destination in AppWindows.PoppableTabs
                    var menuOpen by remember { mutableStateOf(false) }
                    NavigationSuiteItem(
                        selected = stacks.tab == destination,
                        onClick = { stacks.select(destination) },
                        navigationSuiteType = suiteType,
                        modifier = Modifier
                            .testTag("nav:${destination.name}")
                            .contextMenuTrigger(enabled = poppable) { menuOpen = true },
                        icon = {
                            Box {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = if (destination == AppDestination.NOTIFICATIONS && unreadCount > 0) {
                                        pluralStringResource(R.plurals.nav_notifications_unread, unreadCount, unreadCount)
                                    } else {
                                        stringResource(destination.labelRes)
                                    },
                                )
                                DropdownMenu(expanded = menuOpen && poppable, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.nav_open_in_new_window)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            window.onOpenTabWindow(destination, settings?.popOutBounds?.get(destination.name))
                                        },
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                stringResource(
                                    if (destination == AppDestination.NOTIFICATIONS && !railExpanded) {
                                        R.string.nav_notifications_short
                                    } else {
                                        destination.labelRes
                                    },
                                ),
                            )
                        },
                        badge = if (destination == AppDestination.NOTIFICATIONS && unreadCount > 0) {
                            { Badge { Text(if (unreadCount > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else unreadCount.toString()) } }
                        } else {
                            null
                        },
                    )
                }
                if (railExpanded && LocalWindowInfo.current.containerDpSize.height >= SecondaryRailMinHeight) {
                    RailSecondaryItem(R.string.nav_search, Icons.Default.Search, "nav:search", suiteType) {
                        stacks.showRoot(AppDestination.HOME)
                        ui.discovery.requestSearch()
                    }
                    RailSecondaryItem(R.string.nav_lists, Icons.AutoMirrored.Filled.FormatListBulleted, "nav:lists", suiteType) {
                        stacks.showRoot(AppDestination.HOME)
                        ui.feed.select(TimelineKind.Lists)
                    }
                    RailSecondaryItem(R.string.nav_hashtags, Icons.Default.Tag, "nav:hashtags", suiteType) {
                        stacks.openIn(AppDestination.HOME, AppRoute.Hashtags)
                    }
                    if (feedState.signedIn) {
                        RailSecondaryItem(R.string.nav_bookmarks, Icons.Default.BookmarkBorder, "nav:bookmarks", suiteType) {
                            ui.pageKeys.bookmarksRequested = true
                            stacks.showRoot(AppDestination.PROFILE)
                        }
                    }
                }
            },
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavDisplay(
                        stacks = stacks,
                        contentPadding = innerPadding,
                        ui = ui,
                        twoPane = twoPane,
                        paneExpansion = paneExpansion,
                        detailCollapsed = detailCollapsed,
                        showHomeComposeButton = !composeInRail,
                        gamesViewModel = gamesViewModel,
                        notificationsViewModel = notificationsViewModel,
                        backPreview = tabBackPreview,
                    )
                    SnackbarHost(ui.snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun RailSecondaryItem(
    @StringRes labelRes: Int,
    icon: ImageVector,
    tag: String,
    suiteType: NavigationSuiteType,
    onClick: () -> Unit,
) {
    NavigationSuiteItem(
        selected = false,
        onClick = onClick,
        navigationSuiteType = suiteType,
        modifier = Modifier.testTag(tag),
        icon = { Icon(imageVector = icon, contentDescription = stringResource(labelRes)) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun RailActions(
    expanded: Boolean,
    showToggle: Boolean,
    showCompose: Boolean,
    onCompose: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (expanded) Modifier.padding(start = RailActionMargin) else Modifier.width(CollapsedRailWidth))
            .padding(vertical = 8.dp),
    ) {
        if (showToggle) {
            TooltipIconButton(
                label = stringResource(if (expanded) R.string.nav_rail_collapse else R.string.nav_rail_expand),
                icon = if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                onClick = onToggle,
            )
        }
        if (showCompose) {
            val label = stringResource(R.string.feed_compose)
            ExtendedFloatingActionButton(
                onClick = onCompose,
                expanded = expanded,
                icon = { Icon(Icons.Default.Edit, contentDescription = label) },
                text = { Text(label) },
                modifier = Modifier.testTag("nav:compose"),
            )
        }
    }
}

@Composable
private fun ThreePaneScaffoldScope.PaneDragHandle(state: PaneExpansionState) {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier = Modifier.paneExpansionDraggable(
            state,
            LocalMinimumInteractiveComponentSize.current,
            interactionSource,
        ),
        interactionSource = interactionSource,
    )
}

@Composable
private fun AppNavDisplay(
    stacks: AppBackStacks,
    contentPadding: PaddingValues,
    ui: SocialUi,
    twoPane: Boolean,
    paneExpansion: PaneExpansionState,
    detailCollapsed: Boolean,
    showHomeComposeButton: Boolean,
    gamesViewModel: GamesViewModel,
    notificationsViewModel: NotificationsViewModel,
    backPreview: Float?,
) {
    val holder = rememberSaveableStateHolder()
    val reduceMotion = rememberReduceMotion()
    val previewFraction = backPreview.takeIf { !reduceMotion }
    val tabState = remember { SeekableTransitionState(stacks.tab) }
    LaunchedEffect(stacks.tab, previewFraction) {
        if (previewFraction != null) {
            tabState.seekTo(fraction = previewFraction, targetState = AppDestination.HOME)
        } else {
            tabState.animateTo(stacks.tab)
        }
    }
    val previewScale by animateFloatAsState(
        targetValue = previewFraction?.let(::predictiveBackScale) ?: 1f,
        label = "tabBackScale",
    )
    val slidesBack = previewFraction != null
    rememberTransition(tabState, label = "appTab").AnimatedContent(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = previewScale
                scaleY = previewScale
            },
        transitionSpec = {
            if (slidesBack && targetState == AppDestination.HOME) {
                slideIntoContainer(SlideDirection.End, PaneMotionDefaults.OffsetAnimationSpec) togetherWith
                    slideOutOfContainer(SlideDirection.End, PaneMotionDefaults.OffsetAnimationSpec)
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
    ) { shownTab ->
        holder.SaveableStateProvider(shownTab.name) {
            TabNavDisplay(
                tab = shownTab,
                stacks = stacks,
                contentPadding = contentPadding,
                ui = ui,
                twoPane = twoPane,
                paneExpansion = paneExpansion,
                detailCollapsed = detailCollapsed,
                showHomeComposeButton = showHomeComposeButton,
                gamesViewModel = gamesViewModel,
                notificationsViewModel = notificationsViewModel,
            )
        }
    }
}

@Composable
private fun TabNavDisplay(
    tab: AppDestination,
    stacks: AppBackStacks,
    contentPadding: PaddingValues,
    ui: SocialUi,
    twoPane: Boolean,
    paneExpansion: PaneExpansionState,
    detailCollapsed: Boolean,
    showHomeComposeButton: Boolean,
    gamesViewModel: GamesViewModel,
    notificationsViewModel: NotificationsViewModel,
) {
    val reduceMotion = rememberReduceMotion()
    val sharedScope = LocalSharedTransitionScope.current.takeIf { !reduceMotion }
    val people by ui.people.uiState.collectAsStateWithLifecycle()
    val personLayout = rememberPersonLayout()
    val browseGames = { stacks.select(AppDestination.GAMES) }
    val seeAllFavorites = {
        gamesViewModel.showFavorites()
        stacks.select(AppDestination.GAMES)
    }
    val detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = {
        Padded(contentPadding) {
            val listRoute = stacks.stack(tab).lastOrNull { it.isListPane }
            val personUserId = when (listRoute) {
                is AppRoute.Person -> listRoute.userId
                is AppRoute.PersonHandle -> people.handles[PeopleViewModel.handleKey(listRoute.username, listRoute.host)]
                AppRoute.Profile -> people.myUserId
                else -> null
            }
            when {
                listRoute == AppRoute.Home -> HomeDetailPlaceholder(ui, browseGames, seeAllFavorites)
                personLayout == PersonLayout.Split && personUserId != null -> PersonPostsPane(
                    userId = personUserId,
                    ui = ui,
                    isOwn = listRoute == AppRoute.Profile,
                )
                else -> ThreadPlaceholder()
            }
        }
    }
    val listMetadata = ListDetailSceneStrategy.listPane(detailPlaceholder = detailPlaceholder) +
        ListDetailSceneStrategy.preferredPaneSize(width = ListPaneWidth)
    val personMetadata = ListDetailSceneStrategy.listPane(detailPlaceholder = detailPlaceholder) +
        ListDetailSceneStrategy.preferredPaneSize(width = PersonListWidth)
    val listDetailStrategy = rememberListDetailSceneStrategy<AppRoute>(
        backNavigationBehavior = BackNavigationBehavior.PopUntilContentChange,
        paneExpansionDragHandle = { state -> PaneDragHandle(state) },
        paneExpansionState = paneExpansion,
    )
    val strategy = remember(listDetailStrategy, detailCollapsed) {
        DetailCollapsedSceneStrategy(listDetailStrategy, detailCollapsed)
    }
    NavDisplay(
        backStack = stacks.stack(tab),
        onBack = { stacks.pop() },
        sceneStrategies = listOf(strategy),
        sharedTransitionScope = sharedScope,
        transitionSpec = if (reduceMotion) ({ EnterTransition.None togetherWith ExitTransition.None }) else defaultTransitionSpec(),
        popTransitionSpec = if (reduceMotion) ({ EnterTransition.None togetherWith ExitTransition.None }) else defaultPopTransitionSpec(),
        predictivePopTransitionSpec = if (reduceMotion) ({ _ -> EnterTransition.None togetherWith ExitTransition.None }) else defaultPredictivePopTransitionSpec(),
        entryProvider = { route ->
            when (route) {
                AppRoute.Home -> NavEntry(
                    key = route,
                    metadata = listMetadata,
                ) {
                    EntryPage(contentPadding, ListDetailPaneScaffoldRole.List) {
                        HomeScreen(
                            ui = ui,
                            twoPane = twoPane,
                            showComposeButton = showHomeComposeButton,
                            onBrowseGames = browseGames,
                            onSeeAllFavorites = seeAllFavorites,
                            onOpenSearch = { query -> stacks.push(AppRoute.Search(query)) },
                            onOpenTag = { tag -> stacks.push(AppRoute.Tag(tag)) },
                            onOpenHandle = { username, host -> stacks.push(AppRoute.PersonHandle(username, host)) },
                        )
                    }
                }
                AppRoute.Games -> NavEntry(route) {
                    EntryPage(contentPadding, null) { GamesScreen(viewModel = gamesViewModel) }
                }
                AppRoute.Notifications -> NavEntry(
                    key = route,
                    metadata = listMetadata,
                ) {
                    EntryPage(contentPadding, ListDetailPaneScaffoldRole.List) {
                        NotificationsScreen(
                            viewModel = notificationsViewModel,
                            onOpenNote = { noteId -> stacks.push(AppRoute.Thread(noteId)) },
                            onOpenPerson = { userId ->
                                if (userId == people.myUserId) {
                                    stacks.select(AppDestination.PROFILE)
                                } else {
                                    stacks.push(AppRoute.Person(userId))
                                }
                            },
                        )
                    }
                }
                AppRoute.Profile -> NavEntry(
                    key = route,
                    metadata = if (people.myUserId != null) personMetadata else emptyMap(),
                ) {
                    EntryPage(contentPadding, ListDetailPaneScaffoldRole.List) {
                        ProfileScreen(
                            ui = ui,
                            onEditProfile = stacks::openEditProfile,
                            onOpenList = { userId, kind -> stacks.push(AppRoute.FollowList(userId, kind)) },
                        )
                    }
                }
                is AppRoute.Settings -> NavEntry(key = route, contentKey = "settings") {
                    EntryPage(contentPadding, null) {
                        SettingsScreen(
                            editProfileRequested = route.openEditProfile,
                            onEditProfileHandled = stacks::editProfileHandled,
                        )
                    }
                }
                is AppRoute.Person -> NavEntry(key = route, metadata = personMetadata) {
                    EntryPage(contentPadding, ListDetailPaneScaffoldRole.List) {
                        PersonPage(
                            userId = route.userId,
                            ui = ui,
                            onBack = { stacks.pop() },
                            onOpenList = { kind -> stacks.push(AppRoute.FollowList(route.userId, kind)) },
                        )
                    }
                }
                is AppRoute.PersonHandle -> NavEntry(key = route, metadata = personMetadata) {
                    EntryPage(contentPadding, ListDetailPaneScaffoldRole.List) {
                        PersonByHandlePage(
                            username = route.username,
                            host = route.host,
                            ui = ui,
                            onBack = { stacks.pop() },
                            onOpenList = { userId, kind -> stacks.push(AppRoute.FollowList(userId, kind)) },
                        )
                    }
                }
                is AppRoute.FollowList -> NavEntry(route) {
                    EntryPage(contentPadding, null) {
                        FollowListScreen(
                            userId = route.userId,
                            initialKind = route.kind,
                            ui = ui,
                            onBack = { stacks.pop() },
                            onOpenPerson = { detail -> ui.callbacks.openProfile(detail.user) },
                        )
                    }
                }
                is AppRoute.Tag -> NavEntry(key = route, metadata = listMetadata) {
                    EntryPage(contentPadding, ListDetailPaneScaffoldRole.List) { TagPage(route.tag, ui, onBack = { stacks.pop() }) }
                }
                is AppRoute.Search -> NavEntry(key = route, metadata = listMetadata) {
                    EntryPage(contentPadding, ListDetailPaneScaffoldRole.List) { SearchResultsPage(route.query, ui, onBack = { stacks.pop() }) }
                }
                AppRoute.Hashtags -> NavEntry(route) {
                    EntryPage(contentPadding, null) {
                        HashtagsPage(
                            discovery = ui.discovery,
                            onOpenTag = { tag -> stacks.push(AppRoute.Tag(tag)) },
                            onBack = { stacks.pop() },
                        )
                    }
                }
                is AppRoute.Thread -> NavEntry(
                    key = route,
                    metadata = ListDetailSceneStrategy.detailPane() + mapOf(DetailPaneMarker to true),
                ) {
                    val feed by ui.feed.uiState.collectAsStateWithLifecycle()
                    EntryPage(contentPadding, ListDetailPaneScaffoldRole.Detail) {
                        ThreadPane(
                            thread = feed.thread?.takeIf { it.noteId == route.noteId },
                            environment = LocalNoteEnvironment.current,
                            callbacks = ui.callbacks,
                            twoPane = twoPane,
                            onBack = { stacks.pop() },
                            onRetry = { ui.feed.openThread(route.noteId) },
                            onLoadMoreReplies = ui.feed::loadMoreReplies,
                            onToggleBranch = ui.feed::toggleBranch,
                            onLoadMoreBranch = ui.feed::loadMoreBranch,
                            onLoadRenotes = ui.feed::loadThreadRenotes,
                            onLoadReactions = ui.feed::loadThreadReactions,
                            modifier = Modifier.threadBounds(rememberThreadBounds(route.noteId, enabled = !reduceMotion), page = true),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun EntryPage(contentPadding: PaddingValues, paneRole: ThreePaneScaffoldRole?, content: @Composable () -> Unit) {
    EntryVisibilityScope(paneRole) { Padded(contentPadding, content) }
}

@Composable
private fun Padded(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)) { content() }
}

@file:OptIn(
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package com.fortind.zenith.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortind.zenith.R
import com.fortind.zenith.games.Game
import com.fortind.zenith.timeline.TimelineKind
import com.fortind.zenith.ui.feed.FeedMaxWidth
import com.fortind.zenith.ui.feed.FeedUiState
import com.fortind.zenith.ui.feed.FeedViewModel
import com.fortind.zenith.ui.feed.NewPostsPill
import com.fortind.zenith.ui.feed.NoteCallbacks
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.feed.PermissionBanner
import com.fortind.zenith.ui.feed.ThreadPlaceholder
import com.fortind.zenith.ui.feed.TimelineList
import com.fortind.zenith.ui.feed.TimelineListCallbacks
import com.fortind.zenith.ui.feed.TimelineSwitcher
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.games.FavoriteNoticeEffect
import com.fortind.zenith.ui.games.GameActions
import com.fortind.zenith.ui.games.GameItem
import com.fortind.zenith.ui.games.gameActions
import com.fortind.zenith.ui.login.AuthPageEffects
import com.fortind.zenith.ui.login.LoginViewModel
import com.fortind.zenith.ui.pointerHover
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import com.fortind.zenith.ui.social.SocialUi

@Composable
fun HomeScreen(
    ui: SocialUi,
    twoPane: Boolean,
    showComposeButton: Boolean,
    onBrowseGames: () -> Unit,
    onSeeAllFavorites: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val homeState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteNotice by viewModel.favoriteNotice.collectAsStateWithLifecycle()
    val feed by ui.feed.uiState.collectAsStateWithLifecycle()
    val meta by ui.feed.meta.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    val gameActions = gameActions(ui.snackbar, onToggleFavorite = viewModel::toggleFavorite)
    val ready = homeState as? HomeUiState.Ready

    FavoriteNoticeEffect(
        notice = favoriteNotice,
        snackbarHostState = ui.snackbar,
        onUndo = viewModel::undoFavorite,
        onShown = viewModel::favoriteNoticeShown,
    )
    FeedPane(
        feed = feed,
        listStates = ui.listStates,
        available = availableTimelines(meta?.ltlAvailable ?: true, meta?.gtlAvailable ?: true),
        environment = LocalNoteEnvironment.current,
        noteCallbacks = ui.callbacks,
        feedViewModel = ui.feed,
        reduceMotion = reduceMotion,
        onCompose = ui.startNewPost,
        showComposeButton = showComposeButton,
        favorites = if (ready != null && ready.showFavorites && !twoPane && ready.favorites.isNotEmpty()) {
            {
                FavoritesCarousel(
                    favorites = ready.favorites,
                    hasMore = ready.hasMoreFavorites,
                    actions = gameActions,
                    onSeeAll = onSeeAllFavorites,
                    onBrowseGames = onBrowseGames,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        } else {
            null
        },
    )
}

@Composable
fun HomeDetailPlaceholder(
    ui: SocialUi,
    onBrowseGames: () -> Unit,
    onSeeAllFavorites: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val homeState by viewModel.uiState.collectAsStateWithLifecycle()
    val gameActions = gameActions(ui.snackbar, onToggleFavorite = viewModel::toggleFavorite)
    val ready = (homeState as? HomeUiState.Ready)?.takeIf { it.showFavorites }
    if (ready == null) {
        ThreadPlaceholder()
    } else {
        FavoritesPane(
            state = ready,
            actions = gameActions,
            reduceMotion = rememberReduceMotion(),
            onExpandedChange = viewModel::setFavoritesExpanded,
            onBrowseGames = onBrowseGames,
            onSeeAll = onSeeAllFavorites,
        )
    }
}

private fun availableTimelines(local: Boolean, global: Boolean): Set<TimelineKind> = buildSet {
    add(TimelineKind.Home)
    if (local) add(TimelineKind.Local)
    if (global) add(TimelineKind.Global)
}

@Composable
private fun FeedPane(
    feed: FeedUiState,
    listStates: Map<TimelineKind, LazyListState>,
    available: Set<TimelineKind>,
    environment: NoteEnvironment,
    noteCallbacks: NoteCallbacks,
    feedViewModel: FeedViewModel,
    reduceMotion: Boolean,
    onCompose: () -> Unit,
    showComposeButton: Boolean,
    favorites: (@Composable () -> Unit)?,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val selected = feed.selected
    val timeline = feed.current
    val scopesOutdated by feedViewModel.scopesOutdated.collectAsStateWithLifecycle(initialValue = false)
    val listCallbacks = remember(feedViewModel) {
        TimelineListCallbacks(
            onLoadMore = { feedViewModel.loadMore() },
            onRetry = { feedViewModel.retry() },
            onRefresh = feedViewModel::refresh,
            onShowPending = { feedViewModel.showPending() },
            onLogIn = feedViewModel::logIn,
            onAtTop = { feedViewModel.setAtTop(feedViewModel.uiState.value.selected, it) },
            onVisibleNotes = { feedViewModel.setVisibleNotes(feedViewModel.uiState.value.selected, it) },
        )
    }
    val atTopOfSelected by remember(selected, listStates) {
        derivedStateOf {
            val state = listStates.getValue(selected)
            state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    TimelineSwitcher(
                        selected = selected,
                        available = available,
                        onSelect = feedViewModel::select,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    TooltipIconButton(
                        label = stringResource(R.string.feed_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = feedViewModel::refresh,
                    )
                },
                expandedHeight = 64.dp,
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (showComposeButton) {
                val expanded = atTopOfSelected && !reduceMotion
                val label = stringResource(R.string.feed_compose)
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    expanded = expanded,
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        },
    ) { innerPadding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = timeline.refreshing,
            onRefresh = feedViewModel::refresh,
            state = pullState,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = timeline.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "timeline",
            ) { kind ->
                TimelineList(
                    kind = kind,
                    timeline = feed.timeline(kind),
                    listState = listStates.getValue(kind),
                    environment = environment,
                    noteCallbacks = noteCallbacks,
                    callbacks = listCallbacks,
                    reduceMotion = reduceMotion,
                    header = favorites,
                    banner = when {
                        feed.needsPermission -> {
                            { ReauthorizeBanner(onDismiss = feedViewModel::permissionPromptHandled) }
                        }
                        scopesOutdated -> {
                            { ReauthorizeBanner(onDismiss = feedViewModel::dismissScopeBanner, newPermissions = true) }
                        }
                        else -> null
                    },
                )
            }
            NewPostsPill(
                pending = timeline.pending,
                gap = timeline.gap,
                visible = timeline.hasPending && !atTopOfSelected,
                reduceMotion = reduceMotion,
                onClick = { feedViewModel.showPending() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
    }
}

@Composable
fun ReauthorizeBanner(onDismiss: () -> Unit, newPermissions: Boolean = false) {
    val login: LoginViewModel = viewModel(key = "reauthorize", factory = LoginViewModel.Factory)
    val loginState by login.uiState.collectAsStateWithLifecycle()
    AuthPageEffects(login)
    Box(modifier = Modifier.widthIn(max = FeedMaxWidth).fillMaxWidth()) {
        if (loginState.signingIn) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                    TextButton(onClick = login::onCancel, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(stringResource(R.string.login_cancel))
                    }
                }
            }
        } else {
            PermissionBanner(onGrant = login::onSignInClick, onDismiss = onDismiss, newPermissions = newPermissions)
        }
    }
}

@Composable
private fun FavoritesPane(
    state: HomeUiState.Ready,
    actions: GameActions,
    reduceMotion: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBrowseGames: () -> Unit,
    onSeeAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        favoritesSection(
            state = state,
            actions = actions,
            reduceMotion = reduceMotion,
            onExpandedChange = onExpandedChange,
            onBrowseGames = onBrowseGames,
            onSeeAll = onSeeAll,
        )
    }
}

private fun Modifier.homeItemWidth(): Modifier = widthIn(max = FeedMaxWidth).fillMaxWidth()

@Composable
private fun Modifier.homeItemMotion(scope: LazyItemScope, reduceMotion: Boolean): Modifier {
    val motion = MaterialTheme.motionScheme
    return with(scope) {
        if (reduceMotion) {
            animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
        } else {
            animateItem(
                fadeInSpec = motion.defaultEffectsSpec(),
                placementSpec = motion.defaultSpatialSpec(),
                fadeOutSpec = motion.fastEffectsSpec(),
            )
        }
    }
}

private fun LazyListScope.favoritesSection(
    state: HomeUiState.Ready,
    actions: GameActions,
    reduceMotion: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBrowseGames: () -> Unit,
    onSeeAll: () -> Unit,
) {
    item(key = "favorites:header", contentType = "header") {
        FavoritesHeader(
            count = state.favoriteCount,
            expanded = state.favoritesExpanded,
            reduceMotion = reduceMotion,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.homeItemMotion(this, reduceMotion).homeItemWidth(),
        )
    }
    if (!state.favoritesExpanded) return
    if (state.favorites.isEmpty()) {
        item(key = "favorites:empty", contentType = "empty") {
            FavoritesEmpty(
                onBrowseGames = onBrowseGames,
                modifier = Modifier.homeItemMotion(this, reduceMotion).homeItemWidth().padding(top = 8.dp),
            )
        }
        return
    }
    itemsIndexed(state.favorites, key = { _, game -> "favorite:${game.url}" }, contentType = { _, _ -> "game" }) { index, game ->
        FavoriteRow(
            game = game,
            index = index,
            count = state.favorites.size,
            actions = actions,
            modifier = Modifier.homeItemMotion(this, reduceMotion).homeItemWidth(),
        )
    }
    if (state.hasMoreFavorites) {
        item(key = "favorites:see-all", contentType = "see-all") {
            Box(modifier = Modifier.homeItemMotion(this, reduceMotion).homeItemWidth()) {
                TextButton(onClick = onSeeAll, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.home_favorites_see_all))
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(game: Game, index: Int, count: Int, actions: GameActions, modifier: Modifier = Modifier) {
    GameItem(
        game = game,
        favorite = true,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        actions = actions,
        showFavoriteMarker = false,
        modifier = modifier.padding(top = if (index == 0) 8.dp else ListItemDefaults.SegmentedGap),
    )
}

@Composable
private fun FavoritesHeader(
    count: Int,
    expanded: Boolean,
    reduceMotion: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "favoritesChevron",
    )
    val stateLabel = stringResource(if (expanded) R.string.home_favorites_expanded else R.string.home_favorites_collapsed)
    val actionLabel = stringResource(if (expanded) R.string.home_favorites_collapse else R.string.home_favorites_expand)

    Surface(
        onClick = { onExpandedChange(!expanded) },
        shape = MaterialTheme.shapes.large,
        color = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .pointerHover { hovered = it }
            .semantics { stateDescription = stateLabel },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_favorites_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                if (count > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.home_favorites_count, count, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = actionLabel,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun FavoritesEmpty(onBrowseGames: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_favorites_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onBrowseGames, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.home_favorites_browse))
            }
        }
    }
}

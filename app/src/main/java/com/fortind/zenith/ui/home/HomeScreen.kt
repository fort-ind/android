@file:OptIn(
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package com.fortind.zenith.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AdaptStrategy
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableSupportingPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortind.zenith.R
import com.fortind.zenith.games.Game
import com.fortind.zenith.ui.games.FavoriteNoticeEffect
import com.fortind.zenith.ui.games.GameActions
import com.fortind.zenith.ui.games.GameItem
import com.fortind.zenith.ui.games.gameActions
import com.fortind.zenith.ui.pointerHover
import com.fortind.zenith.ui.rememberReduceMotion

private val ContentMaxWidth = 640.dp

@Composable
fun HomeScreen(
    onBrowseGames: () -> Unit,
    onSeeAllFavorites: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteNotice by viewModel.favoriteNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    FavoriteNoticeEffect(
        notice = favoriteNotice,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undoFavorite,
        onShown = viewModel::favoriteNoticeShown,
    )

    Box(modifier = modifier.fillMaxSize()) {
        HomeContent(
            state = state,
            actions = gameActions(snackbarHostState, onToggleFavorite = viewModel::toggleFavorite),
            onFavoritesExpandedChange = viewModel::setFavoritesExpanded,
            onBrowseGames = onBrowseGames,
            onSeeAllFavorites = onSeeAllFavorites,
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun HomeContent(
    state: HomeUiState,
    actions: GameActions,
    onFavoritesExpandedChange: (Boolean) -> Unit,
    onBrowseGames: () -> Unit,
    onSeeAllFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_home),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                subtitle = {
                    Text(text = stringResource(R.string.home_subtitle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val paneModifier = Modifier.padding(innerPadding)
        when (state) {
            HomeUiState.Loading -> Box(paneModifier.fillMaxSize())
            is HomeUiState.Ready -> if (state.showFavorites) {
                HomePanes(
                    state = state,
                    actions = actions,
                    onFavoritesExpandedChange = onFavoritesExpandedChange,
                    onBrowseGames = onBrowseGames,
                    onSeeAllFavorites = onSeeAllFavorites,
                    modifier = paneModifier,
                )
            } else {
                HomeColumn(modifier = paneModifier) { feedPlaceholder() }
            }
        }
    }
}

@Composable
private fun HomePanes(
    state: HomeUiState.Ready,
    actions: GameActions,
    onFavoritesExpandedChange: (Boolean) -> Unit,
    onBrowseGames: () -> Unit,
    onSeeAllFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberSupportingPaneScaffoldNavigator(
        adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(
            supportingPaneAdaptStrategy = AdaptStrategy.Hide,
        ),
    )
    val sideBySide = navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded
    val reduceMotion = rememberReduceMotion()
    val favorites: LazyListScope.() -> Unit = {
        favoritesSection(
            state = state,
            actions = actions,
            reduceMotion = reduceMotion,
            onExpandedChange = onFavoritesExpandedChange,
            onBrowseGames = onBrowseGames,
            onSeeAll = onSeeAllFavorites,
        )
    }

    NavigableSupportingPaneScaffold(
        navigator = navigator,
        modifier = modifier.fillMaxSize(),
        mainPane = {
            AnimatedPane {
                HomeColumn {
                    if (!sideBySide) favorites()
                    feedPlaceholder()
                }
            }
        },
        supportingPane = {
            AnimatedPane {
                HomeColumn(content = favorites)
            }
        },
    )
}

@Composable
private fun HomeColumn(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private fun Modifier.homeItemWidth(): Modifier = widthIn(max = ContentMaxWidth).fillMaxWidth()

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

private fun LazyListScope.feedPlaceholder() {
    item(key = "feed:placeholder", contentType = "feed-placeholder") {
        FeedComingSoonCard(modifier = Modifier.homeItemWidth().padding(top = 24.dp))
    }
}

@Composable
private fun FeedComingSoonCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialShapes.Cookie7Sided.toShape())
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DynamicFeed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.home_feed_overline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = stringResource(R.string.home_feed_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.home_feed_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

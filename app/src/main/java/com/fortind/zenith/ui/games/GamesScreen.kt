package com.fortind.zenith.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.R
import com.fortind.zenith.games.Game
import com.fortind.zenith.ui.pointerHover
import com.fortind.zenith.games.GameCategory
import com.fortind.zenith.games.GameSection
import com.fortind.zenith.games.searchGames
import com.fortind.zenith.ui.rememberReduceMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val HeaderMaxWidth = 720.dp

@Composable
fun GamesScreen(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = viewModel(factory = GamesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategories by viewModel.selectedCategories.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val favoriteNotice by viewModel.favoriteNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    FavoriteNoticeEffect(
        notice = favoriteNotice,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undoFavorite,
        onShown = viewModel::favoriteNoticeShown,
    )

    Box(modifier = modifier.fillMaxSize()) {
        GamesContent(
            state = state,
            selectedCategories = selectedCategories,
            favoritesOnly = favoritesOnly,
            onToggleCategory = viewModel::toggleCategory,
            onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
            onClearFilters = viewModel::clearFilters,
            onRetry = viewModel::retry,
            actions = gameActions(snackbarHostState, onToggleFavorite = viewModel::toggleFavorite),
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesContent(
    state: GamesUiState,
    selectedCategories: Set<GameCategory>,
    favoritesOnly: Boolean,
    onToggleCategory: (GameCategory) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    actions: GameActions,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val bookHinge = adaptiveInfo.windowPosture.separatingVerticalHingeBounds.firstOrNull()
    val tabletopHinge = adaptiveInfo.windowPosture
        .takeIf { it.isTabletop }
        ?.separatingHorizontalHingeBounds
        ?.firstOrNull()
    val columns = when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 3
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 2
        else -> 1
    }
    val gutter = if (columns > 1) 24.dp else 16.dp

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val density = LocalDensity.current
    var searchBarWidth by remember { mutableStateOf(Dp.Unspecified) }
    var contentTopInWindow by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val games = (state as? GamesUiState.Loaded)?.games.orEmpty()
    val query = textFieldState.text.toString()
    val results = remember(games, query) { searchGames(games, query) }

    val openFromSearch: (Game) -> Unit = { game ->
        actions.open(game)
        scope.launch { searchBarState.animateToCollapsed() }
    }
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            modifier = Modifier.fillMaxWidth(),
            onSearch = { results.firstOrNull()?.let(openFromSearch) },
            placeholder = {
                Text(
                    text = stringResource(R.string.games_search_placeholder),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            },
            leadingIcon = { SearchLeadingIcon(searchBarState) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.games_search_clear))
                    }
                }
            },
        )
    }

    val header = @Composable { headerModifier: Modifier ->
        Column(
            modifier = headerModifier
                .padding(horizontal = gutter)
                .widthIn(max = HeaderMaxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBar(
                state = searchBarState,
                inputField = inputField,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { searchBarWidth = with(density) { it.width.toDp() } },
            )
            CategoryChips(
                selected = selectedCategories,
                favoritesOnly = favoritesOnly,
                onToggle = onToggleCategory,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
                onClear = onClearFilters,
            )
        }
    }
    val body = @Composable {
        when (state) {
            GamesUiState.Loading -> LoadingState()
            GamesUiState.Failed -> FailedState(onRetry = onRetry)
            is GamesUiState.Loaded -> if (state.sections.isEmpty()) {
                EmptyFilterState(favoritesOnly = favoritesOnly)
            } else {
                GameGrid(
                    sections = state.sections,
                    favorites = state.favorites,
                    columns = columns,
                    gutter = gutter,
                    bookHinge = bookHinge,
                    actions = actions,
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { contentTopInWindow = it.positionInWindow().y }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                    scope.launch { searchBarState.animateToExpanded() }
                    true
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (tabletopHinge != null) {
            with(density) {
                Box(Modifier.height((tabletopHinge.top - contentTopInWindow).coerceAtLeast(0f).toDp())) { body() }
                Spacer(Modifier.height(tabletopHinge.height.toDp()))
            }
            header(Modifier.padding(top = 24.dp))
        } else {
            header(Modifier.padding(top = 8.dp))
            body()
        }
    }

    if (columns == 1 || tabletopHinge != null) {
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            SearchResults(query = query, results = results, onOpen = openFromSearch)
        }
    } else {
        ExpandedDockedSearchBar(
            state = searchBarState,
            inputField = inputField,
            modifier = if (searchBarWidth.isSpecified) Modifier.width(searchBarWidth) else Modifier,
        ) {
            SearchResults(query = query, results = results, onOpen = openFromSearch)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchLeadingIcon(searchBarState: SearchBarState) {
    val scope = rememberCoroutineScope()
    if (searchBarState.currentValue == SearchBarValue.Expanded) {
        IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.games_search_back))
        }
    } else {
        Icon(Icons.Default.Search, contentDescription = null)
    }
}

@Composable
private fun CategoryChips(
    selected: Set<GameCategory>,
    favoritesOnly: Boolean,
    onToggle: (GameCategory) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryChip(
            label = stringResource(R.string.games_filter_all),
            selected = selected.isEmpty() && !favoritesOnly,
            onClick = onClear,
        )
        CategoryChip(
            label = stringResource(R.string.games_filter_favorites),
            selected = favoritesOnly,
            onClick = onToggleFavoritesOnly,
            unselectedIcon = Icons.Default.Star,
        )
        GameCategory.entries.forEach { category ->
            CategoryChip(
                label = categoryLabel(category),
                selected = category in selected,
                onClick = { onToggle(category) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    unselectedIcon: ImageVector? = null,
) {
    val icon = if (selected) Icons.Default.Check else unselectedIcon
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GameGrid(
    sections: List<GameSection>,
    favorites: Set<String>,
    columns: Int,
    gutter: Dp,
    bookHinge: Rect?,
    actions: GameActions,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var gridBoundsInWindow by remember { mutableStateOf(Rect.Zero) }
    val hingeSplit = bookHinge?.let {
        with(density) { hingeSplitCells(it, gridBoundsInWindow, gutter.toPx(), HingeClearance.roundToPx(), layoutDirection) }
    }
    val segmented = columns == 1 && hingeSplit == null
    LazyVerticalGrid(
        columns = hingeSplit ?: GridCells.Fixed(columns),
        state = rememberLazyGridState(),
        contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 16.dp),
        horizontalArrangement = if (hingeSplit != null) HingeSplitArrangement else Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (segmented) ListItemDefaults.SegmentedGap else 8.dp),
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { gridBoundsInWindow = it.boundsInWindow() },
    ) {
        sections.forEach { section ->
            stickyHeader(key = "header:${section.letter}", contentType = "header") {
                SectionHeader(section.letter)
            }
            itemsIndexed(section.games, key = { _, game -> game.url }, contentType = { _, _ -> "game" }) { index, game ->
                GameItem(
                    game = game,
                    favorite = game.url in favorites,
                    shapes = if (segmented) {
                        ListItemDefaults.segmentedShapes(index = index, count = section.games.size)
                    } else {
                        ListItemDefaults.segmentedShapes(index = 0, count = 1)
                    },
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(letter: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
                .semantics { heading() },
        )
    }
}

@Composable
internal fun GameItem(
    game: Game,
    favorite: Boolean,
    shapes: ListItemShapes,
    actions: GameActions,
    modifier: Modifier = Modifier,
    showFavoriteMarker: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val showMenu = { offset: DpOffset ->
        menuOffset = offset
        menuOpen = true
    }
    fun closeMenuThen(action: (Game) -> Unit) {
        menuOpen = false
        action(game)
    }
    var hovered by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        SegmentedListItem(
            onClick = { actions.open(game) },
            shapes = shapes,
            onLongClick = { showMenu(DpOffset.Zero) },
            onLongClickLabel = stringResource(R.string.games_menu_label, game.title),
            leadingContent = { CategoryBadge(game) },
            supportingContent = { Text(categoryLabel(game.category)) },
            trailingContent = if (favorite && showFavoriteMarker) {
                {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.favorites_marker),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = if (hovered) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .pointerHover { hovered = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val position = event.changes.first().position
                            event.changes.forEach { it.consume() }
                            showMenu(DpOffset(position.x.toDp(), (position.y - size.height).toDp()))
                        }
                    }
                }
                .onKeyEvent { event ->
                    val isMenuKey = event.key == Key.Menu || (event.isShiftPressed && event.key == Key.F10)
                    if (event.type == KeyEventType.KeyDown && isMenuKey) {
                        showMenu(DpOffset.Zero)
                        true
                    } else {
                        false
                    }
                },
        ) {
            Text(game.title)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, offset = menuOffset) {
            GameMenuItem(R.string.games_menu_open) { closeMenuThen(actions.open) }
            GameMenuItem(R.string.games_menu_copy_link) { closeMenuThen(actions.copyLink) }
            GameMenuItem(R.string.games_menu_share) { closeMenuThen(actions.share) }
            GameMenuItem(if (favorite) R.string.favorites_remove else R.string.favorites_add) {
                closeMenuThen(actions.toggleFavorite)
            }
        }
    }
}

@Composable
private fun GameMenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CategoryBadge(game: Game) {
    val colors = MaterialTheme.colorScheme
    val (polygon: RoundedPolygon, container: Color, content: Color) = when (game.category) {
        GameCategory.HTML -> Triple(MaterialShapes.Cookie4Sided, colors.primaryContainer, colors.onPrimaryContainer)
        GameCategory.FLASH -> Triple(MaterialShapes.SoftBurst, colors.tertiaryContainer, colors.onTertiaryContainer)
        GameCategory.RETRO -> Triple(MaterialShapes.Cookie9Sided, colors.secondaryContainer, colors.onSecondaryContainer)
        GameCategory.MINECRAFT -> Triple(MaterialShapes.Square, colors.primaryContainer, colors.onPrimaryContainer)
        GameCategory.CODEPEN -> Triple(MaterialShapes.Clover4Leaf, colors.tertiaryContainer, colors.onTertiaryContainer)
        GameCategory.OTHER -> Triple(MaterialShapes.Circle, colors.secondaryContainer, colors.onSecondaryContainer)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(polygon.toShape())
            .background(container)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(text = game.title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = content)
    }
}

@Composable
private fun SearchResults(query: String, results: List<Game>, onOpen: (Game) -> Unit) {
    if (query.isNotBlank() && results.isEmpty()) {
        Text(
            text = stringResource(R.string.games_search_no_results, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(results, key = { it.url }) { game ->
            var hovered by remember { mutableStateOf(false) }
            ListItem(
                onClick = { onOpen(game) },
                leadingContent = { CategoryBadge(game) },
                supportingContent = { Text(categoryLabel(game.category)) },
                colors = ListItemDefaults.colors(
                    containerColor = if (hovered) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHover { hovered = it },
            ) {
                Text(game.title)
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
private fun FailedState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.games_load_failed_title),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.games_load_failed_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val size = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = onRetry,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(size),
            modifier = Modifier.heightIn(min = size),
        ) {
            Text(stringResource(R.string.games_retry), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyFilterState(favoritesOnly: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(if (favoritesOnly) R.string.games_no_favorites_title else R.string.games_no_matches_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (favoritesOnly) {
            Text(
                text = stringResource(R.string.favorites_how_to),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = HeaderMaxWidth),
            )
        }
    }
}

private val HingeClearance = 12.dp

internal fun hingeSplitCells(
    hinge: Rect,
    grid: Rect,
    gutterPx: Float,
    clearancePx: Int,
    layoutDirection: LayoutDirection,
): HingeSplitCells? {
    val contentWidth = grid.width - 2 * gutterPx
    val hingeLeft = hinge.left - grid.left - gutterPx
    val hingeRight = hinge.right - grid.left - gutterPx
    if (grid.isEmpty || hingeLeft <= clearancePx || hingeRight >= contentWidth - clearancePx) return null
    val (startEdge, endEdge) = if (layoutDirection == LayoutDirection.Ltr) {
        hingeLeft to hingeRight
    } else {
        contentWidth - hingeRight to contentWidth - hingeLeft
    }
    return HingeSplitCells(startEdge.roundToInt() - clearancePx, endEdge.roundToInt() + clearancePx)
}

internal data class HingeSplitCells(val startWidth: Int, val endOffset: Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> =
        listOf(startWidth, (availableSize - endOffset).coerceAtLeast(0))
}

private object HingeSplitArrangement : Arrangement.Horizontal {
    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        val startFirst = layoutDirection == LayoutDirection.Ltr
        outPositions[0] = if (startFirst) 0 else totalSize - sizes[0]
        outPositions[sizes.lastIndex] = if (startFirst) totalSize - sizes.last() else 0
    }
}

@Composable
internal fun categoryLabel(category: GameCategory): String = stringResource(
    when (category) {
        GameCategory.HTML -> R.string.games_category_html
        GameCategory.FLASH -> R.string.games_category_flash
        GameCategory.RETRO -> R.string.games_category_retro
        GameCategory.MINECRAFT -> R.string.games_category_minecraft
        GameCategory.CODEPEN -> R.string.games_category_codepen
        GameCategory.OTHER -> R.string.games_category_other
    }
)

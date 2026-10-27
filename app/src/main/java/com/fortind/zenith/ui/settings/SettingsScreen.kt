@file:OptIn(
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package com.fortind.zenith.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.BuildConfig
import com.fortind.zenith.R
import com.fortind.zenith.settings.AppIcon
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.ThemeMode
import com.fortind.zenith.ui.pointerHover
import kotlinx.coroutines.launch

private enum class SettingsPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val supportingRes: Int,
    val icon: ImageVector,
) {
    APPEARANCE(R.string.settings_category_appearance, R.string.settings_appearance_supporting, Icons.Default.Palette),
    HOME(R.string.settings_category_home, R.string.settings_home_supporting, Icons.Default.Home),
    ABOUT(R.string.settings_category_about, R.string.settings_about_supporting, Icons.Default.Info),
}

private enum class SettingsEntry(
    val page: SettingsPage,
    @param:StringRes val titleRes: Int,
    val alsoMatches: List<Int> = emptyList(),
) {
    THEME(
        SettingsPage.APPEARANCE,
        R.string.settings_theme_section,
        listOf(R.string.settings_theme_system, R.string.settings_theme_light, R.string.settings_theme_dark),
    ),
    DYNAMIC_COLOR(
        SettingsPage.APPEARANCE,
        R.string.settings_dynamic_color_title,
        listOf(R.string.settings_dynamic_color_supporting),
    ),
    APP_ICON(
        SettingsPage.APPEARANCE,
        R.string.settings_icon_section,
        listOf(R.string.settings_icon_default, R.string.settings_icon_fall),
    ),
    HOME_FAVORITES(
        SettingsPage.HOME,
        R.string.settings_home_favorites_title,
        listOf(R.string.settings_home_favorites_supporting, R.string.home_favorites_title),
    ),
    VERSION(SettingsPage.ABOUT, R.string.settings_about_version),
    BUILD(SettingsPage.ABOUT, R.string.settings_about_build),
}

private val ContentMaxWidth = 640.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val appIcon by viewModel.appIcon.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<SettingsPage>()
    val scope = rememberCoroutineScope()
    val searchBarState = rememberSearchBarState()
    var searchRequested by remember { mutableStateOf(false) }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                    val listVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
                    if (!listVisible) scope.launch { navigator.navigateBack() }
                    searchRequested = true
                    true
                } else {
                    false
                }
            },
        listPane = {
            SettingsList(
                selected = navigator.currentDestination?.contentKey,
                searchBarState = searchBarState,
                searchRequested = searchRequested,
                onSearchHandled = { searchRequested = false },
                onSelect = { page ->
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, page) }
                },
            )
        },
        detailPane = {
            val page = navigator.currentDestination?.contentKey
            if (page == null) {
                EmptyDetail()
            } else {
                SettingsDetail(
                    page = page,
                    settings = settings,
                    appIcon = appIcon,
                    onThemeMode = viewModel::setThemeMode,
                    onDynamicColor = viewModel::setDynamicColor,
                    onAppIcon = viewModel::setAppIcon,
                    onShowHomeFavorites = viewModel::setShowHomeFavorites,
                    onBack = { scope.launch { navigator.navigateBack() } },
                )
            }
        },
    )
}

@Composable
private fun SettingsList(
    selected: SettingsPage?,
    searchBarState: SearchBarState,
    searchRequested: Boolean,
    onSearchHandled: () -> Unit,
    onSelect: (SettingsPage) -> Unit,
) {
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var searchBarWidth by remember { mutableStateOf(Dp.Unspecified) }
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val query = textFieldState.text.toString().trim()
    val entries = SettingsEntry.entries.filter {
        it != SettingsEntry.DYNAMIC_COLOR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    val labels = entries.associateWith { entry ->
        (listOf(entry.titleRes, entry.page.titleRes) + entry.alsoMatches).map { stringResource(it) }
    }
    val results = entries.filter { entry -> labels.getValue(entry).any { it.contains(query, ignoreCase = true) } }

    val openFromSearch: (SettingsEntry) -> Unit = { entry ->
        onSelect(entry.page)
        textFieldState.clearText()
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
                    text = stringResource(R.string.settings_search_placeholder),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            },
            leadingIcon = { SearchLeadingIcon(searchBarState) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.settings_search_clear))
                    }
                }
            },
        )
    }

    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            searchBarState.animateToExpanded()
            onSearchHandled()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    windowInsets = WindowInsets(0),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SearchBar(
                        state = searchBarState,
                        inputField = inputField,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { searchBarWidth = with(density) { it.width.toDp() } },
                    )
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        SettingsPage.entries.forEachIndexed { index, page ->
                            SettingsRow(
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = SettingsPage.entries.size),
                                action = RowAction.Select(selected = page == selected, onClick = { onSelect(page) }),
                                leading = { IconBadge(page) },
                                headline = { Text(stringResource(page.titleRes)) },
                                supporting = { Text(stringResource(page.supportingRes)) },
                            )
                        }
                    }
                }
            }
        }

        if (!windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
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
}

@Composable
private fun SearchLeadingIcon(searchBarState: SearchBarState) {
    val scope = rememberCoroutineScope()
    if (searchBarState.currentValue == SearchBarValue.Expanded) {
        IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_search_back))
        }
    } else {
        Icon(Icons.Default.Search, contentDescription = null)
    }
}

@Composable
private fun SearchResults(query: String, results: List<SettingsEntry>, onOpen: (SettingsEntry) -> Unit) {
    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_search_no_results, query),
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
        items(results, key = { it.name }) { entry ->
            var hovered by remember { mutableStateOf(false) }
            ListItem(
                onClick = { onOpen(entry) },
                leadingContent = { IconBadge(entry.page) },
                supportingContent = { Text(stringResource(entry.page.titleRes)) },
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
                Text(stringResource(entry.titleRes))
            }
        }
    }
}

@Composable
private fun IconBadge(page: SettingsPage) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (page) {
        SettingsPage.APPEARANCE -> colors.primaryContainer to colors.onPrimaryContainer
        SettingsPage.HOME -> colors.secondaryContainer to colors.onSecondaryContainer
        SettingsPage.ABOUT -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    val polygon = when (page) {
        SettingsPage.APPEARANCE -> MaterialShapes.Cookie9Sided
        SettingsPage.HOME -> MaterialShapes.Cookie4Sided
        SettingsPage.ABOUT -> MaterialShapes.Cookie6Sided
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(polygon.toShape())
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = page.icon, contentDescription = null, tint = content)
    }
}

@Composable
private fun SettingsDetail(
    page: SettingsPage,
    settings: AppSettings,
    appIcon: AppIcon,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAppIcon: (AppIcon) -> Unit,
    onShowHomeFavorites: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(page.titleRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                subtitle = {
                    Text(text = stringResource(page.supportingRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (page) {
                    SettingsPage.APPEARANCE -> AppearanceSettings(
                        settings = settings,
                        appIcon = appIcon,
                        onThemeMode = onThemeMode,
                        onDynamicColor = onDynamicColor,
                        onAppIcon = onAppIcon,
                    )
                    SettingsPage.HOME -> HomeSettings(settings = settings, onShowFavorites = onShowHomeFavorites)
                    SettingsPage.ABOUT -> AboutSettings()
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettings(
    settings: AppSettings,
    appIcon: AppIcon,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAppIcon: (AppIcon) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(R.string.settings_theme_section)
        ThemeModePicker(selected = settings.themeMode, onSelect = onThemeMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SectionLabel(R.string.settings_color_section)
            SwitchRow(
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColor,
                titleRes = R.string.settings_dynamic_color_title,
                supportingRes = R.string.settings_dynamic_color_supporting,
            )
        }
        SectionLabel(R.string.settings_icon_section)
        AppIconPicker(selected = appIcon, onSelect = onAppIcon)
    }
}

@Composable
private fun HomeSettings(settings: AppSettings, onShowFavorites: (Boolean) -> Unit) {
    SwitchRow(
        checked = settings.showHomeFavorites,
        onCheckedChange = onShowFavorites,
        titleRes = R.string.settings_home_favorites_title,
        supportingRes = R.string.settings_home_favorites_supporting,
    )
}

@Composable
private fun SwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @StringRes titleRes: Int,
    @StringRes supportingRes: Int,
) {
    SettingsRow(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        action = RowAction.Toggle(checked = checked, onCheckedChange = onCheckedChange),
        headline = { Text(stringResource(titleRes)) },
        supporting = { Text(stringResource(supportingRes)) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        },
    )
}

@Composable
private fun AppIconPicker(selected: AppIcon, onSelect: (AppIcon) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon.entries.forEach { icon ->
            var hovered by remember { mutableStateOf(false) }
            val isSelected = icon == selected
            Surface(
                selected = isSelected,
                onClick = { onSelect(icon) },
                modifier = Modifier
                    .weight(1f)
                    .pointerHover { hovered = it }
                    .semantics { role = Role.RadioButton },
                shape = MaterialTheme.shapes.large,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.secondaryContainer
                    hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    ) {
                        Image(
                            painter = painterResource(icon.previewRes()),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(text = stringResource(icon.labelRes()), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun AppIcon.labelRes(): Int = when (this) {
    AppIcon.DEFAULT -> R.string.settings_icon_default
    AppIcon.FALL -> R.string.settings_icon_fall
}

private fun AppIcon.previewRes(): Int = when (this) {
    AppIcon.DEFAULT -> R.drawable.ic_launcher_foreground
    AppIcon.FALL -> R.drawable.ic_launcher_foreground_fall
}

@Composable
private fun ThemeModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, mode ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { if (it) onSelect(mode) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(text = stringResource(mode.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AboutSettings() {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SettingsRow(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
            headline = { Text(stringResource(R.string.settings_about_version)) },
            supporting = { Text(BuildConfig.VERSION_NAME) },
        )
        SettingsRow(
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            headline = { Text(stringResource(R.string.settings_about_build)) },
            supporting = { Text(BuildConfig.VERSION_CODE.toString()) },
        )
    }
}

private sealed interface RowAction {
    data object None : RowAction
    data class Select(val selected: Boolean, val onClick: () -> Unit) : RowAction
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : RowAction
}

@Composable
private fun SettingsRow(
    shapes: ListItemShapes,
    headline: @Composable () -> Unit,
    action: RowAction = RowAction.None,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var hovered by remember { mutableStateOf(false) }
    val colors = ListItemDefaults.segmentedColors(
        containerColor = if (hovered && action !is RowAction.None) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    )
    val modifier = if (action is RowAction.None) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().pointerHover { hovered = it }
    }
    when (action) {
        RowAction.None -> SegmentedListItem(
            shapes = shapes,
            modifier = modifier,
            colors = colors,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
        is RowAction.Select -> SegmentedListItem(
            selected = action.selected,
            onClick = action.onClick,
            shapes = shapes,
            modifier = modifier,
            colors = colors,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
        is RowAction.Toggle -> SegmentedListItem(
            checked = action.checked,
            onCheckedChange = action.onCheckedChange,
            shapes = shapes,
            modifier = modifier.semantics { role = Role.Switch },
            colors = colors,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    }
}

@Composable
private fun SectionLabel(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun EmptyDetail() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.settings_detail_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

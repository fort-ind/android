@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.discovery

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.R
import com.fortind.zenith.timeline.NoteListKey
import com.fortind.zenith.timeline.NoteSearch
import com.fortind.zenith.timeline.SearchMedia
import com.fortind.zenith.ui.feed.FeedLoading
import com.fortind.zenith.ui.feed.FeedMaxWidth
import com.fortind.zenith.ui.feed.FeedMessage
import com.fortind.zenith.ui.feed.TimelineList
import com.fortind.zenith.ui.feed.TimelineListCallbacks
import com.fortind.zenith.ui.feed.TimelineStatus
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.home.ReauthorizeBanner
import com.fortind.zenith.ui.people.PageKeyTarget
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.rememberTopBarReveal
import com.fortind.zenith.ui.revealTopBarOnWheel
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import com.fortind.zenith.ui.social.SocialUi

@Composable
private fun NoteListPage(
    key: NoteListKey,
    title: String,
    @StringRes emptyBody: Int,
    ui: SocialUi,
    onBack: () -> Unit,
    stickyHeader: (@Composable () -> Unit)? = null,
) {
    val feed by ui.feed.uiState.collectAsStateWithLifecycle()
    val timeline = feed.list(key)
    val reduceMotion = rememberReduceMotion()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberSaveable(key.toString(), saver = LazyListState.Saver) { LazyListState() }
    val topBarReveal = rememberTopBarReveal(
        behavior = scrollBehavior,
        reduceMotion = reduceMotion,
        canScrollBack = { listState.canScrollBackward },
    )
    val idle = timeline.status == TimelineStatus.Idle
    val refresh = { ui.feed.loadList(key) }
    val currentRefresh by rememberUpdatedState(refresh)

    LaunchedEffect(ui.feed, key, idle, feed.signedIn) { if (idle) ui.feed.loadList(key) }
    DisposableEffect(ui.pageKeys, key) {
        val target = PageKeyTarget(onRefresh = { currentRefresh() }, onMoveTab = {})
        ui.pageKeys.target = target
        onDispose { if (ui.pageKeys.target === target) ui.pageKeys.target = null }
    }
    val callbacks = remember(ui.feed, key) {
        TimelineListCallbacks(
            onLoadMore = { ui.feed.loadMoreList(key) },
            onRetry = { ui.feed.loadList(key) },
            onRefresh = { ui.feed.loadList(key) },
            onShowPending = {},
            onLogIn = ui.feed::logIn,
            onAtTop = {},
            onVisibleNotes = {},
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).revealTopBarOnWheel(topBarReveal),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.person_back), icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                },
                actions = {
                    TooltipIconButton(label = stringResource(R.string.feed_refresh), icon = Icons.Default.Refresh, onClick = refresh)
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = timeline.refreshing,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            TimelineList(
                kind = null,
                timeline = timeline,
                listState = listState,
                environment = LocalNoteEnvironment.current,
                noteCallbacks = ui.callbacks,
                callbacks = callbacks,
                reduceMotion = reduceMotion,
                stickyHeader = stickyHeader,
                banner = if (feed.needsPermission) {
                    { ReauthorizeBanner(onDismiss = ui.feed::permissionPromptHandled) }
                } else {
                    null
                },
                emptyBody = emptyBody,
            )
        }
    }
}

@Composable
fun TagPage(tag: String, ui: SocialUi, onBack: () -> Unit) {
    NoteListPage(
        key = NoteListKey.Tag(tag),
        title = stringResource(R.string.tag_title, tag),
        emptyBody = R.string.tag_empty,
        ui = ui,
        onBack = onBack,
    )
}

@Composable
fun SearchResultsPage(query: String, ui: SocialUi, onBack: () -> Unit) {
    val feed by ui.feed.uiState.collectAsStateWithLifecycle()
    var media by rememberSaveable(query) { mutableStateOf<SearchMedia?>(null) }
    var onlyHere by rememberSaveable(query) { mutableStateOf(false) }
    if (!feed.signedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FeedMessage(TimelineStatus.SignInRequired, null, onRetry = {}, onLogIn = ui.feed::logIn)
        }
        return
    }
    NoteListPage(
        key = NoteListKey.Search(NoteSearch(query, media, onlyHere)),
        title = stringResource(R.string.search_results_title, query),
        emptyBody = R.string.search_empty,
        ui = ui,
        onBack = onBack,
        stickyHeader = {
            SearchFilters(
                media = media,
                onlyHere = onlyHere,
                onMedia = { media = it },
                onOnlyHere = { onlyHere = it },
            )
        },
    )
}

@Composable
private fun SearchFilters(media: SearchMedia?, onlyHere: Boolean, onMedia: (SearchMedia?) -> Unit, onOnlyHere: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .widthIn(max = FeedMaxWidth)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            SearchChip(onlyHere, R.string.search_filter_here) { onOnlyHere(!onlyHere) }
            SearchChip(media == SearchMedia.Image, R.string.search_filter_images) { onMedia(SearchMedia.Image.takeUnless { media == it }) }
            SearchChip(media == SearchMedia.Video, R.string.search_filter_videos) { onMedia(SearchMedia.Video.takeUnless { media == it }) }
            SearchChip(media == SearchMedia.Audio, R.string.search_filter_audio) { onMedia(SearchMedia.Audio.takeUnless { media == it }) }
        }
    }
}

@Composable
private fun SearchChip(selected: Boolean, @StringRes label: Int, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
fun HashtagsPage(discovery: DiscoveryViewModel, onOpenTag: (String) -> Unit, onBack: () -> Unit) {
    val state by discovery.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(discovery) { discovery.loadHashtags() }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_popular_hashtags), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.person_back), icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                },
                actions = {
                    TooltipIconButton(label = stringResource(R.string.feed_refresh), icon = Icons.Default.Refresh, onClick = { discovery.loadHashtags(force = true) })
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.hashtagStatus) {
                CollectionsStatus.Idle, CollectionsStatus.Loading -> FeedLoading(Modifier.fillMaxWidth().padding(top = 48.dp))
                CollectionsStatus.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { discovery.loadHashtags(force = true) }) { Text(stringResource(R.string.search_hashtags_retry)) }
                }
                CollectionsStatus.Ready -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    modifier = Modifier.fillMaxSize().testTag("hashtags:list"),
                ) {
                    items(state.hashtags, key = { it.tag }, contentType = { "tag" }) { hashtag ->
                        SegmentedListItem(
                            onClick = { onOpenTag(hashtag.tag) },
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                            leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
                            supportingContent = { Text(pluralStringResource(R.plurals.hashtag_used_by, hashtag.usedBy, hashtag.usedBy)) },
                            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Text("#${hashtag.tag}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

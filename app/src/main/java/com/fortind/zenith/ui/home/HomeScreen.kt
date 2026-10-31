@file:OptIn(
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package com.fortind.zenith.ui.home

import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.compose.animation.Crossfade
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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortind.zenith.R
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.games.Game
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.TimelineKind
import com.fortind.zenith.timeline.acceptsOnlyLike
import com.fortind.zenith.ui.feed.FeedEvent
import com.fortind.zenith.ui.feed.FeedMaxWidth
import com.fortind.zenith.ui.feed.FeedUiState
import com.fortind.zenith.ui.feed.ConfirmDeleteDialog
import com.fortind.zenith.ui.feed.FeedViewModel
import com.fortind.zenith.ui.feed.FullscreenVideo
import com.fortind.zenith.ui.feed.ImageViewer
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.feed.NewPostsPill
import com.fortind.zenith.ui.feed.NoteCallbacks
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.feed.PermissionBanner
import com.fortind.zenith.ui.feed.ReactionPickerSheet
import com.fortind.zenith.ui.feed.ThreadPane
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
import com.fortind.zenith.ui.composer.ComposerEvent
import com.fortind.zenith.ui.composer.ComposerHost
import com.fortind.zenith.ui.composer.ComposerTarget
import com.fortind.zenith.ui.composer.ComposerViewModel
import com.fortind.zenith.ui.login.LoginViewModel
import com.fortind.zenith.ui.mfm.rememberMfmEnvironment
import com.fortind.zenith.ui.pointerHover
import com.fortind.zenith.ui.rememberNow
import com.fortind.zenith.ui.rememberReduceMotion
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onBrowseGames: () -> Unit,
    onSeeAllFavorites: () -> Unit,
    onImmersiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showBookmarks: Boolean = false,
    onCloseBookmarks: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.Factory),
    composerViewModel: ComposerViewModel = viewModel(factory = ComposerViewModel.Factory),
    playback: FeedPlayback = viewModel(factory = FeedPlayback.Factory),
) {
    LifecycleResumeEffect(playback) { onPauseOrDispose { playback.pause() } }
    DisposableEffect(playback) { onDispose { playback.stop() } }
    val homeState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteNotice by viewModel.favoriteNotice.collectAsStateWithLifecycle()
    val feed by feedViewModel.uiState.collectAsStateWithLifecycle()
    val emojis by feedViewModel.emojis.collectAsStateWithLifecycle()
    val meta by feedViewModel.meta.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    FavoriteNoticeEffect(
        notice = favoriteNotice,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undoFavorite,
        onShown = viewModel::favoriteNoticeShown,
    )
    LifecycleStartEffect(feedViewModel) {
        feedViewModel.setActive(true)
        onStopOrDispose { feedViewModel.setActive(false) }
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val twoPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1
    val selectedNoteId = navigator.currentDestination
        ?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }
        ?.contentKey
    LaunchedEffect(selectedNoteId) {
        if (selectedNoteId != null) feedViewModel.openThread(selectedNoteId) else feedViewModel.closeThread()
    }
    val immersive = !twoPane && selectedNoteId != null
    val currentImmersiveChange by rememberUpdatedState(onImmersiveChange)
    LaunchedEffect(immersive) { currentImmersiveChange(immersive) }
    DisposableEffect(Unit) { onDispose { currentImmersiveChange(false) } }

    val listStates = rememberTimelineListStates()
    val bookmarksListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var pickerNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(0) }

    val now by rememberNow()
    val emojiUrl = remember(emojis) {
        { name: String -> emojis?.get(name)?.url ?: "https://${FortInstance.HOST}/emoji/$name.webp" }
    }
    val mfm = rememberMfmEnvironment(emojiUrl, animate = !reduceMotion)
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var redraftTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val environment = remember(
        now,
        feed.myUserId,
        feed.signedIn,
        feed.renoted,
        feed.renoteChecks,
        feed.bookmarked,
        feed.bookmarkChecks,
        emojis,
        mfm,
        selectedNoteId,
        playback,
    ) {
        NoteEnvironment(
            now = now,
            myUserId = feed.myUserId,
            signedIn = feed.signedIn,
            renoted = feed.renoted,
            renoteChecks = feed.renoteChecks,
            bookmarked = feed.bookmarked,
            bookmarkChecks = feed.bookmarkChecks,
            localEmojiNames = emojis?.names,
            emojiUrl = emojiUrl,
            mfm = mfm,
            selectedNoteId = if (twoPane) selectedNoteId else null,
            playback = playback,
        )
    }
    val noteCallbacks = rememberNoteCallbacks(
        feedViewModel = feedViewModel,
        snackbarHostState = snackbarHostState,
        signedIn = feed.signedIn,
        onOpenThread = { note -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, note.id) } },
        onOpenPicker = { note ->
            pickerNoteId = note.id
            feedViewModel.loadUnicodeEmoji()
        },
        onOpenMedia = { note, index ->
            viewerNoteId = note.id
            viewerIndex = index
        },
        onReply = { note -> composerViewModel.open(ComposerTarget.Reply(note.id), note) },
        onQuote = { note -> composerViewModel.open(ComposerTarget.Quote(note.id), note) },
        onEdit = { note -> composerViewModel.open(ComposerTarget.Edit(note.id), note) },
        onDelete = { note -> deleteTarget = note.id },
        onRedraft = { note -> redraftTarget = note.id },
    )
    val startNewPost = {
        if (feed.signedIn) composerViewModel.open(ComposerTarget.NewPost, null) else feedViewModel.promptSignIn()
    }
    FeedEventsEffect(feedViewModel, snackbarHostState, listStates, reduceMotion, onRedraft = composerViewModel::redraft)
    ComposerEventsEffect(composerViewModel, feedViewModel, snackbarHostState)

    BackHandler(enabled = showBookmarks && selectedNoteId == null, onBack = onCloseBookmarks)

    val gameActions = gameActions(snackbarHostState, onToggleFavorite = viewModel::toggleFavorite)
    val ready = homeState as? HomeUiState.Ready
    val showFavorites = ready?.showFavorites == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyDown = event.type == KeyEventType.KeyDown
                when {
                    keyDown && event.key == Key.Escape && selectedNoteId != null -> {
                        scope.launch { navigator.navigateBack() }
                        true
                    }
                    keyDown && event.key == Key.Escape && showBookmarks -> {
                        onCloseBookmarks()
                        true
                    }
                    keyDown && event.key == Key.N && (event.isCtrlPressed || event.isMetaPressed) -> {
                        startNewPost()
                        true
                    }
                    else -> false
                }
            },
    ) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            defaultBackBehavior = BackNavigationBehavior.PopUntilContentChange,
            modifier = Modifier.fillMaxSize(),
            listPane = {
                AnimatedPane(modifier = Modifier.preferredWidth(440.dp)) {
                    if (showBookmarks) {
                        BookmarksPane(
                            feed = feed,
                            listState = bookmarksListState,
                            environment = environment,
                            noteCallbacks = noteCallbacks,
                            feedViewModel = feedViewModel,
                            reduceMotion = reduceMotion,
                            onBack = onCloseBookmarks,
                        )
                    } else {
                        FeedPane(
                            feed = feed,
                            listStates = listStates,
                            available = availableTimelines(meta?.ltlAvailable ?: true, meta?.gtlAvailable ?: true),
                            environment = environment,
                            noteCallbacks = noteCallbacks,
                            feedViewModel = feedViewModel,
                            reduceMotion = reduceMotion,
                            onCompose = startNewPost,
                            favorites = if (showFavorites && !twoPane && ready.favorites.isNotEmpty()) {
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
                }
            },
            detailPane = {
                AnimatedPane {
                    when {
                        selectedNoteId != null -> ThreadPane(
                            thread = feed.thread,
                            environment = environment,
                            callbacks = noteCallbacks,
                            twoPane = twoPane,
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onRetry = { feedViewModel.openThread(selectedNoteId) },
                            onLoadMoreReplies = feedViewModel::loadMoreReplies,
                        )
                        twoPane && showFavorites -> FavoritesPane(
                            state = ready,
                            actions = gameActions,
                            reduceMotion = reduceMotion,
                            onExpandedChange = viewModel::setFavoritesExpanded,
                            onBrowseGames = onBrowseGames,
                            onSeeAll = onSeeAllFavorites,
                        )
                        else -> ThreadPlaceholder()
                    }
                }
            },
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    ComposerHost(
        viewModel = composerViewModel,
        environment = environment,
        onGrantPermission = feedViewModel::requestPermission,
    )

    pickerNoteId?.let { id ->
        val note = feed.findNote(id)
        val unicode by feedViewModel.unicodeEmoji.collectAsStateWithLifecycle()
        val recent by feedViewModel.recentReactions.collectAsStateWithLifecycle(initialValue = emptyList())
        if (note == null) {
            pickerNoteId = null
        } else {
            ReactionPickerSheet(
                note = note,
                environment = environment,
                emojis = emojis,
                unicode = unicode,
                recent = recent,
                onPick = { reaction -> feedViewModel.react(note.id, reaction) },
                onDismiss = { pickerNoteId = null },
            )
        }
    }

    deleteTarget?.let { id ->
        ConfirmDeleteDialog(
            redraft = false,
            onConfirm = {
                deleteTarget = null
                feedViewModel.deleteNote(id)
            },
            onDismiss = { deleteTarget = null },
        )
    }
    redraftTarget?.let { id ->
        ConfirmDeleteDialog(
            redraft = true,
            onConfirm = {
                redraftTarget = null
                feedViewModel.redraftNote(id)
            },
            onDismiss = { redraftTarget = null },
        )
    }
    if (playback.fullscreen && playback.current != null) {
        FullscreenVideo(playback = playback, onDismiss = playback::exitFullscreen)
    }
    viewerNoteId?.let { id ->
        val note = feed.findNote(id)
        val images = remember(note) { note?.files?.filter { it.kind == MediaKind.Image }.orEmpty() }
        val uriHandler = LocalUriHandler.current
        ImageViewer(
            files = images,
            initialIndex = viewerIndex,
            animate = !reduceMotion,
            onDismiss = { viewerNoteId = null },
            onOpenOriginal = uriHandler::openUri,
        )
        if (images.isEmpty()) viewerNoteId = null
    }
}

private fun availableTimelines(local: Boolean, global: Boolean): Set<TimelineKind> = buildSet {
    add(TimelineKind.Home)
    if (local) add(TimelineKind.Local)
    if (global) add(TimelineKind.Global)
}

@Composable
private fun rememberTimelineListStates(): Map<TimelineKind, LazyListState> {
    val home = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val local = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val global = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    return remember(home, local, global) {
        mapOf(TimelineKind.Home to home, TimelineKind.Local to local, TimelineKind.Global to global)
    }
}

@Composable
private fun FeedEventsEffect(
    feedViewModel: FeedViewModel,
    snackbarHostState: SnackbarHostState,
    listStates: Map<TimelineKind, LazyListState>,
    reduceMotion: Boolean,
    onRedraft: (Note) -> Unit,
) {
    val resources = LocalResources.current
    val redraft by rememberUpdatedState(onRedraft)
    LaunchedEffect(feedViewModel, reduceMotion) {
        feedViewModel.events.collect { event ->
            when (event) {
                is FeedEvent.ScrollToTop -> listStates[event.kind]?.let { state ->
                    launch { if (reduceMotion) state.scrollToItem(0) else state.animateScrollToItem(0) }
                }
                FeedEvent.SignInRequired -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.feed_sign_in_to_interact),
                        actionLabel = resources.getString(R.string.feed_log_in),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) feedViewModel.logIn()
                }
                FeedEvent.ReactionFailed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_reaction_failed)) }
                FeedEvent.RenoteFailed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_renote_failed)) }
                FeedEvent.Bookmarked -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_bookmarked)) }
                FeedEvent.BookmarkRemoved -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_bookmark_removed)) }
                FeedEvent.BookmarkFailed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_bookmark_failed)) }
                FeedEvent.NoteDeleted -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_note_deleted)) }
                FeedEvent.DeleteFailed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_delete_failed)) }
                is FeedEvent.Redraft -> redraft(event.note)
                FeedEvent.PollVoteFailed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_poll_vote_failed)) }
                FeedEvent.PollEnded -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_poll_ended)) }
                FeedEvent.RateLimited -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_rate_limited)) }
                FeedEvent.NoteUnavailable -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_note_unavailable)) }
            }
        }
    }
}

@Composable
private fun ComposerEventsEffect(
    composerViewModel: ComposerViewModel,
    feedViewModel: FeedViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(composerViewModel, feedViewModel) {
        composerViewModel.events.collect { event ->
            when (event) {
                is ComposerEvent.Posted -> {
                    val message = when (event.target) {
                        is ComposerTarget.Edit -> {
                            feedViewModel.noteEdited(event.note)
                            R.string.composer_saved
                        }
                        is ComposerTarget.Reply -> {
                            feedViewModel.noteCreated(event.note)
                            R.string.composer_reply_sent
                        }
                        else -> {
                            feedViewModel.noteCreated(event.note)
                            R.string.composer_posted
                        }
                    }
                    launch { snackbarHostState.showSnackbar(resources.getString(message)) }
                }
            }
        }
    }
}

@Composable
private fun rememberNoteCallbacks(
    feedViewModel: FeedViewModel,
    snackbarHostState: SnackbarHostState,
    signedIn: Boolean,
    onOpenThread: (Note) -> Unit,
    onOpenPicker: (Note) -> Unit,
    onOpenMedia: (Note, Int) -> Unit,
    onReply: (Note) -> Unit,
    onQuote: (Note) -> Unit,
    onEdit: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onRedraft: (Note) -> Unit,
): NoteCallbacks {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val linkCopied = stringResource(R.string.note_link_copied)
    val textCopied = stringResource(R.string.note_text_copied)
    val openThread by rememberUpdatedState(onOpenThread)
    val openPicker by rememberUpdatedState(onOpenPicker)
    val openMedia by rememberUpdatedState(onOpenMedia)
    val reply by rememberUpdatedState(onReply)
    val quote by rememberUpdatedState(onQuote)
    val edit by rememberUpdatedState(onEdit)
    val delete by rememberUpdatedState(onDelete)
    val redraft by rememberUpdatedState(onRedraft)
    val isSignedIn by rememberUpdatedState(signedIn)
    return remember(feedViewModel, context, clipboard, uriHandler) {
        fun copy(label: String, text: String, message: String) {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbarHostState.showSnackbar(message)
            }
        }
        NoteCallbacks(
            openThread = { openThread(it) },
            reply = { note -> if (isSignedIn) reply(note) else feedViewModel.promptSignIn() },
            quote = { note -> if (isSignedIn) quote(note) else feedViewModel.promptSignIn() },
            openProfile = { uriHandler.openUri(it.profileUrl) },
            toggleReaction = { note, reaction -> feedViewModel.toggleReaction(note.id, reaction) },
            openReactionPicker = { note ->
                when {
                    !isSignedIn -> feedViewModel.promptSignIn()
                    note.acceptsOnlyLike() -> feedViewModel.reactDefault(note.id)
                    else -> openPicker(note)
                }
            },
            reactDefault = { feedViewModel.reactDefault(it.id) },
            renote = { feedViewModel.renote(it.id) },
            unrenote = { feedViewModel.unrenote(it.id) },
            checkRenoted = { feedViewModel.checkRenoted(it.id) },
            vote = { note, choices -> feedViewModel.votePoll(note.id, choices) },
            edit = { edit(it) },
            delete = { delete(it) },
            redraft = { redraft(it) },
            toggleBookmark = { feedViewModel.toggleBookmark(it.id) },
            checkBookmarked = { feedViewModel.checkBookmarked(it.id) },
            openMedia = { note, index -> openMedia(note, index) },
            openUrl = { uriHandler.openUri(it) },
            copyLink = { copy(it.user.handle, it.webUrl, linkCopied) },
            copyText = { copy(it.user.handle, it.text.orEmpty(), textCopied) },
            share = { note ->
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, note.webUrl)
                context.startActivity(Intent.createChooser(send, note.user.handle))
            },
        )
    }
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
    favorites: (@Composable () -> Unit)?,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val selected = feed.selected
    val timeline = feed.current
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
            val expanded = atTopOfSelected && !reduceMotion
            val label = stringResource(R.string.feed_compose)
            ExtendedFloatingActionButton(
                onClick = onCompose,
                expanded = expanded,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text(label) },
                modifier = Modifier.semantics { contentDescription = label },
            )
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
                    banner = if (feed.needsPermission) {
                        { ReauthorizeBanner(onDismiss = feedViewModel::permissionPromptHandled) }
                    } else {
                        null
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
private fun BookmarksPane(
    feed: FeedUiState,
    listState: LazyListState,
    environment: NoteEnvironment,
    noteCallbacks: NoteCallbacks,
    feedViewModel: FeedViewModel,
    reduceMotion: Boolean,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val bookmarks = feed.bookmarks
    val listCallbacks = remember(feedViewModel) {
        TimelineListCallbacks(
            onLoadMore = feedViewModel::loadMoreBookmarks,
            onRetry = feedViewModel::loadBookmarks,
            onRefresh = feedViewModel::loadBookmarks,
            onShowPending = {},
            onLogIn = feedViewModel::logIn,
            onAtTop = {},
            onVisibleNotes = {},
        )
    }
    LaunchedEffect(feedViewModel, feed.signedIn) { if (feed.signedIn) feedViewModel.loadBookmarks() }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    TooltipIconButton(
                        label = stringResource(R.string.bookmarks_back),
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                    )
                },
                actions = {
                    TooltipIconButton(
                        label = stringResource(R.string.feed_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = feedViewModel::loadBookmarks,
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
    ) { innerPadding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = bookmarks.refreshing,
            onRefresh = feedViewModel::loadBookmarks,
            state = pullState,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = bookmarks.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            TimelineList(
                kind = null,
                timeline = bookmarks,
                listState = listState,
                environment = environment,
                noteCallbacks = noteCallbacks,
                callbacks = listCallbacks,
                reduceMotion = reduceMotion,
                banner = if (feed.needsPermission) {
                    { ReauthorizeBanner(onDismiss = feedViewModel::permissionPromptHandled) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ReauthorizeBanner(onDismiss: () -> Unit) {
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
            PermissionBanner(onGrant = login::onSignInClick, onDismiss = onDismiss)
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.fortind.zenith.ui.social

import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.ui.discovery.AddToListSheet
import com.fortind.zenith.ui.discovery.CollectionsEventsEffect
import com.fortind.zenith.ui.discovery.CollectionsViewModel
import com.fortind.zenith.ui.discovery.DiscoveryViewModel
import com.fortind.zenith.ui.people.parseTagLink
import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortind.zenith.R
import com.fortind.zenith.account.reportNotePrefix
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.people.UserDetail
import com.fortind.zenith.people.followState
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.TimelineKind
import com.fortind.zenith.timeline.acceptsOnlyLike
import com.fortind.zenith.ui.composer.ComposerEvent
import com.fortind.zenith.ui.composer.ComposerHost
import com.fortind.zenith.ui.composer.ComposerTarget
import com.fortind.zenith.ui.composer.ComposerViewModel
import com.fortind.zenith.ui.feed.ConfirmBlockDialog
import com.fortind.zenith.ui.feed.ConfirmDeleteDialog
import com.fortind.zenith.ui.feed.FeedEvent
import com.fortind.zenith.ui.feed.FeedViewModel
import com.fortind.zenith.ui.feed.FullscreenVideo
import com.fortind.zenith.ui.feed.ImageViewer
import com.fortind.zenith.ui.motion.LocalSharedTransitionScope
import com.fortind.zenith.ui.motion.SharedMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import com.fortind.zenith.ui.feed.MuteDurationDialog
import com.fortind.zenith.ui.feed.NoteCallbacks
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.feed.ReactionPickerSheet
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.mfm.rememberMfmEnvironment
import com.fortind.zenith.ui.people.ConfirmFollowChangeDialog
import com.fortind.zenith.ui.people.ConfirmUnblockDialog
import com.fortind.zenith.ui.people.FollowFailure
import com.fortind.zenith.ui.people.ModerationAction
import com.fortind.zenith.ui.people.PageKeys
import com.fortind.zenith.ui.people.PeopleActions
import com.fortind.zenith.ui.people.PeopleEvent
import com.fortind.zenith.ui.people.PeopleViewModel
import com.fortind.zenith.ui.people.ProfileHandle
import com.fortind.zenith.ui.people.ReportDialog
import com.fortind.zenith.ui.people.ReportFailure
import com.fortind.zenith.ui.people.parseProfileLink
import com.fortind.zenith.ui.rememberNow
import com.fortind.zenith.ui.rememberReduceMotion
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

val LocalNoteEnvironment = compositionLocalOf<NoteEnvironment> { error("LocalNoteEnvironment is provided by SocialHost") }

@Stable
class SocialUi(
    val feed: FeedViewModel,
    val composer: ComposerViewModel,
    val playback: FeedPlayback,
    val snackbar: SnackbarHostState,
    val listStates: Map<TimelineKind, LazyListState>,
    val callbacks: NoteCallbacks,
    val startNewPost: () -> Unit,
    val people: PeopleViewModel,
    val peopleActions: PeopleActions,
    val pageKeys: PageKeys,
    val collections: CollectionsViewModel,
    val discovery: DiscoveryViewModel,
    val viewer: ViewerControl,
)

@Stable
class ViewerControl(private val open: State<Boolean>, private val close: State<() -> Unit>) {
    val isOpen: Boolean get() = open.value

    fun close() = close.value()
}

@Composable
fun SocialHost(
    selectedNoteId: String?,
    onOpenThread: (Note) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenOwnProfile: () -> Unit,
    onOpenHandle: (ProfileHandle) -> Unit,
    onOpenTag: (String) -> Unit,
    requestedReplyId: String? = null,
    onRequestedReplyHandled: () -> Unit = {},
    feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.Factory),
    collectionsViewModel: CollectionsViewModel = viewModel(factory = CollectionsViewModel.Factory),
    discoveryViewModel: DiscoveryViewModel = viewModel(factory = DiscoveryViewModel.Factory),
    composerViewModel: ComposerViewModel = viewModel(factory = ComposerViewModel.Factory),
    playback: FeedPlayback = viewModel(factory = FeedPlayback.Factory),
    peopleViewModel: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory),
    content: @Composable (SocialUi) -> Unit,
) {
    LifecycleResumeEffect(playback) { onPauseOrDispose { playback.pause() } }
    val feed by feedViewModel.uiState.collectAsStateWithLifecycle()
    val emojis by feedViewModel.emojis.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val reduceMotion = rememberReduceMotion()
    val listStates = rememberTimelineListStates()
    val motion = remember { SharedMotion() }

    var pickerNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(0) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var redraftTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var muteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var blockTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var followConfirmTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var personMuteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var personBlockTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var personUnblockTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var reportUserTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var reportNoteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var addToListTarget by remember { mutableStateOf<NoteUser?>(null) }
    val people by peopleViewModel.uiState.collectAsStateWithLifecycle()
    val pageKeys = remember { PageKeys() }
    val browser = LocalUriHandler.current
    val openHandle by rememberUpdatedState(onOpenHandle)
    val openTag by rememberUpdatedState(onOpenTag)
    val linkRouter = remember(browser) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val tag = parseTagLink(uri)
                val handle = parseProfileLink(uri)
                when {
                    tag != null -> openTag(tag)
                    handle != null -> openHandle(handle)
                    else -> browser.openUri(uri)
                }
            }
        }
    }

    val linkPreviews = (LocalContext.current.applicationContext as ZenithApplication).container.linkPreviews
    val now by rememberNow()
    val emojiUrl = remember(emojis) {
        { name: String -> emojis?.get(name)?.url ?: "https://${FortInstance.HOST}/emoji/$name.webp" }
    }
    val mfm = rememberMfmEnvironment(emojiUrl, animate = !reduceMotion)
    val environment = remember(
        now,
        feed.myUserId,
        feed.signedIn,
        feed.renoted,
        feed.renoteChecks,
        feed.bookmarked,
        feed.bookmarkChecks,
        feed.pinned,
        feed.threadMuted,
        emojis,
        mfm,
        selectedNoteId,
        playback,
        motion,
    ) {
        NoteEnvironment(
            linkPreviews = linkPreviews,
            pinned = feed.pinned,
            threadMuted = feed.threadMuted,
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
            selectedNoteId = selectedNoteId,
            playback = playback,
            motion = motion,
        )
    }
    val noteCallbacks = rememberNoteCallbacks(
        feedViewModel = feedViewModel,
        snackbarHostState = snackbarHostState,
        signedIn = feed.signedIn,
        onOpenThread = onOpenThread,
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
        onMuteAuthor = { note -> muteTarget = note.id },
        onBlockAuthor = { note -> blockTarget = note.id },
        onReport = { note -> reportNoteTarget = note.id },
        onOpenProfile = { user ->
            if (user.id == feed.myUserId) {
                onOpenOwnProfile()
            } else {
                peopleViewModel.seed(user)
                onOpenPerson(user.id)
            }
        },
    )
    val peopleActions = rememberPeopleActions(
        people = peopleViewModel,
        feed = feedViewModel,
        snackbarHostState = snackbarHostState,
        browser = browser,
        onConfirmFollow = { detail -> followConfirmTarget = detail.id },
        onMute = { detail -> personMuteTarget = detail.id },
        onBlock = { detail -> personBlockTarget = detail.id },
        onUnblock = { detail -> personUnblockTarget = detail.id },
        onReport = { detail -> reportUserTarget = detail.id },
        onAddToList = { addToListTarget = it },
    )
    val signedIn by rememberUpdatedState(feed.signedIn)
    val closeViewer = {
        motion.closeViewer()
        viewerNoteId = null
    }
    val viewerOpenState = rememberUpdatedState(viewerNoteId != null)
    val closeViewerState = rememberUpdatedState(closeViewer)
    val viewer = remember { ViewerControl(viewerOpenState, closeViewerState) }
    val ui = remember(feedViewModel, composerViewModel, playback, listStates, noteCallbacks, peopleViewModel, peopleActions, pageKeys, collectionsViewModel, discoveryViewModel) {
        SocialUi(
            feed = feedViewModel,
            composer = composerViewModel,
            playback = playback,
            snackbar = snackbarHostState,
            listStates = listStates,
            callbacks = noteCallbacks,
            startNewPost = {
                if (signedIn) composerViewModel.open(ComposerTarget.NewPost, null) else feedViewModel.promptSignIn()
            },
            people = peopleViewModel,
            peopleActions = peopleActions,
            pageKeys = pageKeys,
            collections = collectionsViewModel,
            discovery = discoveryViewModel,
            viewer = viewer,
        )
    }
    CollectionsEventsEffect(collectionsViewModel, snackbarHostState)
    FeedEventsEffect(feedViewModel, peopleViewModel, snackbarHostState, listStates, reduceMotion, onRedraft = composerViewModel::redraft)
    PeopleEventsEffect(peopleViewModel, feedViewModel, snackbarHostState)
    ComposerEventsEffect(composerViewModel, feedViewModel, snackbarHostState)
    LaunchedEffect(requestedReplyId) {
        val noteId = requestedReplyId ?: return@LaunchedEffect
        val note = withTimeoutOrNull(REPLY_NOTE_WAIT_MS) {
            feedViewModel.uiState.map { state -> state.thread?.takeIf { it.noteId == noteId }?.note }.filterNotNull().first()
        }
        if (note != null && feedViewModel.uiState.value.signedIn) composerViewModel.open(ComposerTarget.Reply(noteId), note)
        onRequestedReplyHandled()
    }

    val viewerShown = viewerNoteId != null
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { onEnter = { if (viewerNoteId != null) cancelFocusChange() } }
                    .focusGroup()
                    .then(if (viewerShown) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                CompositionLocalProvider(LocalNoteEnvironment provides environment, LocalUriHandler provides linkRouter, com.fortind.zenith.ui.feed.LocalNoteActionKit provides com.fortind.zenith.ui.feed.rememberNoteActionKit()) {
                    content(ui)
                }
            }
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = viewerNoteId?.let { ViewerRequest(it, viewerIndex) },
                transitionSpec = {
                    if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn(effects) togetherWith fadeOut(effects)
                },
                modifier = Modifier.fillMaxSize(),
                contentKey = { it?.noteId },
                label = "imageViewer",
            ) { request ->
                if (request != null) {
                    val note = feed.findNote(request.noteId)
                    val images = remember(note) { note?.files?.filter { it.kind == MediaKind.Image }.orEmpty() }
                    val uriHandler = LocalUriHandler.current
                    val heroSource = remember(request) { motion.imageSource }
                    val returnFocus = remember(request) { motion.returnFocus }
                    LaunchedEffect(images.isEmpty()) { if (images.isEmpty() && transition.targetState == EnterExitState.Visible) closeViewer() }
                    ImageViewer(
                        files = images,
                        initialIndex = request.index,
                        animate = !reduceMotion,
                        heroSource = heroSource,
                        returnFocus = returnFocus,
                        visibility = this,
                        onDismiss = closeViewer,
                        onOpenOriginal = uriHandler::openUri,
                    )
                }
            }
        }
    }

    addToListTarget?.let { user ->
        AddToListSheet(user = user, collections = collectionsViewModel, onDismiss = { addToListTarget = null })
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
    muteTarget?.let { id ->
        val author = feed.findNote(id)?.user
        if (author == null) {
            muteTarget = null
        } else {
            MuteDurationDialog(
                name = author.displayName,
                onSelect = { duration ->
                    muteTarget = null
                    feedViewModel.muteAuthor(id, duration.millis)
                },
                onDismiss = { muteTarget = null },
            )
        }
    }
    blockTarget?.let { id ->
        val author = feed.findNote(id)?.user
        if (author == null) {
            blockTarget = null
        } else {
            ConfirmBlockDialog(
                name = author.displayName,
                onConfirm = {
                    blockTarget = null
                    feedViewModel.blockAuthor(id)
                },
                onDismiss = { blockTarget = null },
            )
        }
    }
    followConfirmTarget?.let { id ->
        val detail = people.detailOf(id)
        if (detail == null) {
            followConfirmTarget = null
        } else {
            ConfirmFollowChangeDialog(
                state = detail.followState(people.myUserId),
                name = detail.user.displayName,
                onConfirm = {
                    followConfirmTarget = null
                    peopleViewModel.toggleFollow(id)
                },
                onDismiss = { followConfirmTarget = null },
            )
        }
    }
    personMuteTarget?.let { id ->
        val detail = people.detailOf(id)
        if (detail == null) {
            personMuteTarget = null
        } else {
            MuteDurationDialog(
                name = detail.user.displayName,
                onSelect = { duration ->
                    personMuteTarget = null
                    peopleViewModel.moderate(id, ModerationAction.Mute, duration.millis)
                },
                onDismiss = { personMuteTarget = null },
            )
        }
    }
    personBlockTarget?.let { id ->
        val detail = people.detailOf(id)
        if (detail == null) {
            personBlockTarget = null
        } else {
            ConfirmBlockDialog(
                name = detail.user.displayName,
                onConfirm = {
                    personBlockTarget = null
                    peopleViewModel.moderate(id, ModerationAction.Block)
                },
                onDismiss = { personBlockTarget = null },
            )
        }
    }
    personUnblockTarget?.let { id ->
        val detail = people.detailOf(id)
        if (detail == null) {
            personUnblockTarget = null
        } else {
            ConfirmUnblockDialog(
                name = detail.user.displayName,
                onConfirm = {
                    personUnblockTarget = null
                    peopleViewModel.moderate(id, ModerationAction.Unblock)
                },
                onDismiss = { personUnblockTarget = null },
            )
        }
    }
    reportUserTarget?.let { id ->
        val detail = people.detailOf(id)
        if (detail == null) {
            reportUserTarget = null
        } else {
            ReportDialog(
                name = detail.user.displayName,
                prefix = "",
                onSend = { text ->
                    reportUserTarget = null
                    peopleViewModel.report(id, text)
                },
                onDismiss = { reportUserTarget = null },
            )
        }
    }
    reportNoteTarget?.let { id ->
        val note = feed.findNote(id)
        if (note == null) {
            reportNoteTarget = null
        } else {
            val prefix = remember(note.id) { reportNotePrefix(note.webUrl, note.remoteUrl) }
            ReportDialog(
                name = note.user.displayName,
                prefix = prefix,
                onSend = { text ->
                    reportNoteTarget = null
                    peopleViewModel.report(note.user.id, prefix + text)
                },
                onDismiss = { reportNoteTarget = null },
            )
        }
    }
    if (playback.fullscreen && playback.current != null) {
        FullscreenVideo(playback = playback, onDismiss = playback::exitFullscreen)
    }
}

private data class ViewerRequest(val noteId: String, val index: Int)

@Composable
private fun rememberTimelineListStates(): Map<TimelineKind, LazyListState> {
    val home = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val following = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val local = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val global = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val lists = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    return remember(home, following, local, global, lists) {
        mapOf(
            TimelineKind.Home to home,
            TimelineKind.Following to following,
            TimelineKind.Local to local,
            TimelineKind.Global to global,
            TimelineKind.Lists to lists,
        )
    }
}

@Composable
private fun FeedEventsEffect(
    feedViewModel: FeedViewModel,
    peopleViewModel: PeopleViewModel,
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
                is FeedEvent.UserMuted -> {
                    peopleViewModel.relationChanged(event.userId)
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_user_muted, event.name)) }
                }
                is FeedEvent.RenotesHidden -> {
                    peopleViewModel.relationChanged(event.userId)
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_renotes_hidden, event.name)) }
                }
                is FeedEvent.UserBlocked -> {
                    peopleViewModel.relationChanged(event.userId)
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_user_blocked, event.name)) }
                }
                FeedEvent.ModerationFailed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_moderation_failed)) }
                FeedEvent.RateLimited -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_rate_limited)) }
                FeedEvent.NoteUnavailable -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_note_unavailable)) }
                FeedEvent.ThreadMuted -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_thread_muted)) }
                FeedEvent.ThreadUnmuted -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_thread_unmuted)) }
                FeedEvent.ThreadMuteFailed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_thread_mute_failed)) }
                FeedEvent.NotePinned -> {
                    peopleViewModel.uiState.value.myUserId?.let(peopleViewModel::refresh)
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_note_pinned)) }
                }
                FeedEvent.NoteUnpinned -> {
                    peopleViewModel.uiState.value.myUserId?.let(peopleViewModel::refresh)
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_note_unpinned)) }
                }
                FeedEvent.PinLimitReached -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_pin_limit)) }
                FeedEvent.PinFailed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.feed_pin_failed)) }
            }
        }
    }
}

@Composable
private fun PeopleEventsEffect(
    peopleViewModel: PeopleViewModel,
    feedViewModel: FeedViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(peopleViewModel, feedViewModel) {
        peopleViewModel.events.collect { event ->
            val message = when (event) {
                PeopleEvent.SignInRequired -> {
                    feedViewModel.promptSignIn()
                    null
                }
                PeopleEvent.PermissionMissing -> {
                    feedViewModel.requestPermission()
                    null
                }
                is PeopleEvent.FollowFailed -> when (event.failure) {
                    FollowFailure.BlockedByThem -> resources.getString(R.string.person_follow_blocked_by_them, event.name)
                    FollowFailure.YouBlocked -> resources.getString(R.string.person_follow_you_blocked, event.name)
                    FollowFailure.RateLimited -> resources.getString(R.string.feed_rate_limited)
                    FollowFailure.Other -> resources.getString(R.string.person_follow_failed)
                }
                is PeopleEvent.RequestFailed -> resources.getString(
                    if (event.accept) R.string.follows_accept_failed else R.string.follows_reject_failed,
                    event.name,
                )
                is PeopleEvent.Moderated -> {
                    when (event.action) {
                        ModerationAction.Mute, ModerationAction.Block -> feedViewModel.dropAuthor(event.userId)
                        ModerationAction.HideRenotes -> feedViewModel.dropRenotesBy(event.userId)
                        else -> Unit
                    }
                    when (event.action) {
                        ModerationAction.Mute -> resources.getString(R.string.feed_user_muted, event.name)
                        ModerationAction.Unmute -> resources.getString(R.string.person_unmuted, event.name)
                        ModerationAction.HideRenotes -> resources.getString(R.string.feed_renotes_hidden, event.name)
                        ModerationAction.ShowRenotes -> resources.getString(R.string.person_renotes_shown, event.name)
                        ModerationAction.Block -> resources.getString(R.string.feed_user_blocked, event.name)
                        ModerationAction.Unblock -> resources.getString(R.string.person_unblocked, event.name)
                    }
                }
                PeopleEvent.ModerationFailed -> resources.getString(R.string.feed_moderation_failed)
                PeopleEvent.Reported -> resources.getString(R.string.report_sent)
                is PeopleEvent.ReportFailed -> resources.getString(
                    when (event.failure) {
                        ReportFailure.Yourself -> R.string.report_failed_yourself
                        ReportFailure.Admin -> R.string.report_failed_admin
                        ReportFailure.RateLimited -> R.string.feed_rate_limited
                        ReportFailure.Other -> R.string.report_failed
                    },
                )
            }
            if (message != null) launch { snackbarHostState.showSnackbar(message) }
        }
    }
}

@Composable
private fun rememberPeopleActions(
    people: PeopleViewModel,
    feed: FeedViewModel,
    snackbarHostState: SnackbarHostState,
    browser: UriHandler,
    onConfirmFollow: (UserDetail) -> Unit,
    onMute: (UserDetail) -> Unit,
    onBlock: (UserDetail) -> Unit,
    onUnblock: (UserDetail) -> Unit,
    onReport: (UserDetail) -> Unit,
    onAddToList: (NoteUser) -> Unit,
): PeopleActions {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val linkCopied = stringResource(R.string.note_link_copied)
    val confirmFollow by rememberUpdatedState(onConfirmFollow)
    val mute by rememberUpdatedState(onMute)
    val block by rememberUpdatedState(onBlock)
    val unblock by rememberUpdatedState(onUnblock)
    val report by rememberUpdatedState(onReport)
    val addToList by rememberUpdatedState(onAddToList)
    return remember(people, feed, browser, context, clipboard) {
        PeopleActions(
            toggleFollow = { detail ->
                val state = people.uiState.value
                when {
                    !state.signedIn -> feed.promptSignIn()
                    detail.followState(state.myUserId).asksFirst -> confirmFollow(detail)
                    else -> people.toggleFollow(detail.id)
                }
            },
            moderate = { detail, action ->
                when (action) {
                    ModerationAction.Mute -> mute(detail)
                    ModerationAction.Block -> block(detail)
                    ModerationAction.Unblock -> unblock(detail)
                    else -> people.moderate(detail.id, action)
                }
            },
            report = { report(it) },
            addToList = { addToList(it) },
            openInBrowser = browser::openUri,
            copyLink = { user ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(user.handle, user.profileUrl)))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbarHostState.showSnackbar(linkCopied)
                }
            },
            share = { user ->
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, user.profileUrl)
                context.startActivity(Intent.createChooser(send, user.handle))
            },
        )
    }
}

private const val REPLY_NOTE_WAIT_MS = 20_000L

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
    onMuteAuthor: (Note) -> Unit,
    onBlockAuthor: (Note) -> Unit,
    onReport: (Note) -> Unit,
    onOpenProfile: (NoteUser) -> Unit,
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
    val muteAuthor by rememberUpdatedState(onMuteAuthor)
    val blockAuthor by rememberUpdatedState(onBlockAuthor)
    val reportNote by rememberUpdatedState(onReport)
    val openProfile by rememberUpdatedState(onOpenProfile)
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
            openProfile = { openProfile(it) },
            renoteAs = { note, visibility -> feedViewModel.renote(note.id, visibility) },
            togglePin = { note -> feedViewModel.togglePin(note.id) },
            toggleThreadMute = { note -> feedViewModel.toggleThreadMute(note.id) },
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
            muteAuthor = { muteAuthor(it) },
            hideAuthorRenotes = { feedViewModel.hideAuthorRenotes(it.id) },
            blockAuthor = { blockAuthor(it) },
            report = { reportNote(it) },
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

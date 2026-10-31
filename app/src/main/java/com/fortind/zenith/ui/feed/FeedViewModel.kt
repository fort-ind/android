package com.fortind.zenith.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.emoji.DEFAULT_LIKE
import com.fortind.zenith.emoji.InstanceInfo
import com.fortind.zenith.emoji.RecentReactionsStore
import com.fortind.zenith.emoji.UnicodeEmojiGroup
import com.fortind.zenith.emoji.UnicodeEmojiSource
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.net.isRateLimited
import com.fortind.zenith.timeline.Bookmark
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUpdate
import com.fortind.zenith.timeline.NotesApi
import com.fortind.zenith.timeline.Reactions
import com.fortind.zenith.timeline.TimelineKind
import com.fortind.zenith.timeline.TimelineStream
import com.fortind.zenith.timeline.TimelineStreamEvent
import com.fortind.zenith.timeline.TimelineUnavailableException
import com.fortind.zenith.timeline.acceptsOnlyLike
import com.fortind.zenith.timeline.canRenote
import com.fortind.zenith.timeline.captureIds
import com.fortind.zenith.timeline.hasVoted
import com.fortind.zenith.timeline.isRenoteOf
import com.fortind.zenith.timeline.mapNote
import com.fortind.zenith.timeline.mergeNewer
import com.fortind.zenith.timeline.mergeOlder
import com.fortind.zenith.timeline.removeNote
import com.fortind.zenith.timeline.withMyReaction
import com.fortind.zenith.timeline.withOtherVote
import com.fortind.zenith.timeline.withReactionCount
import com.fortind.zenith.timeline.withVotes
import com.fortind.zenith.timeline.withoutVotes
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private sealed interface FeedAuth {
    data object Pending : FeedAuth
    data object Guest : FeedAuth
    data class SignedIn(val token: String, val userId: String?) : FeedAuth
}

class FeedViewModel(
    private val session: UserSessionService,
    private val api: NotesApi,
    private val stream: TimelineStream,
    private val instance: InstanceInfo,
    private val recents: RecentReactionsStore,
    private val unicode: UnicodeEmojiSource,
    private val reactionDebounceMs: Long = REACTION_DEBOUNCE_MS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _events = Channel<FeedEvent>(Channel.BUFFERED)
    val events: Flow<FeedEvent> = _events.receiveAsFlow()

    val emojis = instance.emojis
    val meta = instance.meta
    val recentReactions: Flow<List<String>> = recents.recent

    private val _unicodeEmoji = MutableStateFlow<List<UnicodeEmojiGroup>>(emptyList())
    val unicodeEmoji: StateFlow<List<UnicodeEmojiGroup>> = _unicodeEmoji.asStateFlow()

    private val active = MutableStateFlow(false)
    private val selected = MutableStateFlow(TimelineKind.Home)
    private val visible = MutableStateFlow<Set<String>>(emptySet())
    private val atTop = TimelineKind.entries.associateWith { true }.toMutableMap()
    private val loadJobs = HashMap<TimelineKind, Job>()
    private var threadJob: Job? = null
    private val threadCache = object : LinkedHashMap<String, ThreadState>(THREAD_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ThreadState>?) = size > THREAD_CACHE_SIZE
    }
    private val confirmedReactions = HashMap<String, String?>()
    private val reactionJobs = HashMap<String, Job>()
    private val reactionMutex = Mutex()
    private val voting = HashSet<String>()
    private val deleting = HashSet<String>()
    private var bookmarksJob: Job? = null
    private var bookmarkCursor: String? = null
    private var auth: FeedAuth = FeedAuth.Pending

    private val token: String? get() = (auth as? FeedAuth.SignedIn)?.token

    private val myUserId: String? get() = (auth as? FeedAuth.SignedIn)?.userId

    init {
        instance.ensureFresh()
        viewModelScope.launch {
            authStates().collectLatest { next ->
                auth = next
                reset(next)
                if (next != FeedAuth.Pending) serve()
            }
        }
    }

    fun setActive(isActive: Boolean) {
        active.value = isActive
        if (isActive) instance.ensureFresh()
    }

    fun select(kind: TimelineKind) {
        if (auth == FeedAuth.Pending) return
        selected.value = kind
        _uiState.update { it.copy(selected = kind) }
        visible.value = emptySet()
        val timeline = _uiState.value.timeline(kind)
        if (timeline.status == TimelineStatus.Idle) loadFirstPage(kind)
    }

    fun refresh() {
        val kind = selected.value
        val timeline = _uiState.value.timeline(kind)
        when (timeline.status) {
            TimelineStatus.Ready -> if (!timeline.refreshing) {
                if (timeline.items.isEmpty() || timeline.gap) reload(kind) else catchUp(kind, toTop = true)
            }
            TimelineStatus.Failed, TimelineStatus.Idle, TimelineStatus.Unavailable -> loadFirstPage(kind)
            TimelineStatus.Loading, TimelineStatus.SignInRequired -> Unit
        }
    }

    fun retry(kind: TimelineKind = selected.value) {
        val timeline = _uiState.value.timeline(kind)
        if (timeline.items.isEmpty()) loadFirstPage(kind) else loadMore(kind, force = true)
    }

    fun loadMore(kind: TimelineKind = selected.value, force: Boolean = false) {
        val timeline = _uiState.value.timeline(kind)
        val last = timeline.items.lastOrNull() ?: return
        if (timeline.status != TimelineStatus.Ready || timeline.loadingMore || timeline.endReached || timeline.refreshing) return
        if (timeline.loadMoreFailed && !force) return
        updateTimeline(kind) { it.copy(loadingMore = true, loadMoreFailed = false) }
        val currentAuth = auth
        loadJobs[kind] = viewModelScope.launch {
            try {
                val page = api.timeline(token, kind, untilId = last.id, sinceId = null, limit = PAGE_SIZE)
                if (auth != currentAuth) return@launch
                updateTimeline(kind) {
                    it.copy(items = mergeOlder(it.items, page), loadingMore = false, endReached = page.isEmpty())
                }
            } catch (e: IOException) {
                handleFailure(e) { updateTimeline(kind) { it.copy(loadingMore = false, loadMoreFailed = true) } }
            }
        }
    }

    fun showPending(kind: TimelineKind = selected.value) {
        val timeline = _uiState.value.timeline(kind)
        if (timeline.gap) {
            reload(kind)
            return
        }
        if (timeline.pending.isEmpty()) return
        updateTimeline(kind) { it.copy(items = mergeNewer(it.items, it.pending), pending = emptyList()) }
        _events.trySend(FeedEvent.ScrollToTop(kind))
    }

    fun setAtTop(kind: TimelineKind, isAtTop: Boolean) {
        val was = atTop[kind] ?: true
        atTop[kind] = isAtTop
        if (isAtTop && !was) {
            val timeline = _uiState.value.timeline(kind)
            if (timeline.pending.isNotEmpty() && !timeline.gap) {
                updateTimeline(kind) { it.copy(items = mergeNewer(it.items, it.pending), pending = emptyList()) }
            }
        }
    }

    fun setVisibleNotes(kind: TimelineKind, noteIds: Set<String>) {
        if (kind != selected.value) return
        val items = _uiState.value.timeline(kind).items
        visible.value = noteIds.asSequence()
            .mapNotNull { id -> items.firstOrNull { it.id == id } }
            .flatMap { it.captureIds }
            .take(MAX_CAPTURED)
            .toSet()
    }

    fun openThread(noteId: String) {
        val existing = _uiState.value.thread
        if (existing?.noteId == noteId && !existing.failed) return
        threadJob?.cancel()
        val cached = threadCache[noteId]
        val seed = cached ?: ThreadState(noteId = noteId, note = findAnywhere(noteId), loading = true)
        _uiState.update { it.copy(thread = seed.copy(loading = true, failed = false)) }
        val currentAuth = auth
        threadJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val note = api.show(token, noteId)
                    val ancestors = if (note.reply != null) api.conversation(token, noteId, THREAD_ANCESTORS) else emptyList()
                    val replies = api.children(token, noteId, untilId = null, limit = PAGE_SIZE)
                    if (auth != currentAuth) return@coroutineScope
                    val loaded = ThreadState(
                        noteId = noteId,
                        ancestors = ancestors,
                        note = note,
                        replies = replies,
                        loading = false,
                        repliesEnd = replies.size < PAGE_SIZE,
                    )
                    threadCache[noteId] = loaded
                    _uiState.update { state -> if (state.thread?.noteId == noteId) state.copy(thread = loaded) else state }
                }
            } catch (e: IOException) {
                handleFailure(e) {
                    _uiState.update { state ->
                        if (state.thread?.noteId == noteId) state.copy(thread = state.thread.copy(loading = false, failed = true)) else state
                    }
                    if (e is HttpStatusException && e.apiErrorCode == ApiErrorCodes.NO_SUCH_NOTE) _events.trySend(FeedEvent.NoteUnavailable)
                }
            }
        }
    }

    fun closeThread() {
        threadJob?.cancel()
        _uiState.update { it.copy(thread = null) }
    }

    fun loadMoreReplies() {
        val thread = _uiState.value.thread ?: return
        val last = thread.replies.lastOrNull() ?: return
        if (thread.loading || thread.repliesLoadingMore || thread.repliesEnd) return
        _uiState.update { it.copy(thread = thread.copy(repliesLoadingMore = true)) }
        viewModelScope.launch {
            val page = try {
                api.children(token, thread.noteId, untilId = last.id, limit = PAGE_SIZE)
            } catch (e: IOException) {
                handleFailure(e) {}
                emptyList()
            }
            _uiState.update { state ->
                val current = state.thread
                if (current?.noteId != thread.noteId) {
                    state
                } else {
                    state.copy(
                        thread = current.copy(
                            replies = mergeOlder(current.replies, page),
                            repliesLoadingMore = false,
                            repliesEnd = page.size < PAGE_SIZE,
                        ),
                    )
                }
            }
        }
    }

    fun toggleReaction(noteId: String, reaction: String) {
        val note = findAnywhere(noteId) ?: return
        val desired = if (Reactions.sameReaction(note.myReaction, reaction)) null else reaction
        setMyReaction(note, desired)
    }

    fun react(noteId: String, reaction: String) {
        val note = findAnywhere(noteId) ?: return
        val chosen = if (note.acceptsOnlyLike()) defaultLike() else reaction
        if (Reactions.sameReaction(note.myReaction, chosen)) return
        setMyReaction(note, chosen)
        viewModelScope.launch { recents.record(chosen) }
    }

    fun reactDefault(noteId: String) {
        val note = findAnywhere(noteId) ?: return
        val like = defaultLike()
        setMyReaction(note, if (Reactions.sameReaction(note.myReaction, like)) null else like)
    }

    fun checkRenoted(noteId: String) {
        val token = token ?: return
        val userId = myUserId ?: return
        val state = _uiState.value
        if (noteId in state.renoted || noteId in state.renoteChecks) return
        _uiState.update { it.copy(renoteChecks = it.renoteChecks + noteId) }
        viewModelScope.launch {
            val renoted = try {
                api.renotedByMe(token, noteId, userId)
            } catch (e: IOException) {
                handleFailure(e) {}
                false
            }
            _uiState.update {
                it.copy(renoted = if (renoted) it.renoted + noteId else it.renoted)
            }
        }
    }

    fun renote(noteId: String) {
        val token = token ?: return requireSignIn()
        val note = findAnywhere(noteId) ?: return
        if (!note.canRenote(myUserId) || noteId in _uiState.value.renoted) return
        _uiState.update { it.copy(renoted = it.renoted + noteId) }
        mutateEverywhere(noteId) { it.copy(renoteCount = it.renoteCount + 1) }
        viewModelScope.launch {
            try {
                api.renote(token, noteId, note.visibility, note.localOnly)
            } catch (e: IOException) {
                _uiState.update { it.copy(renoted = it.renoted - noteId) }
                mutateEverywhere(noteId) { it.copy(renoteCount = (it.renoteCount - 1).coerceAtLeast(0)) }
                handleFailure(e) { _events.trySend(if (e.isRateLimited) FeedEvent.RateLimited else FeedEvent.RenoteFailed) }
            }
        }
    }

    fun unrenote(noteId: String) {
        val token = token ?: return requireSignIn()
        val userId = myUserId
        if (noteId !in _uiState.value.renoted) return
        _uiState.update { state ->
            state.copy(
                renoted = state.renoted - noteId,
                timelines = state.timelines.mapValues { (_, timeline) ->
                    if (userId == null) {
                        timeline
                    } else {
                        timeline.copy(
                            items = timeline.items.filterNot { it.isRenoteOf(noteId, userId) },
                            pending = timeline.pending.filterNot { it.isRenoteOf(noteId, userId) },
                        )
                    }
                },
            )
        }
        mutateEverywhere(noteId) { it.copy(renoteCount = (it.renoteCount - 1).coerceAtLeast(0)) }
        viewModelScope.launch {
            try {
                api.unrenote(token, noteId)
            } catch (e: IOException) {
                _uiState.update { it.copy(renoted = it.renoted + noteId) }
                mutateEverywhere(noteId) { it.copy(renoteCount = it.renoteCount + 1) }
                handleFailure(e) { _events.trySend(if (e.isRateLimited) FeedEvent.RateLimited else FeedEvent.RenoteFailed) }
            }
        }
    }

    fun votePoll(noteId: String, choices: List<Int>) {
        val token = token ?: return requireSignIn()
        val poll = findAnywhere(noteId)?.poll ?: return
        val picked = choices.distinct().filter { it in poll.choices.indices }
        if (picked.isEmpty() || poll.hasVoted || (!poll.multiple && picked.size > 1) || !voting.add(noteId)) return
        mutateEverywhere(noteId) { note -> note.copy(poll = note.poll?.withVotes(picked)) }
        viewModelScope.launch {
            val confirmed = mutableSetOf<Int>()
            try {
                picked.forEach { choice ->
                    api.vote(token, noteId, choice)
                    confirmed += choice
                }
            } catch (e: IOException) {
                val unconfirmed = picked.filterNot { it in confirmed }
                mutateEverywhere(noteId) { note -> note.copy(poll = note.poll?.withoutVotes(unconfirmed)) }
                handleVoteFailure(e, token, noteId)
            } finally {
                voting.remove(noteId)
            }
        }
    }

    private suspend fun handleVoteFailure(error: IOException, token: String, noteId: String) {
        val code = (error as? HttpStatusException)?.apiErrorCode
        if (code == ApiErrorCodes.ALREADY_VOTED || code == ApiErrorCodes.ALREADY_EXPIRED) {
            refreshPoll(token, noteId)
            if (code == ApiErrorCodes.ALREADY_EXPIRED) _events.trySend(FeedEvent.PollEnded)
        } else {
            handleFailure(error) { _events.trySend(if (error.isRateLimited) FeedEvent.RateLimited else FeedEvent.PollVoteFailed) }
        }
    }

    private suspend fun refreshPoll(token: String, noteId: String) {
        try {
            val fresh = api.show(token, noteId)
            mutateEverywhere(noteId) { it.copy(poll = fresh.poll) }
        } catch (e: IOException) {
            handleFailure(e) {}
        }
    }

    fun checkBookmarked(noteId: String) {
        val token = token ?: return
        if (noteId in _uiState.value.bookmarkChecks) return
        _uiState.update { it.copy(bookmarkChecks = it.bookmarkChecks + noteId) }
        viewModelScope.launch {
            try {
                val bookmarked = api.isBookmarked(token, noteId)
                _uiState.update { it.copy(bookmarked = if (bookmarked) it.bookmarked + noteId else it.bookmarked - noteId) }
            } catch (e: IOException) {
                handleFailure(e) {}
            } finally {
                _uiState.update { it.copy(bookmarkChecks = it.bookmarkChecks - noteId) }
            }
        }
    }

    fun toggleBookmark(noteId: String) {
        val token = token ?: return requireSignIn()
        val note = findAnywhere(noteId) ?: return
        val remove = noteId in _uiState.value.bookmarked
        _uiState.update { it.copy(bookmarked = if (remove) it.bookmarked - noteId else it.bookmarked + noteId) }
        viewModelScope.launch {
            try {
                if (remove) unbookmarkTolerant(token, noteId) else bookmarkTolerant(token, noteId)
                _uiState.update { state -> state.copy(bookmarks = state.bookmarks.withBookmark(note, present = !remove)) }
                _events.trySend(if (remove) FeedEvent.BookmarkRemoved else FeedEvent.Bookmarked)
            } catch (e: IOException) {
                _uiState.update { it.copy(bookmarked = if (remove) it.bookmarked + noteId else it.bookmarked - noteId) }
                handleFailure(e) { _events.trySend(if (e.isRateLimited) FeedEvent.RateLimited else FeedEvent.BookmarkFailed) }
            }
        }
    }

    fun loadBookmarks() {
        val token = token ?: return
        val current = _uiState.value.bookmarks
        if (current.status == TimelineStatus.Loading || current.refreshing) return
        bookmarksJob?.cancel()
        val hasItems = current.items.isNotEmpty()
        _uiState.update {
            it.copy(
                bookmarks = it.bookmarks.copy(
                    status = if (hasItems) TimelineStatus.Ready else TimelineStatus.Loading,
                    refreshing = hasItems,
                    loadingMore = false,
                    loadMoreFailed = false,
                ),
            )
        }
        bookmarksJob = viewModelScope.launch {
            try {
                val page = api.bookmarks(token, null, PAGE_SIZE)
                bookmarkCursor = page.lastOrNull()?.id
                _uiState.update { state ->
                    state.copy(
                        bookmarks = TimelineState(items = page.map(Bookmark::note), status = TimelineStatus.Ready, endReached = page.isEmpty()),
                        bookmarked = state.bookmarked + page.map { it.note.id },
                    )
                }
            } catch (e: IOException) {
                handleFailure(e) {}
                _uiState.update {
                    it.copy(
                        bookmarks = it.bookmarks.copy(
                            status = if (it.bookmarks.items.isEmpty()) TimelineStatus.Failed else TimelineStatus.Ready,
                            refreshing = false,
                        ),
                    )
                }
            }
        }
    }

    fun loadMoreBookmarks() {
        val token = token ?: return
        val cursor = bookmarkCursor ?: return
        val state = _uiState.value.bookmarks
        if (state.status != TimelineStatus.Ready || state.loadingMore || state.refreshing || state.endReached) return
        _uiState.update { it.copy(bookmarks = it.bookmarks.copy(loadingMore = true, loadMoreFailed = false)) }
        bookmarksJob = viewModelScope.launch {
            try {
                val page = api.bookmarks(token, cursor, PAGE_SIZE)
                bookmarkCursor = page.lastOrNull()?.id ?: cursor
                _uiState.update { current ->
                    current.copy(
                        bookmarks = current.bookmarks.copy(
                            items = mergeOlder(current.bookmarks.items, page.map(Bookmark::note)),
                            loadingMore = false,
                            endReached = page.isEmpty(),
                        ),
                        bookmarked = current.bookmarked + page.map { it.note.id },
                    )
                }
            } catch (e: IOException) {
                handleFailure(e) {}
                _uiState.update { it.copy(bookmarks = it.bookmarks.copy(loadingMore = false, loadMoreFailed = true)) }
            }
        }
    }

    fun deleteNote(noteId: String) = delete(noteId, redraft = false)

    fun redraftNote(noteId: String) = delete(noteId, redraft = true)

    private fun delete(noteId: String, redraft: Boolean) {
        val token = token ?: return requireSignIn()
        val note = findAnywhere(noteId) ?: return
        if (myUserId == null || note.user.id != myUserId || !deleting.add(noteId)) return
        viewModelScope.launch {
            try {
                try {
                    api.delete(token, noteId)
                } catch (e: HttpStatusException) {
                    if (e.apiErrorCode != ApiErrorCodes.NO_SUCH_NOTE) throw e
                }
                removeEverywhere(noteId)
                _events.trySend(if (redraft) FeedEvent.Redraft(note) else FeedEvent.NoteDeleted)
            } catch (e: IOException) {
                handleFailure(e) { _events.trySend(if (e.isRateLimited) FeedEvent.RateLimited else FeedEvent.DeleteFailed) }
            } finally {
                deleting.remove(noteId)
            }
        }
    }

    private suspend fun bookmarkTolerant(token: String, noteId: String) {
        try {
            api.bookmark(token, noteId)
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode != ApiErrorCodes.ALREADY_FAVORITED) throw e
        }
    }

    private suspend fun unbookmarkTolerant(token: String, noteId: String) {
        try {
            api.unbookmark(token, noteId)
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode != ApiErrorCodes.NOT_FAVORITED) throw e
        }
    }

    private fun TimelineState.withBookmark(note: Note, present: Boolean): TimelineState = when {
        !present -> copy(items = items.filterNot { it.id == note.id })
        status == TimelineStatus.Ready && items.none { it.id == note.id } -> copy(items = listOf(note) + items)
        else -> this
    }

    fun noteCreated(note: Note) {
        val parent = note.reply
        if (parent != null) {
            mutateEverywhere(parent.id) { it.copy(repliesCount = it.repliesCount + 1) }
            _uiState.update { state ->
                val thread = state.thread
                if (thread?.noteId == parent.id) state.copy(thread = thread.withReply(note)) else state
            }
            threadCache[parent.id]?.let { threadCache[parent.id] = it.withReply(note) }
            return
        }
        note.renote?.let { quoted -> mutateEverywhere(quoted.id) { it.copy(renoteCount = it.renoteCount + 1) } }
        if (_uiState.value.signedIn) {
            updateTimeline(TimelineKind.Home) { timeline ->
                if (timeline.status == TimelineStatus.Ready) timeline.copy(items = mergeNewer(timeline.items, listOf(note))) else timeline
            }
            if (selected.value == TimelineKind.Home) _events.trySend(FeedEvent.ScrollToTop(TimelineKind.Home))
        }
    }

    fun noteEdited(note: Note) {
        mutateEverywhere(note.id) {
            it.copy(
                text = note.text,
                textMfm = note.textMfm,
                cw = note.cw,
                cwMfm = note.cwMfm,
                visibility = note.visibility,
                localOnly = note.localOnly,
                files = note.files,
                editedAtMillis = note.editedAtMillis ?: System.currentTimeMillis(),
            )
        }
    }

    fun requestPermission() {
        _uiState.update { it.copy(needsPermission = true) }
    }

    fun loadUnicodeEmoji() {
        if (_unicodeEmoji.value.isNotEmpty()) return
        viewModelScope.launch {
            _unicodeEmoji.value = try {
                unicode.groups()
            } catch (_: IOException) {
                emptyList()
            }
        }
    }

    fun logIn() = session.leaveGuest()

    fun permissionPromptHandled() {
        _uiState.update { it.copy(needsPermission = false) }
    }

    private fun authStates(): Flow<FeedAuth> = combine(session.state, session.credentialsVersion) { state, _ -> state }
        .map { state ->
            when (state) {
                is SessionState.SignedIn -> session.token()?.let { FeedAuth.SignedIn(it, state.user?.id) } ?: FeedAuth.Pending
                SessionState.Guest -> FeedAuth.Guest
                SessionState.Restoring, SessionState.SignedOut -> FeedAuth.Pending
            }
        }
        .distinctUntilChanged()

    private fun reset(next: FeedAuth) {
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        threadJob?.cancel()
        threadCache.clear()
        reactionJobs.values.forEach { it.cancel() }
        reactionJobs.clear()
        confirmedReactions.clear()
        bookmarksJob?.cancel()
        bookmarkCursor = null
        TimelineKind.entries.forEach { atTop[it] = true }
        visible.value = emptySet()
        val signedIn = next is FeedAuth.SignedIn
        val initial = if (signedIn) TimelineKind.Home else TimelineKind.Local
        selected.value = initial
        _uiState.value = FeedUiState(
            authResolved = next != FeedAuth.Pending,
            signedIn = signedIn,
            myUserId = (next as? FeedAuth.SignedIn)?.userId,
            selected = initial,
            timelines = TimelineKind.entries.associateWith { kind ->
                TimelineState(status = if (kind.requiresAuth && !signedIn) TimelineStatus.SignInRequired else TimelineStatus.Idle)
            },
        )
    }

    private suspend fun serve() {
        loadFirstPage(selected.value)
        coroutineScope {
            launch {
                combine(active, selected) { isActive, kind -> isActive to kind }
                    .distinctUntilChanged()
                    .collectLatest { (isActive, kind) -> if (isActive && canStream(kind)) listen(kind) }
            }
            launch {
                active.collectLatest { isActive ->
                    if (isActive) stream.updates(token, capturedIds()).collect(::onUpdate)
                }
            }
        }
    }

    private fun canStream(kind: TimelineKind): Boolean = !kind.requiresAuth || token != null

    private fun capturedIds(): Flow<Set<String>> = combine(visible, _uiState.map { state ->
        state.thread?.notes?.flatMap { it.captureIds.toList() }?.toSet().orEmpty()
    }.distinctUntilChanged()) { shown, thread -> (shown + thread).take(MAX_CAPTURED).toSet() }
        .distinctUntilChanged()

    private suspend fun listen(kind: TimelineKind) {
        stream.events(token, kind).collect { event ->
            when (event) {
                TimelineStreamEvent.Connected -> {
                    val timeline = _uiState.value.timeline(kind)
                    if (timeline.status == TimelineStatus.Ready && timeline.items.isNotEmpty()) catchUp(kind, toTop = false)
                }
                is TimelineStreamEvent.Posted -> onPosted(kind, event.note)
            }
        }
    }

    private fun onPosted(kind: TimelineKind, note: Note) {
        updateTimeline(kind) { timeline ->
            when {
                timeline.status != TimelineStatus.Ready -> timeline
                timeline.items.any { it.id == note.id } -> timeline
                atTop[kind] == true && !timeline.gap -> timeline.copy(items = mergeNewer(timeline.items, listOf(note)))
                timeline.pending.size >= MAX_PENDING -> timeline.copy(gap = true)
                else -> timeline.copy(pending = mergeNewer(timeline.pending, listOf(note)))
            }
        }
    }

    private fun loadFirstPage(kind: TimelineKind) {
        val timeline = _uiState.value.timeline(kind)
        if (timeline.status == TimelineStatus.SignInRequired) return
        loadJobs[kind]?.cancel()
        updateTimeline(kind) { it.copy(status = TimelineStatus.Loading, loadMoreFailed = false) }
        val currentAuth = auth
        loadJobs[kind] = viewModelScope.launch {
            try {
                val page = api.timeline(token, kind, untilId = null, sinceId = null, limit = PAGE_SIZE)
                if (auth != currentAuth) return@launch
                updateTimeline(kind) {
                    TimelineState(items = page, status = TimelineStatus.Ready, endReached = page.isEmpty())
                }
            } catch (_: TimelineUnavailableException) {
                updateTimeline(kind) { it.copy(status = TimelineStatus.Unavailable) }
            } catch (e: IOException) {
                handleFailure(e) { updateTimeline(kind) { it.copy(status = TimelineStatus.Failed) } }
            }
        }
    }

    private fun reload(kind: TimelineKind) {
        loadJobs[kind]?.cancel()
        updateTimeline(kind) { it.copy(refreshing = true) }
        val currentAuth = auth
        loadJobs[kind] = viewModelScope.launch {
            try {
                val page = api.timeline(token, kind, untilId = null, sinceId = null, limit = PAGE_SIZE)
                if (auth != currentAuth) return@launch
                updateTimeline(kind) { TimelineState(items = page, status = TimelineStatus.Ready, endReached = page.isEmpty()) }
                _events.trySend(FeedEvent.ScrollToTop(kind))
            } catch (e: IOException) {
                updateTimeline(kind) { it.copy(refreshing = false) }
                handleFailure(e) {}
            }
        }
    }

    private fun catchUp(kind: TimelineKind, toTop: Boolean) {
        val timeline = _uiState.value.timeline(kind)
        val newest = (timeline.pending + timeline.items).maxWithOrNull(compareBy<Note> { it.createdAtMillis }.thenBy { it.id })
            ?: return reload(kind)
        if (toTop) updateTimeline(kind) { it.copy(refreshing = true) }
        val currentAuth = auth
        viewModelScope.launch {
            try {
                val page = api.timeline(token, kind, untilId = null, sinceId = newest.id, limit = CATCH_UP_LIMIT)
                if (auth != currentAuth) return@launch
                val overflow = page.size >= CATCH_UP_LIMIT
                if (toTop && overflow) {
                    reload(kind)
                    return@launch
                }
                updateTimeline(kind) { current ->
                    if (toTop || (atTop[kind] == true && !current.gap)) {
                        current.copy(
                            items = mergeNewer(current.items, mergeNewer(current.pending, page)),
                            pending = emptyList(),
                            refreshing = false,
                            gap = current.gap || overflow,
                        )
                    } else {
                        current.copy(pending = mergeNewer(current.pending, page), gap = current.gap || overflow, refreshing = false)
                    }
                }
                if (toTop) _events.trySend(FeedEvent.ScrollToTop(kind))
            } catch (e: IOException) {
                updateTimeline(kind) { it.copy(refreshing = false) }
                handleFailure(e) {}
            }
        }
    }

    private suspend fun onUpdate(update: NoteUpdate) {
        val me = myUserId
        when (update) {
            is NoteUpdate.Reacted -> if (update.userId != me || me == null) {
                mutateEverywhere(update.noteId) { it.withReactionCount(update.reaction, 1, update.emojiUrl) }
            }
            is NoteUpdate.Unreacted -> if (update.userId != me || me == null) {
                mutateEverywhere(update.noteId) { it.withReactionCount(update.reaction, -1) }
            }
            is NoteUpdate.Deleted -> removeEverywhere(update.noteId)
            is NoteUpdate.PollVoted -> if (update.userId != me || me == null) {
                mutateEverywhere(update.noteId) { note -> note.copy(poll = note.poll?.withOtherVote(update.choice)) }
            }
            is NoteUpdate.Updated -> if (update.hasContent) {
                val textMfm = withContext(Dispatchers.Default) { update.text?.let(MfmParser::parse).orEmpty() }
                val cwMfm = withContext(Dispatchers.Default) { update.cw?.let(MfmParser::parse) }
                mutateEverywhere(update.noteId) {
                    it.copy(text = update.text, textMfm = textMfm, cw = update.cw, cwMfm = cwMfm, editedAtMillis = System.currentTimeMillis())
                }
            } else {
                try {
                    val fresh = api.show(token, update.noteId)
                    mutateEverywhere(update.noteId) {
                        it.copy(
                            text = fresh.text,
                            textMfm = fresh.textMfm,
                            cw = fresh.cw,
                            cwMfm = fresh.cwMfm,
                            files = fresh.files,
                            poll = fresh.poll,
                            editedAtMillis = fresh.editedAtMillis,
                        )
                    }
                } catch (e: IOException) {
                    handleFailure(e) {}
                }
            }
        }
    }

    private fun setMyReaction(note: Note, desired: String?) {
        val token = token ?: return requireSignIn()
        val noteId = note.id
        if (!confirmedReactions.containsKey(noteId)) confirmedReactions[noteId] = note.myReaction
        mutateEverywhere(noteId) { it.withMyReaction(desired) }
        reactionJobs[noteId]?.cancel()
        reactionJobs[noteId] = viewModelScope.launch {
            delay(reactionDebounceMs)
            reconcileReaction(noteId, token)
        }
    }

    private suspend fun reconcileReaction(noteId: String, token: String) = reactionMutex.withLock {
        val confirmed = confirmedReactions[noteId]
        val desired = findAnywhere(noteId)?.myReaction
        if (Reactions.sameReaction(confirmed, desired)) {
            confirmedReactions.remove(noteId)
            return@withLock
        }
        val apiReaction = desired?.let { Reactions.toApi(it, instance.emojis.value?.names) }
        if (desired != null && apiReaction == null) {
            val restore = confirmedReactions.remove(noteId)
            mutateEverywhere(noteId) { it.withMyReaction(restore) }
            _events.trySend(FeedEvent.ReactionFailed)
            return@withLock
        }
        try {
            withContext(NonCancellable) {
                if (confirmed != null) unreactTolerant(token, noteId)
                if (apiReaction != null) reactTolerant(token, noteId, apiReaction)
            }
            confirmedReactions[noteId] = desired
            if (Reactions.sameReaction(findAnywhere(noteId)?.myReaction, desired)) confirmedReactions.remove(noteId)
        } catch (e: IOException) {
            val restore = confirmedReactions.remove(noteId)
            mutateEverywhere(noteId) { it.withMyReaction(restore) }
            handleFailure(e) { _events.trySend(if (e.isRateLimited) FeedEvent.RateLimited else FeedEvent.ReactionFailed) }
        }
    }

    private suspend fun unreactTolerant(token: String, noteId: String) {
        try {
            api.unreact(token, noteId)
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode != ApiErrorCodes.NOT_REACTED) throw e
        }
    }

    private suspend fun reactTolerant(token: String, noteId: String, reaction: String) {
        try {
            api.react(token, noteId, reaction)
        } catch (e: HttpStatusException) {
            if (e.apiErrorCode != ApiErrorCodes.ALREADY_REACTED) throw e
            api.unreact(token, noteId)
            api.react(token, noteId, reaction)
        }
    }

    private fun defaultLike(): String = instance.meta.value?.defaultLike ?: DEFAULT_LIKE

    fun promptSignIn() = requireSignIn()

    private fun requireSignIn() {
        _events.trySend(FeedEvent.SignInRequired)
    }

    private fun findAnywhere(noteId: String): Note? = _uiState.value.findNote(noteId)

    private fun mutateEverywhere(noteId: String, transform: (Note) -> Note) {
        _uiState.update { state ->
            state.copy(
                timelines = state.timelines.mapValues { (_, timeline) ->
                    val items = timeline.items.mapNote(noteId, transform)
                    val pending = timeline.pending.mapNote(noteId, transform)
                    if (items === timeline.items && pending === timeline.pending) timeline else timeline.copy(items = items, pending = pending)
                },
                thread = state.thread?.mapThread { it.mapNote(noteId, transform) },
                bookmarks = state.bookmarks.items.mapNote(noteId, transform).let { items ->
                    if (items === state.bookmarks.items) state.bookmarks else state.bookmarks.copy(items = items)
                },
            )
        }
        threadCache.entries.forEach { entry -> entry.setValue(entry.value.mapThread { it.mapNote(noteId, transform) }) }
    }

    private fun removeEverywhere(noteId: String) {
        _uiState.update { state ->
            state.copy(
                timelines = state.timelines.mapValues { (_, timeline) ->
                    timeline.copy(items = timeline.items.removeNote(noteId), pending = timeline.pending.removeNote(noteId))
                },
                thread = state.thread?.mapThread { it.removeNote(noteId) },
                bookmarks = state.bookmarks.copy(items = state.bookmarks.items.removeNote(noteId)),
            )
        }
        threadCache.remove(noteId)
    }

    private fun ThreadState.mapThread(transform: (List<Note>) -> List<Note>): ThreadState {
        val ancestors = transform(ancestors)
        val note = note?.let { transform(listOf(it)).firstOrNull() ?: it.copy(deleted = true) }
        val replies = transform(replies)
        return copy(ancestors = ancestors, note = note, replies = replies)
    }

    private fun updateTimeline(kind: TimelineKind, transform: (TimelineState) -> TimelineState) {
        _uiState.update { state -> state.copy(timelines = state.timelines + (kind to transform(state.timeline(kind)))) }
    }

    private fun handleFailure(error: IOException, onOther: () -> Unit) {
        when (error) {
            is MissingPermissionException -> _uiState.update { it.copy(needsPermission = true) }
            is TokenRejectedException -> viewModelScope.launch { session.signOut() }
            else -> onOther()
        }
    }

    companion object {
        const val PAGE_SIZE = 30
        const val CATCH_UP_LIMIT = 100
        const val MAX_PENDING = 200
        const val MAX_CAPTURED = 60
        const val THREAD_ANCESTORS = 30
        const val THREAD_CACHE_SIZE = 8
        const val REACTION_DEBOUNCE_MS = 600L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                FeedViewModel(
                    session = app.container.sessionService,
                    api = app.container.notesApi,
                    stream = app.container.timelineStream,
                    instance = app.container.emojiRepository,
                    recents = app.container.recentReactions,
                    unicode = app.container.unicodeEmoji,
                )
            }
        }
    }
}

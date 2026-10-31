package com.fortind.zenith.ui.feed

import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.TimelineKind
import com.fortind.zenith.timeline.findNote
import com.fortind.zenith.timeline.mergeNewer

enum class TimelineStatus { Idle, Loading, Ready, Failed, Unavailable, SignInRequired }

data class TimelineState(
    val items: List<Note> = emptyList(),
    val status: TimelineStatus = TimelineStatus.Idle,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val endReached: Boolean = false,
    val pending: List<Note> = emptyList(),
    val gap: Boolean = false,
) {
    val hasPending: Boolean get() = pending.isNotEmpty() || gap
}

data class ThreadState(
    val noteId: String,
    val ancestors: List<Note> = emptyList(),
    val note: Note? = null,
    val replies: List<Note> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val repliesLoadingMore: Boolean = false,
    val repliesEnd: Boolean = false,
) {
    val notes: List<Note> get() = ancestors + listOfNotNull(note) + replies

    fun withReply(reply: Note): ThreadState = copy(replies = mergeNewer(replies, listOf(reply)))
}

data class FeedUiState(
    val authResolved: Boolean = false,
    val signedIn: Boolean = false,
    val myUserId: String? = null,
    val selected: TimelineKind = TimelineKind.Home,
    val timelines: Map<TimelineKind, TimelineState> = TimelineKind.entries.associateWith { TimelineState() },
    val thread: ThreadState? = null,
    val renoted: Set<String> = emptySet(),
    val renoteChecks: Set<String> = emptySet(),
    val needsPermission: Boolean = false,
    val bookmarks: TimelineState = TimelineState(),
    val bookmarked: Set<String> = emptySet(),
    val bookmarkChecks: Set<String> = emptySet(),
) {
    fun timeline(kind: TimelineKind): TimelineState = timelines[kind] ?: TimelineState()

    val current: TimelineState get() = timeline(selected)

    fun findNote(noteId: String): Note? =
        timelines.values.firstNotNullOfOrNull { (it.items + it.pending).findNote(noteId) }
            ?: thread?.notes?.findNote(noteId)
            ?: bookmarks.items.findNote(noteId)
}

sealed interface FeedEvent {
    data object ReactionFailed : FeedEvent

    data object RenoteFailed : FeedEvent

    data object Bookmarked : FeedEvent

    data object BookmarkRemoved : FeedEvent

    data object BookmarkFailed : FeedEvent

    data object NoteDeleted : FeedEvent

    data object DeleteFailed : FeedEvent

    data class Redraft(val note: Note) : FeedEvent

    data object PollVoteFailed : FeedEvent

    data object PollEnded : FeedEvent

    data object RateLimited : FeedEvent

    data object SignInRequired : FeedEvent

    data object NoteUnavailable : FeedEvent

    data class ScrollToTop(val kind: TimelineKind) : FeedEvent
}

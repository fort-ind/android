package com.fortind.zenith.ui.feed

import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteCollection
import com.fortind.zenith.timeline.NoteListKey
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
    val cursor: String? = null,
    val softMuted: Map<String, List<String>> = emptyMap(),
    val headTrimmed: Boolean = false,
    val newerCursor: String? = null,
    val loadingNewer: Boolean = false,
    val loadNewerFailed: Boolean = false,
) {
    val hasPending: Boolean get() = pending.isNotEmpty() || gap
}

data class ThreadPerson(val pagingId: String, val user: NoteUser, val reaction: String? = null)

data class ThreadPeople(
    val status: TimelineStatus = TimelineStatus.Idle,
    val rows: List<ThreadPerson> = emptyList(),
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
)

data class ReplyBranch(
    val children: List<Note> = emptyList(),
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
    val moreFailed: Boolean = false,
    val end: Boolean = false,
)

data class ThreadState(
    val noteId: String,
    val ancestors: List<Note> = emptyList(),
    val note: Note? = null,
    val replies: List<Note> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val repliesLoadingMore: Boolean = false,
    val repliesEnd: Boolean = false,
    val repliesLoadMoreFailed: Boolean = false,
    val branches: Map<String, ReplyBranch> = emptyMap(),
    val renotes: ThreadPeople = ThreadPeople(),
    val reactions: Map<String?, ThreadPeople> = emptyMap(),
) {
    val notes: List<Note> get() = ancestors + listOfNotNull(note) + replies + branches.values.flatMap { it.children }

    fun withReply(reply: Note, parentId: String): ThreadState = when {
        parentId == noteId -> copy(replies = mergeNewer(replies, listOf(reply)))
        parentId in branches -> copy(
            branches = branches + (parentId to branches.getValue(parentId).let { it.copy(children = mergeNewer(it.children, listOf(reply)), expanded = true) }),
        )
        else -> this
    }

    fun mapNotes(transform: (List<Note>) -> List<Note>): ThreadState {
        val nextNote = note?.let { transform(listOf(it)).firstOrNull() ?: it.copy(deleted = true) }
        return copy(
            ancestors = transform(ancestors),
            note = nextNote,
            replies = transform(replies),
            branches = branches.mapValues { (_, branch) -> branch.copy(children = transform(branch.children)) },
        )
    }
}

data class FeedUiState(
    val authResolved: Boolean = false,
    val signedIn: Boolean = false,
    val myUserId: String? = null,
    val selected: TimelineKind = TimelineKind.Home,
    val collection: NoteCollection? = null,
    val lists: Map<NoteListKey, TimelineState> = TimelineKind.entries.associate { NoteListKey.Timeline(it) to TimelineState() },
    val thread: ThreadState? = null,
    val renoted: Set<String> = emptySet(),
    val renoteChecks: Set<String> = emptySet(),
    val needsPermission: Boolean = false,
    val bookmarked: Set<String> = emptySet(),
    val bookmarkChecks: Set<String> = emptySet(),
    val threadMuted: Set<String> = emptySet(),
    val pinned: Set<String> = emptySet(),
) {
    fun list(key: NoteListKey): TimelineState = lists[key] ?: TimelineState()

    fun timeline(kind: TimelineKind): TimelineState = list(NoteListKey.Timeline(kind))

    val bookmarks: TimelineState get() = list(NoteListKey.Bookmarks)

    val current: TimelineState get() = timeline(selected)

    fun findNote(noteId: String): Note? =
        lists.values.firstNotNullOfOrNull { (it.items + it.pending).findNote(noteId) }
            ?: thread?.notes?.findNote(noteId)
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

    data class UserMuted(val userId: String, val name: String) : FeedEvent

    data class RenotesHidden(val userId: String, val name: String) : FeedEvent

    data class UserBlocked(val userId: String, val name: String) : FeedEvent

    data object ModerationFailed : FeedEvent

    data object PollVoteFailed : FeedEvent

    data object PollEnded : FeedEvent

    data object RateLimited : FeedEvent

    data object SignInRequired : FeedEvent

    data object NoteUnavailable : FeedEvent

    data object ThreadMuted : FeedEvent

    data object ThreadUnmuted : FeedEvent

    data object ThreadMuteFailed : FeedEvent

    data object NotePinned : FeedEvent

    data object NoteUnpinned : FeedEvent

    data object PinLimitReached : FeedEvent

    data object PinFailed : FeedEvent

    data class ScrollToTop(val kind: TimelineKind) : FeedEvent
}

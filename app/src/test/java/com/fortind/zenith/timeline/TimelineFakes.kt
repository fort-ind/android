package com.fortind.zenith.timeline

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.mfm.MfmParser
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

fun testUser(id: String = "u1", username: String = id, host: String? = null) = NoteUser(
    id = id,
    username = username,
    host = host,
    name = null,
    nameMfm = emptyList(),
    avatarUrl = null,
    avatarBlurhash = null,
    emojis = emptyMap(),
    isBot = false,
    isCat = false,
    instance = null,
)

fun testNote(
    id: String,
    createdAt: Long = id.filter(Char::isDigit).toLongOrNull() ?: 0L,
    user: NoteUser = testUser(),
    text: String? = "note $id",
    renote: Note? = null,
    reply: Note? = null,
    reactions: Map<String, Int> = emptyMap(),
    myReaction: String? = null,
    renoteCount: Int = 0,
    visibility: Visibility = Visibility.Public,
    reactionAcceptance: ReactionAcceptance = ReactionAcceptance.All,
    poll: Poll? = null,
    repliesCount: Int = 0,
) = Note(
    id = id,
    createdAtMillis = createdAt,
    editedAtMillis = null,
    user = user,
    text = text,
    textMfm = text?.let(MfmParser::parse).orEmpty(),
    cw = null,
    cwMfm = null,
    visibility = visibility,
    localOnly = false,
    renote = renote,
    reply = reply,
    files = emptyList(),
    poll = poll,
    emojis = emptyMap(),
    reactions = reactions,
    reactionEmojis = emptyMap(),
    renoteCount = renoteCount,
    repliesCount = repliesCount,
    myReaction = myReaction,
    reactionAcceptance = reactionAcceptance,
    remoteUrl = null,
    isHidden = false,
)

class TimelineCall(val token: String?, val kind: TimelineKind, val untilId: String?, val sinceId: String?, val limit: Int)

class FakeNotesApi : NotesApi {
    val timelines = mutableMapOf<TimelineKind, List<Note>>()
    val failures = mutableMapOf<TimelineKind, IOException>()
    val calls = CopyOnWriteArrayList<TimelineCall>()
    val notes = mutableMapOf<String, Note>()
    var ancestors: List<Note> = emptyList()
    var replies: List<Note> = emptyList()
    val branchReplies = mutableMapOf<String, List<Note>>()
    var branchFailure: IOException? = null
    var moreRepliesFailure: IOException? = null
    var reactFailure: IOException? = null
    var unreactFailure: IOException? = null
    var renoteFailure: IOException? = null
    var renotedByMe = false
    var renotedByMeFailure: IOException? = null
    val renotedByMeCalls = CopyOnWriteArrayList<String>()
    val reactCalls = CopyOnWriteArrayList<Pair<String, String>>()
    val unreactCalls = CopyOnWriteArrayList<String>()
    val renoteCalls = CopyOnWriteArrayList<String>()
    val unrenoteCalls = CopyOnWriteArrayList<String>()
    val createCalls = CopyOnWriteArrayList<NewNote>()
    val editCalls = CopyOnWriteArrayList<Pair<String, NewNote>>()
    val deleteCalls = CopyOnWriteArrayList<String>()
    var created: Note? = null
    var createFailure: IOException? = null
    var editFailure: IOException? = null
    var deleteFailure: IOException? = null
    val voteCalls = CopyOnWriteArrayList<Pair<String, Int>>()
    val voteFailures = mutableMapOf<Int, IOException>()
    val bookmarkedIds = mutableSetOf<String>()
    val bookmarkCalls = CopyOnWriteArrayList<String>()
    val unbookmarkCalls = CopyOnWriteArrayList<String>()
    var bookmarkFailure: IOException? = null
    var bookmarkEntries: List<Bookmark> = emptyList()
    val bookmarkPageCalls = CopyOnWriteArrayList<String?>()
    var bookmarkPageFailure: IOException? = null

    override suspend fun timeline(token: String?, kind: TimelineKind, untilId: String?, sinceId: String?, limit: Int): List<Note> {
        calls += TimelineCall(token, kind, untilId, sinceId, limit)
        failures[kind]?.let { throw it }
        val all = timelines[kind].orEmpty()
        val older = if (untilId == null) all else all.dropWhile { it.id != untilId }.drop(1)
        val bounded = if (sinceId == null) older else older.takeWhile { it.id != sinceId }
        return if (sinceId != null && untilId == null) bounded.takeLast(limit) else bounded.take(limit)
    }

    override suspend fun show(token: String?, noteId: String): Note =
        notes[noteId] ?: timelines.values.flatten().firstOrNull { it.id == noteId } ?: throw IOException("missing")

    override suspend fun conversation(token: String?, noteId: String, limit: Int): List<Note> = ancestors

    override suspend fun children(token: String?, noteId: String, untilId: String?, limit: Int): List<Note> {
        branchReplies[noteId]?.let { branch ->
            branchFailure?.let { throw it }
            val older = if (untilId == null) branch else branch.dropWhile { it.id != untilId }.drop(1)
            return older.take(limit)
        }
        if (untilId != null) moreRepliesFailure?.let { throw it }
        val older = if (untilId == null) replies else replies.dropWhile { it.id != untilId }.drop(1)
        return older.take(limit)
    }

    override suspend fun renotedByMe(token: String, noteId: String, userId: String): Boolean {
        renotedByMeCalls += noteId
        renotedByMeFailure?.let { throw it }
        return renotedByMe
    }

    override suspend fun react(token: String, noteId: String, reaction: String) {
        reactCalls += noteId to reaction
        reactFailure?.let {
            reactFailure = null
            throw it
        }
    }

    override suspend fun unreact(token: String, noteId: String) {
        unreactCalls += noteId
        unreactFailure?.let { throw it }
    }

    override suspend fun renote(token: String, noteId: String, visibility: Visibility, localOnly: Boolean) {
        renoteCalls += noteId
        renoteVisibility = visibility
        renoteFailure?.let { throw it }
    }

    override suspend fun unrenote(token: String, noteId: String) {
        unrenoteCalls += noteId
    }

    override suspend fun create(token: String, note: NewNote): Note {
        createCalls += note
        createFailure?.let { throw it }
        return created ?: testNote(id = "c${createCalls.size}", text = note.text, reply = note.replyId?.let { notes[it] })
    }

    override suspend fun edit(token: String, noteId: String, note: NewNote): Note {
        editCalls += noteId to note
        editFailure?.let { throw it }
        return notes[noteId]?.copy(text = note.text) ?: testNote(id = noteId, text = note.text)
    }

    override suspend fun delete(token: String, noteId: String) {
        deleteCalls += noteId
        deleteFailure?.let { throw it }
    }

    override suspend fun vote(token: String, noteId: String, choice: Int) {
        voteCalls += noteId to choice
        voteFailures[choice]?.let { throw it }
    }

    val threadMutedIds = mutableSetOf<String>()
    val pinCalls = CopyOnWriteArrayList<String>()
    var pinFailure: IOException? = null
    var renoteVisibility: Visibility? = null
    var muteThreadFailure: IOException? = null
    var renoteRows: List<Note> = emptyList()
    var reactionRows: List<NoteReactionRow> = emptyList()
    val reactionQueries = CopyOnWriteArrayList<String?>()

    override suspend fun noteFlags(token: String, noteId: String) = NoteFlags(noteId in bookmarkedIds, noteId in threadMutedIds)

    override suspend fun muteThread(token: String, noteId: String) {
        muteThreadFailure?.let { throw it }
        threadMutedIds += noteId
    }

    override suspend fun unmuteThread(token: String, noteId: String) {
        muteThreadFailure?.let { throw it }
        threadMutedIds -= noteId
    }

    override suspend fun pin(token: String, noteId: String): CurrentUser {
        pinCalls += "pin:$noteId"
        pinFailure?.let { throw it }
        return CurrentUser("me", "me", null, null, null, pinnedNoteIds = listOf(noteId))
    }

    override suspend fun unpin(token: String, noteId: String): CurrentUser {
        pinCalls += "unpin:$noteId"
        pinFailure?.let { throw it }
        return CurrentUser("me", "me", null, null, null)
    }

    override suspend fun renotesOf(token: String?, noteId: String, untilId: String?, limit: Int): List<Note> =
        if (untilId == null) renoteRows else emptyList()

    override suspend fun reactionsOn(token: String?, noteId: String, type: String?, untilId: String?, limit: Int): List<NoteReactionRow> {
        reactionQueries += type
        return if (untilId == null) reactionRows.filter { type == null || it.reaction == type } else emptyList()
    }

    override suspend fun bookmark(token: String, noteId: String) {
        bookmarkCalls += noteId
        bookmarkFailure?.let { throw it }
        bookmarkedIds += noteId
    }

    override suspend fun unbookmark(token: String, noteId: String) {
        unbookmarkCalls += noteId
        bookmarkFailure?.let { throw it }
        bookmarkedIds -= noteId
    }

    override suspend fun bookmarks(token: String, untilId: String?, limit: Int): List<Bookmark> {
        bookmarkPageCalls += untilId
        bookmarkPageFailure?.let { throw it }
        val older = if (untilId == null) bookmarkEntries else bookmarkEntries.dropWhile { it.id != untilId }.drop(1)
        return older.take(limit)
    }

    val userNotePages = mutableMapOf<Pair<String, UserNotesTab>, List<Note>>()

    val collectionPages = mutableMapOf<NoteCollection, List<Note>>()
    val collectionCalls = CopyOnWriteArrayList<NoteCollection>()
    val searchCalls = CopyOnWriteArrayList<NoteSearch>()
    val tagCalls = CopyOnWriteArrayList<Pair<String?, String>>()
    var searchResults: List<Note> = emptyList()

    override suspend fun collectionNotes(token: String, collection: NoteCollection, untilId: String?, sinceId: String?, limit: Int): List<Note> {
        collectionCalls += collection
        val all = collectionPages[collection].orEmpty()
        val older = if (untilId == null) all else all.dropWhile { it.id != untilId }.drop(1)
        return older.take(limit)
    }

    override suspend fun searchNotes(token: String, search: NoteSearch, untilId: String?, limit: Int): List<Note> {
        searchCalls += search
        val older = if (untilId == null) searchResults else searchResults.dropWhile { it.id != untilId }.drop(1)
        return older.take(limit)
    }

    override suspend fun notesByTag(token: String?, tag: String, untilId: String?, limit: Int): List<Note> {
        tagCalls += token to tag
        val older = if (untilId == null) searchResults else searchResults.dropWhile { it.id != untilId }.drop(1)
        return older.take(limit)
    }

    override suspend fun userNotes(token: String?, userId: String, tab: UserNotesTab, untilId: String?, limit: Int): List<Note> {
        val all = userNotePages[userId to tab].orEmpty()
        val older = if (untilId == null) all else all.dropWhile { it.id != untilId }.drop(1)
        return older.take(limit)
    }
}

class FakeTimelineStream : TimelineStream {
    val events = MutableSharedFlow<TimelineStreamEvent>(extraBufferCapacity = 16)
    val updates = MutableSharedFlow<NoteUpdate>(extraBufferCapacity = 16)
    val subscriptions = CopyOnWriteArrayList<Pair<String?, TimelineKind>>()
    val captured = MutableStateFlow<Set<String>>(emptySet())

    override fun events(token: String?, kind: TimelineKind): Flow<TimelineStreamEvent> {
        subscriptions += token to kind
        return events
    }

    val collectionSubscriptions = CopyOnWriteArrayList<NoteCollection>()
    val collectionStream = MutableSharedFlow<TimelineStreamEvent>(extraBufferCapacity = 16)

    override fun collectionEvents(token: String, collection: NoteCollection): Flow<TimelineStreamEvent> {
        collectionSubscriptions += collection
        return collectionStream
    }

    override fun updates(token: String?, captured: Flow<Set<String>>): Flow<NoteUpdate> = channelFlow {
        launch { captured.collect { this@FakeTimelineStream.captured.value = it } }
        updates.collect { send(it) }
    }
}

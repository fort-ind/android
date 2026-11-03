package com.fortind.zenith.timeline

enum class UserNotesTab { Notes, NotesAndReplies, Media }

sealed interface NoteListKey {
    val isPrimary: Boolean

    val ownerId: String? get() = null

    data class Timeline(val kind: TimelineKind) : NoteListKey {
        override val isPrimary: Boolean = true
    }

    data object Bookmarks : NoteListKey {
        override val isPrimary: Boolean = false
    }

    data class UserNotes(val userId: String, val tab: UserNotesTab) : NoteListKey {
        override val isPrimary: Boolean = false
        override val ownerId: String = userId
    }

    data class Pinned(val userId: String) : NoteListKey {
        override val isPrimary: Boolean = false
        override val ownerId: String = userId
    }
}

class NotePage(val notes: List<Note>, val nextCursor: String?)

interface NoteSource {
    suspend fun page(token: String?, key: NoteListKey, cursor: String?, limit: Int): NotePage
}

class NotesApiSource(private val api: NotesApi) : NoteSource {
    override suspend fun page(token: String?, key: NoteListKey, cursor: String?, limit: Int): NotePage = when (key) {
        is NoteListKey.Timeline -> api.timeline(token, key.kind, untilId = cursor, sinceId = null, limit = limit).let { NotePage(it, it.lastOrNull()?.id) }
        NoteListKey.Bookmarks -> api.bookmarks(requireNotNull(token), cursor, limit).let { page ->
            NotePage(page.map(Bookmark::note), page.lastOrNull()?.id)
        }
        is NoteListKey.UserNotes -> api.userNotes(token, key.userId, key.tab, cursor, limit).let { NotePage(it, it.lastOrNull()?.id) }
        is NoteListKey.Pinned -> NotePage(emptyList(), null)
    }
}

fun <K, V> evictSecondary(lists: Map<K, V>, recentlyUsed: List<K>, keep: Int, isPrimary: (K) -> Boolean): Map<K, V> {
    val secondary = lists.keys.filterNot(isPrimary)
    if (secondary.size <= keep) return lists
    val kept = recentlyUsed.filter { it in secondary }.takeLast(keep).toSet()
    return lists.filterKeys { isPrimary(it) || it in kept }
}

package com.fortind.zenith.timeline

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteListKeyTest {

    private val home = NoteListKey.Timeline(TimelineKind.Home)

    private fun String.isPrimary() = startsWith("primary")

    @Test
    fun aBookmarkPageCursorIsTheFavoriteEntryNotTheNote() = runTest {
        val api = FakeNotesApi()
        api.bookmarkEntries = listOf(Bookmark("f1", testNote("n1")), Bookmark("f2", testNote("n2")))

        val page = NotesApiSource(api).page("t", NoteListKey.Bookmarks, cursor = null, limit = 30)

        assertEquals("f2", page.nextCursor)
        assertEquals(listOf("n1", "n2"), page.notes.map { it.id })
    }

    @Test
    fun aTimelinePageCursorIsTheLastNote() = runTest {
        val api = FakeNotesApi()
        api.timelines[TimelineKind.Home] = listOf(testNote("n2"), testNote("n1"))

        assertEquals("n1", NotesApiSource(api).page("t", home, cursor = null, limit = 30).nextCursor)
    }

    @Test
    fun timelinesArePrimaryAndBookmarksAreNot() {
        assertEquals(true, home.isPrimary)
        assertEquals(false, NoteListKey.Bookmarks.isPrimary)
    }

    @Test
    fun primaryListsAreNeverEvicted() {
        val lists = mapOf("primary" to 0, "a" to 1, "b" to 2)

        val kept = evictSecondary(lists, listOf("a", "b"), keep = 1) { it.isPrimary() }

        assertEquals(setOf("primary", "b"), kept.keys)
    }

    @Test
    fun theLeastRecentlyUsedSecondaryListGoesFirst() {
        val lists = mapOf("a" to 1, "b" to 2, "c" to 3)

        val kept = evictSecondary(lists, listOf("c", "a", "b"), keep = 2) { it.isPrimary() }

        assertEquals(setOf("a", "b"), kept.keys)
    }

    @Test
    fun nothingIsEvictedUnderTheLimit() {
        val lists = mapOf("a" to 1)

        assertEquals(lists, evictSecondary(lists, emptyList(), keep = 8) { it.isPrimary() })
    }
}

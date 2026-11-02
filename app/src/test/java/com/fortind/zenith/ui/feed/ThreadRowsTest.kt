package com.fortind.zenith.ui.feed

import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.testNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadRowsTest {
    private fun thread(replies: List<Note>, branches: Map<String, ReplyBranch> = emptyMap()) =
        ThreadState(noteId = "root", note = testNote("root"), replies = replies, loading = false, branches = branches)

    private fun loaded(vararg children: Note, end: Boolean = true) =
        ReplyBranch(children = children.toList(), expanded = true, end = end)

    @Test
    fun aReplyWithoutChildrenIsFollowedOnlyByADivider() {
        val rows = thread(listOf(testNote("n1"))).replyRows()

        assertEquals(listOf("reply:n1", "divider:n1"), rows.map { it.key })
    }

    @Test
    fun aReplyWithUnfetchedChildrenGetsAnAutoLoadingRow() {
        val rows = thread(listOf(testNote("n1", repliesCount = 3))).replyRows()

        val toggle = rows[1] as ReplyRow.Toggle
        assertEquals(3, toggle.count)
        assertTrue(toggle.auto)
        assertTrue(toggle.loading)
        assertEquals(1, toggle.depth)
    }

    @Test
    fun aLoadedBranchNestsItsChildrenWithoutAToggleRow() {
        val rows = thread(listOf(testNote("n1", repliesCount = 1)), mapOf("n1" to loaded(testNote("n2")))).replyRows()

        assertEquals(listOf("reply:n1", "reply:n2", "divider:n1"), rows.map { it.key })
        assertEquals(1, (rows[1] as ReplyRow.Item).depth)
    }

    @Test
    fun aBranchLoadingItsFirstPageShowsAnInFlightRowThatDoesNotRetrigger() {
        val branch = ReplyBranch(expanded = true, loading = true)
        val toggle = thread(listOf(testNote("n1", repliesCount = 2)), mapOf("n1" to branch)).replyRows()[1] as ReplyRow.Toggle

        assertTrue(toggle.loading)
        assertFalse(toggle.auto)
    }

    @Test
    fun aFailedBranchOffersARetryRow() {
        val branch = ReplyBranch(expanded = true, failed = true)
        val toggle = thread(listOf(testNote("n1", repliesCount = 2)), mapOf("n1" to branch)).replyRows()[1] as ReplyRow.Toggle

        assertTrue(toggle.failed)
        assertFalse(toggle.loading)
        assertFalse(toggle.auto)
    }

    @Test
    fun aCollapsedBranchOffersToShowItsReplies() {
        val branch = ReplyBranch(children = listOf(testNote("n2")), expanded = false, end = true)
        val rows = thread(listOf(testNote("n1", repliesCount = 1)), mapOf("n1" to branch)).replyRows()

        val toggle = rows[1] as ReplyRow.Toggle
        assertFalse(toggle.loading)
        assertFalse(toggle.auto)
        assertTrue(rows.none { it is ReplyRow.Item && it.note.id == "n2" })
    }

    @Test
    fun anUnfinishedBranchEndsWithAMoreRow() {
        val rows = thread(
            listOf(testNote("n1", repliesCount = 40)),
            mapOf("n1" to loaded(testNote("n2"), end = false)),
        ).replyRows()

        val more = rows.filterIsInstance<ReplyRow.More>().single()
        assertEquals("n1", more.parentId)
        assertFalse(more.failed)
    }

    @Test
    fun aFailedNextPageMarksTheMoreRowFailed() {
        val branch = loaded(testNote("n2"), end = false).copy(moreFailed = true)
        val rows = thread(listOf(testNote("n1", repliesCount = 40)), mapOf("n1" to branch)).replyRows()

        assertTrue(rows.filterIsInstance<ReplyRow.More>().single().failed)
    }

    @Test
    fun nestingStopsAtTheDepthCapWithAContinueRow() {
        val branches = mapOf(
            "n1" to loaded(testNote("n2", repliesCount = 1)),
            "n2" to loaded(testNote("n3", repliesCount = 1)),
            "n3" to loaded(testNote("n4", repliesCount = 1)),
            "n4" to loaded(testNote("n5", repliesCount = 2)),
        )
        val rows = thread(listOf(testNote("n1", repliesCount = 1)), branches).replyRows()

        val continueRow = rows.filterIsInstance<ReplyRow.Continue>().single()
        assertEquals("n5", continueRow.note.id)
        assertEquals(MAX_REPLY_DEPTH, (rows.first { it.key == "reply:n5" } as ReplyRow.Item).depth)
        assertTrue(rows.none { it is ReplyRow.Toggle && it.parentId == "n5" })
    }

    @Test
    fun deletedRepliesOfferNoBranch() {
        val rows = thread(listOf(testNote("n1", repliesCount = 2).copy(deleted = true))).replyRows()

        assertTrue(rows.none { it is ReplyRow.Toggle })
    }
}

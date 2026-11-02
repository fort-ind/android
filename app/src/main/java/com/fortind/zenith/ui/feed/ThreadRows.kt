package com.fortind.zenith.ui.feed

import com.fortind.zenith.timeline.Note

const val MAX_REPLY_DEPTH = 4

sealed interface ReplyRow {
    val key: String
    val depth: Int

    data class Item(val note: Note, override val depth: Int) : ReplyRow {
        override val key: String get() = "reply:${note.id}"
    }

    data class Toggle(
        val parentId: String,
        val count: Int,
        val auto: Boolean,
        val loading: Boolean,
        val failed: Boolean,
        override val depth: Int,
    ) : ReplyRow {
        override val key: String get() = "toggle:$parentId"
    }

    data class More(val parentId: String, val loading: Boolean, val failed: Boolean, override val depth: Int) : ReplyRow {
        override val key: String get() = "more:$parentId"
    }

    data class Continue(val note: Note, override val depth: Int) : ReplyRow {
        override val key: String get() = "continue:${note.id}"
    }

    data class Divider(val afterId: String) : ReplyRow {
        override val key: String get() = "divider:$afterId"
        override val depth: Int get() = 0
    }
}

fun ThreadState.replyRows(): List<ReplyRow> {
    val rows = ArrayList<ReplyRow>()
    fun walk(notes: List<Note>, depth: Int) {
        notes.forEach { note ->
            rows += ReplyRow.Item(note, depth)
            if (note.deleted || note.repliesCount <= 0) return@forEach
            val branch = branches[note.id]
            when {
                depth >= MAX_REPLY_DEPTH -> rows += ReplyRow.Continue(note, depth + 1)
                else -> {
                    val loadingFirst = branch == null || (branch.loading && branch.children.isEmpty())
                    val collapsed = branch != null && !branch.expanded
                    val failed = branch?.failed == true
                    if (loadingFirst || collapsed || failed) {
                        rows += ReplyRow.Toggle(
                            parentId = note.id,
                            count = note.repliesCount,
                            auto = branch == null,
                            loading = loadingFirst,
                            failed = failed,
                            depth = depth + 1,
                        )
                    }
                    if (branch != null && branch.expanded) {
                        walk(branch.children, depth + 1)
                        if (!branch.end && branch.children.isNotEmpty()) {
                            rows += ReplyRow.More(note.id, branch.loadingMore, branch.moreFailed, depth + 1)
                        }
                    }
                }
            }
        }
    }
    replies.forEach { reply ->
        walk(listOf(reply), 0)
        rows += ReplyRow.Divider(reply.id)
    }
    return rows
}

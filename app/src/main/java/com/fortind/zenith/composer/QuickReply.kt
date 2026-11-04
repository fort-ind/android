package com.fortind.zenith.composer

import com.fortind.zenith.account.composerContentWarning
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.timeline.NewNote
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.Visibility

fun failedReplySlot(noteId: String): String = "failed-reply:$noteId"

fun replyText(parent: Note, typed: String, myUsername: String?): String {
    val firstWord = typed.trimStart().split(' ', '\n', limit = 2).first()
    return if (firstWord.equals(parent.user.handle, ignoreCase = true)) typed else replyPrefix(parent, myUsername) + typed
}

fun quickReplyNote(parent: Note, typed: String, me: CurrentUser): NewNote {
    val direct = parent.visibility == Visibility.Specified
    return NewNote(
        text = replyText(parent, typed, me.username),
        cw = composerContentWarning(parent.cw, me.settings.defaultCw, me.settings.defaultCwPriority),
        visibility = parent.visibility,
        localOnly = parent.localOnly && !direct,
        replyId = parent.id,
        visibleUserIds = if (direct) (parent.visibleUserIds + parent.user.id).filter { it != me.id }.distinct() else emptyList(),
    )
}

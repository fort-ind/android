package com.fortind.zenith.notifications

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.composer.ComposerDraft
import com.fortind.zenith.composer.DraftStore
import com.fortind.zenith.composer.draftStorageKey
import com.fortind.zenith.composer.failedReplySlot
import com.fortind.zenith.composer.quickReplyNote
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.timeline.NotesApi
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class QuickAction { Reply, Like }

enum class QuickOutcome { Done, Retry }

const val SCOPE_WRITE_NOTES = "write:notes"
const val SCOPE_WRITE_REACTIONS = "write:reactions"

class NotificationQuickActions(
    private val session: UserSessionService,
    private val notes: NotesApi,
    private val drafts: DraftStore,
    private val notifier: SystemNotifier,
    private val defaultLike: () -> String,
) {
    suspend fun reply(noteId: String, typed: String) {
        val token = session.token()
        if (token == null) {
            notifier.showActionFailed(QuickAction.Reply, noteId)
            return
        }
        val user = currentUser()
        try {
            val parent = notes.show(token, noteId)
            notes.create(token, quickReplyNote(parent, typed, user ?: throw IOException("Profile unavailable")))
        } catch (_: TokenRejectedException) {
            session.signOut()
            notifier.showActionFailed(QuickAction.Reply, noteId)
        } catch (_: IOException) {
            keepFailedReply(user, noteId, typed)
            notifier.showActionFailed(QuickAction.Reply, noteId)
        }
    }

    suspend fun replyTooLong(noteId: String, typed: String) {
        keepFailedReply(currentUser(), noteId, typed)
        notifier.showActionFailed(QuickAction.Reply, noteId)
    }

    suspend fun like(noteId: String): QuickOutcome {
        val token = session.token()
        if (token == null) {
            notifier.showActionFailed(QuickAction.Like, noteId)
            return QuickOutcome.Done
        }
        return try {
            notes.react(token, noteId, defaultLike())
            QuickOutcome.Done
        } catch (_: TokenRejectedException) {
            session.signOut()
            notifier.showActionFailed(QuickAction.Like, noteId)
            QuickOutcome.Done
        } catch (e: HttpStatusException) {
            when {
                e.apiErrorCode == ApiErrorCodes.ALREADY_REACTED -> QuickOutcome.Done
                e.code in FINAL_REFUSALS -> {
                    notifier.showActionFailed(QuickAction.Like, noteId)
                    QuickOutcome.Done
                }
                else -> QuickOutcome.Retry
            }
        } catch (_: IOException) {
            QuickOutcome.Retry
        }
    }

    fun likeGaveUp(noteId: String) = notifier.showActionFailed(QuickAction.Like, noteId)

    private suspend fun keepFailedReply(user: CurrentUser?, noteId: String, typed: String) {
        drafts.save(draftStorageKey(user?.id, failedReplySlot(noteId)), ComposerDraft(text = typed))
    }

    private suspend fun currentUser(): CurrentUser? {
        withTimeoutOrNull(SESSION_WAIT_MS) { session.state.first { it !is SessionState.Restoring } }
        val state = session.state.value as? SessionState.SignedIn ?: return null
        if (state.user != null) return state.user
        session.refreshUser()
        return (session.state.value as? SessionState.SignedIn)?.user
    }

    private companion object {
        const val SESSION_WAIT_MS = 35_000L
        val FINAL_REFUSALS = setOf(400, 403, 404, 422)
    }
}

package com.fortind.zenith.notifications

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemNotifierTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidSystemNotifier(context)
    private val holdMillis = InstrumentationRegistry.getArguments().getString("holdMillis")?.toLong() ?: 0L
    private val runId = SystemClock.elapsedRealtimeNanos()

    @Before
    fun start() {
        assumeTrue("run: adb shell pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS", manager.areNotificationsEnabled())
        manager.cancelAll()
    }

    @After
    fun clean() {
        Thread.sleep(holdMillis)
        manager.cancelAll()
    }

    private fun notification(id: String, type: NotificationType, isDirect: Boolean = false, hasMyReaction: Boolean = false, noteId: String? = "note-$id") =
        FortNotification(
            id = id,
            type = type,
            createdAtMillis = System.currentTimeMillis(),
            actor = NotificationActor("actor-$id", "someone", null, null, null),
            noteId = noteId,
            notePreview = "secret words",
            reaction = null,
            reactionEmojiUrl = null,
            title = null,
            body = null,
            isDirect = isDirect,
            hasMyReaction = hasMyReaction,
        )

    private fun post(item: FortNotification, actions: Set<QuickAction> = QuickAction.entries.toSet()): Notification {
        val unique = item.copy(id = "$runId-${item.id}")
        runBlocking { notifier.show(listOf(unique), actions) }
        return awaitActive("notification ${unique.id}") { active -> active.firstOrNull { it.id == unique.id.hashCode() }?.notification }
    }

    private fun <T : Any> awaitActive(what: String, read: (List<StatusBarNotification>) -> T?): T {
        val deadline = SystemClock.elapsedRealtime() + POST_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            read(manager.activeNotifications.toList())?.let { return it }
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("$what never appeared")
    }

    private fun Notification.titles(): List<String> = actions.orEmpty().map { it.title.toString() }

    private val reply get() = context.getString(R.string.notification_action_reply)
    private val like get() = context.getString(R.string.notification_action_like)
    private val markRead get() = context.getString(R.string.notification_action_mark_read)

    @Test
    fun aMentionGetsReplyAndLikeButNotMarkRead() {
        val shown = post(notification("1", NotificationType.Mention))

        assertEquals(listOf(reply, like), shown.titles())
    }

    @Test
    fun onlyReplyTakesText() {
        val shown = post(notification("1", NotificationType.Reply))
        val (replyAction, likeAction) = shown.actions.toList()

        assertEquals(NotificationActionReceiver.KEY_REPLY_TEXT, replyAction.remoteInputs?.single()?.resultKey)
        assertNull(likeAction.remoteInputs)
    }

    @Test
    fun onlyTheReplyIntentIsMutable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val shown = post(notification("1", NotificationType.Mention))
        val (replyAction, likeAction) = shown.actions.toList()

        assertFalse(replyAction.actionIntent.isImmutable)
        assertTrue(likeAction.actionIntent.isImmutable)
        assertTrue(replyAction.actionIntent.isBroadcast)
    }

    @Test
    fun yourOwnReactionHidesLike() {
        assertEquals(listOf(reply), post(notification("1", NotificationType.Mention, hasMyReaction = true)).titles())
    }

    @Test
    fun missingScopesFallBackToMarkRead() {
        assertEquals(listOf(markRead), post(notification("1", NotificationType.Mention), actions = emptySet()).titles())
        assertEquals(listOf(like), post(notification("2", NotificationType.Mention), actions = setOf(QuickAction.Like)).titles())
    }

    @Test
    fun otherTypesKeepTheirActions() {
        assertEquals(listOf(markRead), post(notification("1", NotificationType.Renote)).titles())
        val accept = context.getString(R.string.notifications_accept)
        val reject = context.getString(R.string.notifications_reject)
        assertEquals(listOf(accept, reject), post(notification("2", NotificationType.FollowRequest, noteId = null)).titles())
    }

    @Test
    fun aDirectNoteKeepsItsButtonsButHidesItsText() {
        val shown = post(notification("1", NotificationType.Mention, isDirect = true))

        assertEquals(listOf(reply, like), shown.titles())
        assertEquals(context.getString(R.string.notifications_direct_body), shown.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(shown.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("secret"))
    }

    @Test
    fun aFailedReplySurvivesClearAll() {
        post(notification("1", NotificationType.Mention))
        notifier.showActionFailed(QuickAction.Reply, "note-1")

        val failedTitle = context.getString(R.string.notification_reply_failed_title)
        val title = { item: StatusBarNotification -> item.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() }
        awaitActive("the failed notification") { active -> active.firstOrNull { title(it) == failedTitle } }

        notifier.clearAll()

        awaitActive("only the failed notification") { active -> active.singleOrNull()?.takeIf { title(it) == failedTitle } }
    }

    private companion object {
        const val POST_WAIT_MS = 5_000L
        const val POLL_MS = 50L
    }
}

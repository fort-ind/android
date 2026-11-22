package com.fortind.zenith.notifications

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.composer.draftStorageKey
import com.fortind.zenith.composer.failedReplySlot
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockScreenHoldDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = (context.applicationContext as ZenithApplication).container
    private val work = WorkManager.getInstance(context)
    private val knownWork = File(context.cacheDir, "lock-hold-known-work.txt")
    private val expectedReply = InstrumentationRegistry.getArguments().getString("typed")

    @Test
    fun postAFakeMention() {
        assertTrue(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        knownWork.writeText(actionWorkIds().joinToString("\n"))
        val mention = FortNotification(
            id = NOTIFICATION_ID,
            type = NotificationType.Mention,
            createdAtMillis = System.currentTimeMillis(),
            actor = NotificationActor("locktest-actor", "Lock test", null, null, null),
            noteId = FAKE_NOTE_ID,
            notePreview = "lock screen hold test",
            reaction = null,
            reactionEmojiUrl = null,
            title = null,
            body = null,
            isDirect = false,
            hasMyReaction = false,
        )
        runBlocking { AndroidSystemNotifier(context).show(listOf(mention), setOf(QuickAction.Reply, QuickAction.Like)) }
    }

    @Test
    fun nothingWasQueuedAndTheReplyWasKept() {
        val before = knownWork.readLines().filter { it.isNotBlank() }.toSet()
        val queued = actionWorkIds() - before
        assertEquals("action work queued while locked", emptySet<String>(), queued)
        if (expectedReply != null) {
            val draft = runBlocking { container.draftStore.load(draftStorageKey(accountId(), failedReplySlot(FAKE_NOTE_ID))) }
            assertEquals(expectedReply, draft?.text)
        }
    }

    @Test
    fun pressReplyAndLikeLikeSystemUiDoes() {
        val typed = requireNotNull(expectedReply) { "pass -e typed <text>" }
        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications.first { it.id == NOTIFICATION_ID.hashCode() }.notification
        val reply = posted.actions.first { it.remoteInputs?.isNotEmpty() == true }
        val like = posted.actions.first { it.remoteInputs.isNullOrEmpty() }
        val filled = Intent()
        val results = Bundle().apply { putCharSequence(NotificationActionReceiver.KEY_REPLY_TEXT, typed) }
        android.app.RemoteInput.addResultsToIntent(reply.remoteInputs, filled, results)
        reply.actionIntent.send(context, 0, filled)
        like.actionIntent.send()
        Thread.sleep(SETTLE_MS)
    }

    @Test
    fun deviceReportsLocked() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        println("isDeviceLocked=${keyguard.isDeviceLocked} isDeviceSecure=${keyguard.isDeviceSecure} ungated=${lockScreenUngated(android.os.Build.VERSION.SDK_INT)}")
    }

    @Test
    fun cleanUp() {
        runBlocking { container.draftStore.clear(draftStorageKey(accountId(), failedReplySlot(FAKE_NOTE_ID))) }
        context.getSystemService(NotificationManager::class.java).cancelAll()
        knownWork.delete()
    }

    private fun actionWorkIds(): Set<String> =
        work.getWorkInfosByTag(ACTION_TAG).get().map { it.id.toString() }.toSet()

    private fun accountId(): String? = runBlocking {
        withTimeoutOrNull(10_000) { container.sessionService.state.first { it != SessionState.Restoring } }
        container.sessionService.accountId()
    }

    private companion object {
        const val NOTIFICATION_ID = "locktest-notification"
        const val FAKE_NOTE_ID = "locktestnote0"
        const val ACTION_TAG = "notification-action"
        const val SETTLE_MS = 3_000L
    }
}

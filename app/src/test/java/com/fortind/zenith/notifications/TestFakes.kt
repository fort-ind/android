package com.fortind.zenith.notifications

import com.fortind.zenith.account.AccountSettings
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.TokenStore
import com.fortind.zenith.auth.UserApi
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

fun testNotification(
    id: String,
    type: NotificationType = NotificationType.Mention,
    createdAt: Long = 1_000L,
    avatarUrl: String? = null,
) = FortNotification(
    id = id,
    type = type,
    createdAtMillis = createdAt,
    actor = NotificationActor("actor-$id", "user$id", null, null, avatarUrl),
    noteId = "note-$id",
    notePreview = "text $id",
    reaction = null,
    reactionEmojiUrl = null,
    title = null,
    body = null,
)

suspend fun awaitTrue(condition: () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeout(10_000) {
            while (!condition()) delay(5)
        }
    }
}

class FakeTokenStore(@Volatile var token: String?) : TokenStore {
    override fun read(): String? = token

    override fun write(token: String) {
        this.token = token
    }

    override fun clear() {
        token = null
    }
}

class FakeUserApi(@Volatile var settings: AccountSettings = AccountSettings()) : UserApi {
    @Volatile var failure: IOException? = null
    val fetches = AtomicInteger()

    override suspend fun fetchCurrentUser(token: String): CurrentUser {
        fetches.incrementAndGet()
        failure?.let { throw it }
        return CurrentUser("me", "me", null, null, null, settings = settings)
    }
}

class FakeNotificationsApi : NotificationsApi {
    @Volatile var items: List<FortNotification> = emptyList()
    @Volatile var unread: Int = 0
    @Volatile var fetchFailure: IOException? = null
    @Volatile var markReadFailure: IOException? = null
    @Volatile var followFailure: IOException? = null
    val fetchTokens = CopyOnWriteArrayList<String>()
    val fetchTypes = CopyOnWriteArrayList<List<NotificationType>?>()
    val markAllReadTokens = CopyOnWriteArrayList<String>()
    val followResponses = CopyOnWriteArrayList<Pair<String, Boolean>>()

    override suspend fun fetch(
        token: String,
        types: List<NotificationType>?,
        untilId: String?,
        limit: Int,
    ): List<FortNotification> {
        fetchTokens += token
        fetchTypes += types
        fetchFailure?.let { throw it }
        val matching = items.filter { types == null || it.type in types }
        val start = untilId?.let { id ->
            matching.indexOfFirst { it.id == id }.let { if (it < 0) matching.size else it + 1 }
        } ?: 0
        return matching.drop(start).take(limit)
    }

    override suspend fun unreadCount(token: String): Int = unread

    override suspend fun markAllAsRead(token: String) {
        markReadFailure?.let { throw it }
        markAllReadTokens += token
    }

    override suspend fun respondToFollowRequest(token: String, userId: String, accept: Boolean) {
        followFailure?.let { throw it }
        followResponses += userId to accept
    }
}

class FakeNotificationStream : NotificationStream {
    val shared = MutableSharedFlow<NotificationStreamEvent>(extraBufferCapacity = 16)

    override fun events(token: String): Flow<NotificationStreamEvent> = shared
}

class FakeSystemNotifier : SystemNotifier {
    val shown = CopyOnWriteArrayList<FortNotification>()
    val cancelled = CopyOnWriteArrayList<String>()
    val failed = CopyOnWriteArrayList<Pair<QuickAction, String>>()
    @Volatile var clearCount = 0
    @Volatile var lastActions: Set<QuickAction> = emptySet()

    override suspend fun show(notifications: List<FortNotification>, actions: Set<QuickAction>) {
        shown += notifications
        lastActions = actions
    }

    override fun showActionFailed(action: QuickAction, noteId: String) {
        failed += action to noteId
    }

    override fun cancel(notificationId: String) {
        cancelled += notificationId
    }

    override fun clearAll() {
        clearCount++
    }
}

class FakeNotificationSyncStore : NotificationSyncStore {
    @Volatile var lastSeen: Long = 0L

    override suspend fun lastSeenMillis(): Long = lastSeen

    override suspend fun advanceLastSeenMillis(millis: Long) {
        if (millis > lastSeen) lastSeen = millis
    }

    override suspend fun clear() {
        lastSeen = 0L
    }
}

class FakeNotificationWork : NotificationWork {
    @Volatile var polling = false
    @Volatile var cancelledActions = 0
    val followResponses = CopyOnWriteArrayList<Triple<String, String, Boolean>>()
    @Volatile var markAllReadCount = 0
    val quickReplies = CopyOnWriteArrayList<Pair<String, String>>()
    val quickLikes = CopyOnWriteArrayList<String>()

    override fun enqueueQuickReply(noteId: String, text: String) {
        quickReplies += noteId to text
    }

    override fun enqueueQuickLike(noteId: String) {
        quickLikes += noteId
    }

    override fun schedulePolling() {
        polling = true
    }

    override fun cancelPolling() {
        polling = false
    }

    override fun cancelActions() {
        cancelledActions++
    }

    override fun enqueueFollowRequestResponse(notificationId: String, userId: String, accept: Boolean) {
        followResponses += Triple(notificationId, userId, accept)
    }

    override fun enqueueMarkAllRead() {
        markAllReadCount++
    }
}

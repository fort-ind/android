package com.fortind.zenith.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.fortind.zenith.MainActivity
import com.fortind.zenith.R
import java.util.Objects
import kotlinx.coroutines.withTimeoutOrNull

interface SystemNotifier {
    suspend fun show(notifications: List<FortNotification>, actions: Set<QuickAction> = emptySet())

    fun showActionFailed(action: QuickAction, noteId: String)

    fun cancel(notificationId: String)

    fun clearAll()
}

class AndroidSystemNotifier(private val context: Context) : SystemNotifier {
    private val manager = NotificationManagerCompat.from(context)
    private val systemManager = context.getSystemService(NotificationManager::class.java)

    private val channels by lazy { createChannels() }

    fun ensureChannels() {
        channels
    }

    override suspend fun show(notifications: List<FortNotification>, actions: Set<QuickAction>) {
        ensureChannels()
        notifications.forEach { post(it, avatar = loadAvatar(it.actor?.avatarUrl), actions) }
        updateSummary()
    }

    override fun showActionFailed(action: QuickAction, noteId: String) {
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_THREAD_NOTE_ID, noteId)
        if (action == QuickAction.Reply) intent.putExtra(MainActivity.EXTRA_OPEN_REPLY, true)
        val tap = PendingIntent.getActivity(
            context,
            Objects.hash(noteId, FAILED_ACTION, action.name),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val isReply = action == QuickAction.Reply
        val notification = NotificationCompat.Builder(context, NotificationCategory.Mentions.channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(context.getString(if (isReply) R.string.notification_reply_failed_title else R.string.notification_like_failed_title))
            .setContentText(context.getString(if (isReply) R.string.notification_reply_failed_body else R.string.notification_like_failed_body))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setGroup(FAILED_GROUP_KEY)
            .setContentIntent(tap)
            .build()
        notifyIfAllowed(Objects.hash(FAILED_ACTION, action.name, noteId), notification)
    }

    override fun cancel(notificationId: String) {
        manager.cancel(notificationId.hashCode())
        updateSummary()
    }

    override fun clearAll() {
        systemManager.activeNotifications
            .filter { it.notification.group == GROUP_KEY }
            .forEach { manager.cancel(it.id) }
    }

    private fun createChannels() {
        NotificationCategory.entries.forEach { category ->
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(category.channelId, category.importance())
                    .setName(context.getString(category.labelRes))
                    .build()
            )
        }
    }

    private fun post(notification: FortNotification, avatar: Bitmap?, actions: Set<QuickAction>) {
        val resources = context.resources
        val text = notification.systemBodyText(context.getString(R.string.notifications_direct_body))
        val builder = NotificationCompat.Builder(context, notification.type.category().channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(notification.titleText(resources))
            .setContentText(text)
            .setLargeIcon(avatar)
            .setWhen(notification.createdAtMillis)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openAppIntent(notification))
        if (text != null) builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        addActions(builder, notification, actions)
        notifyIfAllowed(notification.id.hashCode(), builder.build())
    }

    private fun addActions(builder: NotificationCompat.Builder, notification: FortNotification, actions: Set<QuickAction>) {
        val actorId = notification.actor?.id
        val noteId = notification.noteId?.takeIf { notification.type == NotificationType.Mention || notification.type == NotificationType.Reply }
        val canReply = noteId != null && QuickAction.Reply in actions
        val canLike = noteId != null && QuickAction.Like in actions && !notification.hasMyReaction
        if (notification.type == NotificationType.FollowRequest && actorId != null) {
            builder.addAction(
                lockedAction(
                    context.getString(R.string.notifications_accept),
                    actionIntent(NotificationActionReceiver.ACTION_ACCEPT, notification.id, actorId),
                )
            )
            builder.addAction(
                lockedAction(
                    context.getString(R.string.notifications_reject),
                    actionIntent(NotificationActionReceiver.ACTION_REJECT, notification.id, actorId),
                )
            )
        } else if (noteId != null && (canReply || canLike)) {
            if (canReply) builder.addAction(replyAction(notification.id, noteId))
            if (canLike) builder.addAction(likeAction(notification.id, noteId))
        } else {
            builder.addAction(
                lockedAction(
                    context.getString(R.string.notification_action_mark_read),
                    actionIntent(NotificationActionReceiver.ACTION_MARK_READ, notification.id, null),
                )
            )
        }
    }

    private fun lockedAction(title: String, intent: PendingIntent): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, title, intent)
            .setAuthenticationRequired(true)
            .build()

    private fun replyAction(notificationId: String, noteId: String): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_REPLY)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(NotificationActionReceiver.EXTRA_NOTE_ID, noteId)
        val pending = PendingIntentCompat.getBroadcast(
            context,
            Objects.hash(notificationId, NotificationActionReceiver.ACTION_REPLY),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            true,
        )
        val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_reply_label))
            .build()
        return NotificationCompat.Action.Builder(0, context.getString(R.string.notification_action_reply), pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAuthenticationRequired(true)
            .build()
    }

    private fun likeAction(notificationId: String, noteId: String): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_LIKE)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(NotificationActionReceiver.EXTRA_NOTE_ID, noteId)
        val pending = PendingIntent.getBroadcast(
            context,
            Objects.hash(notificationId, NotificationActionReceiver.ACTION_LIKE),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(0, context.getString(R.string.notification_action_like), pending)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
            .setAuthenticationRequired(true)
            .build()
    }

    private fun updateSummary() {
        val shown = systemManager.activeNotifications.count {
            it.id != SUMMARY_ID && it.notification.group == GROUP_KEY
        }
        if (shown < MIN_FOR_SUMMARY) {
            manager.cancel(SUMMARY_ID)
            return
        }
        val summary = NotificationCompat.Builder(context, NotificationCategory.Other.channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_summary))
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openAppIntent(notification = null))
            .build()
        notifyIfAllowed(SUMMARY_ID, summary)
    }

    private fun notifyIfAllowed(id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(id, notification)
    }

    private fun openAppIntent(notification: FortNotification?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val noteId = notification?.noteId
        if (noteId != null) {
            intent.putExtra(MainActivity.EXTRA_THREAD_NOTE_ID, noteId)
        } else {
            intent.putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_NOTIFICATIONS)
        }
        val requestCode = if (notification == null) {
            OPEN_APP_REQUEST_CODE
        } else {
            Objects.hash(notification.id, OPEN_ACTION)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun actionIntent(action: String, notificationId: String, userId: String?): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        userId?.let { intent.putExtra(NotificationActionReceiver.EXTRA_USER_ID, it) }
        return PendingIntent.getBroadcast(
            context,
            Objects.hash(notificationId, action),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private suspend fun loadAvatar(url: String?): Bitmap? {
        if (url == null) return null
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(AVATAR_PX, AVATAR_PX)
            .allowHardware(false)
            .build()
        val result = withTimeoutOrNull(AVATAR_TIMEOUT_MS) { SingletonImageLoader.get(context).execute(request) }
        val image = (result as? SuccessResult)?.image ?: return null
        return circleCrop(image.toBitmap())
    }

    private fun circleCrop(source: Bitmap): Bitmap {
        val output = createBitmap(AVATAR_PX, AVATAR_PX)
        val scaled = source.scale(AVATAR_PX, AVATAR_PX)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = AVATAR_PX / 2f
        Canvas(output).drawCircle(radius, radius, radius, paint)
        return output
    }

    private fun NotificationCategory.importance(): Int = when (this) {
        NotificationCategory.Mentions -> NotificationManager.IMPORTANCE_HIGH
        NotificationCategory.Follows -> NotificationManager.IMPORTANCE_DEFAULT
        NotificationCategory.Reactions, NotificationCategory.Renotes, NotificationCategory.Other ->
            NotificationManager.IMPORTANCE_LOW
    }

    private companion object {
        const val GROUP_KEY = "com.fortind.zenith.NOTIFICATIONS"
        const val FAILED_GROUP_KEY = "com.fortind.zenith.ACTION_FAILED"
        const val FAILED_ACTION = "failed"
        const val SUMMARY_ID = 0
        const val MIN_FOR_SUMMARY = 2
        const val OPEN_APP_REQUEST_CODE = 1
        const val OPEN_ACTION = "open"
        const val AVATAR_PX = 128
        const val AVATAR_TIMEOUT_MS = 5_000L
    }
}

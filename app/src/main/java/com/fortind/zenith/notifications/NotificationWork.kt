package com.fortind.zenith.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.HttpStatusException
import java.io.IOException
import java.util.concurrent.TimeUnit

interface NotificationWork {
    fun schedulePolling()
    fun cancelPolling()
    fun enqueueFollowRequestResponse(notificationId: String, userId: String, accept: Boolean)
    fun enqueueMarkAllRead()
}

class WorkManagerNotificationWork(private val context: Context) : NotificationWork {
    private val workManager get() = WorkManager.getInstance(context)
    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun schedulePolling() {
        val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(needsNetwork)
            .build()
        workManager.enqueueUniquePeriodicWork(POLL_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelPolling() {
        workManager.cancelUniqueWork(POLL_WORK_NAME)
    }

    override fun enqueueFollowRequestResponse(notificationId: String, userId: String, accept: Boolean) {
        workManager.enqueue(
            OneTimeWorkRequestBuilder<FollowRequestWorker>()
                .setConstraints(needsNetwork)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(FollowRequestWorker.KEY_NOTIFICATION_ID, notificationId)
                        .putString(FollowRequestWorker.KEY_USER_ID, userId)
                        .putBoolean(FollowRequestWorker.KEY_ACCEPT, accept)
                        .build()
                )
                .build()
        )
    }

    override fun enqueueMarkAllRead() {
        workManager.enqueue(
            OneTimeWorkRequestBuilder<MarkAllReadWorker>()
                .setConstraints(needsNetwork)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        )
    }

    private companion object {
        const val POLL_WORK_NAME = "notification-poll"
        const val POLL_INTERVAL_MINUTES = 15L
        const val BACKOFF_SECONDS = 30L
    }
}

private val Context.container get() = (applicationContext as ZenithApplication).container

class NotificationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return when (applicationContext.container.notificationSyncer.sync()) {
            SyncOutcome.Done -> Result.success()
            SyncOutcome.Retry -> Result.retry()
        }
    }
}

class FollowRequestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val notificationId = inputData.getString(KEY_NOTIFICATION_ID) ?: return Result.failure()
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val container = applicationContext.container
        val token = container.sessionService.token() ?: return Result.failure()
        return try {
            container.notificationsApi.respondToFollowRequest(token, userId, inputData.getBoolean(KEY_ACCEPT, false))
            container.systemNotifier.cancel(notificationId)
            Result.success()
        } catch (e: HttpStatusException) {
            if (e.code in CLIENT_ERROR_RANGE) {
                container.systemNotifier.cancel(notificationId)
                Result.failure()
            } else {
                retryOrFail()
            }
        } catch (_: IOException) {
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCEPT = "accept"
        private const val MAX_ATTEMPTS = 3
        private val CLIENT_ERROR_RANGE = 400..499
    }
}

class MarkAllReadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val token = container.sessionService.token() ?: return Result.failure()
        return try {
            container.notificationsApi.markAllAsRead(token)
            container.systemNotifier.clearAll()
            Result.success()
        } catch (_: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val work = context.container.notificationWork
        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        when (intent.action) {
            ACTION_ACCEPT -> if (notificationId != null && userId != null) {
                work.enqueueFollowRequestResponse(notificationId, userId, accept = true)
            }
            ACTION_REJECT -> if (notificationId != null && userId != null) {
                work.enqueueFollowRequestResponse(notificationId, userId, accept = false)
            }
            ACTION_MARK_READ -> work.enqueueMarkAllRead()
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.fortind.zenith.action.ACCEPT_FOLLOW_REQUEST"
        const val ACTION_REJECT = "com.fortind.zenith.action.REJECT_FOLLOW_REQUEST"
        const val ACTION_MARK_READ = "com.fortind.zenith.action.MARK_ALL_READ"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_USER_ID = "user_id"
    }
}

package com.fortind.zenith.notifications

import com.fortind.zenith.net.ChannelEvent
import com.fortind.zenith.net.MisskeyStreaming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONObject

sealed interface NotificationStreamEvent {
    data object Connected : NotificationStreamEvent

    data class Received(val notification: FortNotification) : NotificationStreamEvent

    data object AllRead : NotificationStreamEvent
}

interface NotificationStream {
    fun events(token: String): Flow<NotificationStreamEvent>
}

fun parseMainChannelEvent(type: String, body: JSONObject?): NotificationStreamEvent? = when (type) {
    "notification" -> body?.let(::parseNotification)?.let(NotificationStreamEvent::Received)
    "readAllNotifications" -> NotificationStreamEvent.AllRead
    else -> null
}

class StreamingNotificationStream(private val streaming: MisskeyStreaming) : NotificationStream {
    override fun events(token: String): Flow<NotificationStreamEvent> =
        streaming.connection(token).channel("main").mapNotNull { event ->
            when (event) {
                ChannelEvent.Connected -> NotificationStreamEvent.Connected
                is ChannelEvent.Message -> parseMainChannelEvent(event.type, event.body)
            }
        }
}

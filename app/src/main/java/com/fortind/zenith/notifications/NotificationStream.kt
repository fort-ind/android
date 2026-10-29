package com.fortind.zenith.notifications

import com.fortind.zenith.auth.FortInstance
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject

sealed interface NotificationStreamEvent {
    data object Connected : NotificationStreamEvent

    data class Received(val notification: FortNotification) : NotificationStreamEvent

    data object AllRead : NotificationStreamEvent
}

interface NotificationStream {
    fun events(token: String): Flow<NotificationStreamEvent>
}

private const val MAIN_CHANNEL_ID = "main"

fun parseStreamMessage(text: String): NotificationStreamEvent? {
    val json = try {
        JSONObject(text)
    } catch (_: JSONException) {
        return null
    }
    if (json.optString("type") != "channel") return null
    val body = json.optJSONObject("body") ?: return null
    if (body.optString("id") != MAIN_CHANNEL_ID) return null
    return when (body.optString("type")) {
        "notification" -> body.optJSONObject("body")?.let(::parseNotification)?.let(NotificationStreamEvent::Received)
        "readAllNotifications" -> NotificationStreamEvent.AllRead
        else -> null
    }
}

class OkHttpNotificationStream(
    private val client: OkHttpClient = defaultClient(),
    private val host: String = FortInstance.HOST,
) : NotificationStream {

    override fun events(token: String): Flow<NotificationStreamEvent> = flow {
        var backoffMs = INITIAL_BACKOFF_MS
        while (true) {
            try {
                connectOnce(token).collect { event ->
                    if (event is NotificationStreamEvent.Connected) backoffMs = INITIAL_BACKOFF_MS
                    emit(event)
                }
            } catch (_: IOException) {
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun connectOnce(token: String): Flow<NotificationStreamEvent> = callbackFlow {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegment("streaming")
            .addQueryParameter("i", token)
            .build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(CONNECT_MAIN_MESSAGE)
                trySend(NotificationStreamEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseStreamMessage(text)?.let { trySend(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
                close(IOException("Stream closed ($code)"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(IOException("Stream failed", t))
            }
        }
        val socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        awaitClose { socket.cancel() }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val PING_INTERVAL_SECONDS = 30L
        val CONNECT_MAIN_MESSAGE = JSONObject()
            .put("type", "connect")
            .put("body", JSONObject().put("channel", "main").put("id", MAIN_CHANNEL_ID))
            .toString()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

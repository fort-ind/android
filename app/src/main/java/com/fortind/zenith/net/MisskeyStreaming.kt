package com.fortind.zenith.net

import com.fortind.zenith.auth.FortInstance
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import org.json.JSONException
import org.json.JSONObject

sealed interface StreamFrame {
    data class Opened(val generation: Int) : StreamFrame

    data class Channel(val id: String, val type: String, val body: JSONObject?) : StreamFrame

    data class NoteUpdated(val noteId: String, val type: String, val body: JSONObject?) : StreamFrame
}

sealed interface ChannelEvent {
    data object Connected : ChannelEvent

    data class Message(val type: String, val body: JSONObject?) : ChannelEvent
}

fun parseStreamFrame(text: String): StreamFrame? {
    val json = try {
        JSONObject(text)
    } catch (_: JSONException) {
        return null
    }
    val body = json.optJSONObject("body") ?: return null
    val id = body.optStringOrNull("id") ?: return null
    val type = body.optStringOrNull("type") ?: return null
    return when (json.optString("type")) {
        "channel" -> StreamFrame.Channel(id, type, body.optJSONObject("body"))
        "noteUpdated" -> StreamFrame.NoteUpdated(id, type, body.optJSONObject("body"))
        else -> null
    }
}

interface StreamSocket {
    fun send(text: String): Boolean

    fun cancel()
}

interface StreamListener {
    fun onOpen(socket: StreamSocket)

    fun onMessage(text: String)

    fun onClosed(cause: Throwable?)
}

interface StreamTransport {
    fun open(url: String, listener: StreamListener): StreamSocket
}

class MisskeyStreaming(
    private val transport: StreamTransport,
    private val scope: CoroutineScope,
    private val host: String = FortInstance.HOST,
    private val initialBackoffMs: Long = 1_000,
    private val maxBackoffMs: Long = 30_000,
) {
    private val connections = LinkedHashMap<String?, StreamConnection>()

    fun connection(token: String?): StreamConnection = synchronized(connections) {
        connections.getOrPut(token) {
            val url = if (token == null) "https://$host/streaming" else "https://$host/streaming?i=$token"
            StreamConnection(transport, url, scope, initialBackoffMs, maxBackoffMs)
        }.also {
            while (connections.size > MAX_CACHED_CONNECTIONS) connections.remove(connections.keys.first())
        }
    }

    private companion object {
        const val MAX_CACHED_CONNECTIONS = 2
    }
}

class StreamConnection internal constructor(
    private val transport: StreamTransport,
    private val url: String,
    scope: CoroutineScope,
    private val initialBackoffMs: Long,
    private val maxBackoffMs: Long,
) {
    private val generations = AtomicInteger(0)
    private val channelIds = AtomicInteger(0)

    @Volatile
    private var socket: StreamSocket? = null

    @Volatile
    private var openGeneration = 0

    val frames: SharedFlow<StreamFrame> = flow {
        var backoffMs = initialBackoffMs
        while (true) {
            try {
                connectOnce().collect { frame ->
                    if (frame is StreamFrame.Opened) backoffMs = initialBackoffMs
                    emit(frame)
                }
            } catch (_: IOException) {
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS))

    private fun send(text: String): Boolean = socket?.send(text) ?: false

    private fun framesFromNow(): Flow<StreamFrame> = frames.onSubscription {
        val generation = openGeneration
        if (generation > 0) emit(StreamFrame.Opened(generation))
    }

    fun channel(name: String, params: JSONObject = JSONObject()): Flow<ChannelEvent> = flow {
        val id = "c${channelIds.incrementAndGet()}"
        val connect = JSONObject()
            .put("type", "connect")
            .put("body", JSONObject().put("channel", name).put("id", id).put("params", params))
            .toString()
        var connectedGeneration = 0
        try {
            framesFromNow().collect { frame ->
                when (frame) {
                    is StreamFrame.Opened -> if (frame.generation > connectedGeneration && send(connect)) {
                        connectedGeneration = frame.generation
                        emit(ChannelEvent.Connected)
                    }
                    is StreamFrame.Channel -> if (frame.id == id) emit(ChannelEvent.Message(frame.type, frame.body))
                    is StreamFrame.NoteUpdated -> Unit
                }
            }
        } finally {
            if (connectedGeneration > 0) send(message("disconnect", id))
        }
    }

    fun noteUpdates(captured: Flow<Set<String>>): Flow<StreamFrame.NoteUpdated> = flow {
        var wanted = emptySet<String>()
        var subscribed = emptySet<String>()
        var subscribedGeneration = 0
        fun sync() {
            val generation = openGeneration
            if (generation == 0) return
            if (generation != subscribedGeneration) {
                subscribed = emptySet()
                subscribedGeneration = generation
            }
            (wanted - subscribed).forEach { send(message("s", it)) }
            (subscribed - wanted).forEach { send(message("un", it)) }
            subscribed = wanted
        }
        try {
            merge<Any>(captured.map(::CaptureChange), framesFromNow()).collect { event ->
                when (event) {
                    is CaptureChange -> {
                        wanted = event.ids
                        sync()
                    }
                    is StreamFrame.Opened -> sync()
                    is StreamFrame.NoteUpdated -> if (event.noteId in wanted) emit(event)
                    else -> Unit
                }
            }
        } finally {
            if (subscribedGeneration == openGeneration) subscribed.forEach { send(message("un", it)) }
        }
    }

    private class CaptureChange(val ids: Set<String>)

    private fun connectOnce(): Flow<StreamFrame> = callbackFlow {
        val listener = object : StreamListener {
            override fun onOpen(socket: StreamSocket) {
                this@StreamConnection.socket = socket
                val generation = generations.incrementAndGet()
                openGeneration = generation
                trySend(StreamFrame.Opened(generation))
            }

            override fun onMessage(text: String) {
                parseStreamFrame(text)?.let { trySend(it) }
            }

            override fun onClosed(cause: Throwable?) {
                close(IOException("Stream closed", cause))
            }
        }
        val opened = transport.open(url, listener)
        awaitClose {
            openGeneration = 0
            socket = null
            opened.cancel()
        }
    }.buffer(Channel.UNLIMITED)

    private companion object {
        const val STOP_TIMEOUT_MS = 2_000L

        fun message(type: String, id: String): String =
            JSONObject().put("type", type).put("body", JSONObject().put("id", id)).toString()
    }
}

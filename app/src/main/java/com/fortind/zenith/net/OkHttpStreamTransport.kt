package com.fortind.zenith.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class OkHttpStreamTransport(client: OkHttpClient) : StreamTransport {
    private val client = client.newBuilder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun open(url: String, token: String?, listener: StreamListener): StreamSocket {
        val request = Request.Builder().url(url).apply {
            token?.let { header("Authorization", "Bearer $it") }
        }.build()
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen(OkHttpStreamSocket(webSocket))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL_CLOSURE, null)
                    listener.onClosed(null)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val refused = response?.code?.takeIf { it == HTTP_UNAUTHORIZED || it == HTTP_FORBIDDEN }
                    listener.onClosed(refused?.let(::StreamRejectedException) ?: t)
                }
            },
        )
        return OkHttpStreamSocket(socket)
    }

    private class OkHttpStreamSocket(private val socket: WebSocket) : StreamSocket {
        override fun send(text: String): Boolean = socket.send(text)

        override fun cancel() = socket.cancel()
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val PING_INTERVAL_SECONDS = 30L
    }
}

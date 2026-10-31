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

    override fun open(url: String, listener: StreamListener): StreamSocket {
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
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
                    listener.onClosed(t)
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
        const val PING_INTERVAL_SECONDS = 30L
    }
}

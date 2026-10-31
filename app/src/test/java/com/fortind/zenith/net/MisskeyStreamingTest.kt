package com.fortind.zenith.net

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MisskeyStreamingTest {

    private class FakeSocket(val listener: StreamListener) : StreamSocket {
        val sent = CopyOnWriteArrayList<JSONObject>()
        var cancelled = false

        override fun send(text: String): Boolean {
            if (cancelled) return false
            sent += JSONObject(text)
            return true
        }

        override fun cancel() {
            cancelled = true
        }

        fun open() = listener.onOpen(this)

        fun receive(text: String) = listener.onMessage(text)

        fun drop() = listener.onClosed(null)

        fun sentOfType(type: String) = sent.filter { it.getString("type") == type }
    }

    private class FakeTransport : StreamTransport {
        val sockets = CopyOnWriteArrayList<FakeSocket>()
        val urls = CopyOnWriteArrayList<String>()

        val latest: FakeSocket get() = sockets.last()

        override fun open(url: String, listener: StreamListener): StreamSocket {
            urls += url
            return FakeSocket(listener).also { sockets += it }
        }
    }

    private fun TestScope.streaming(transport: FakeTransport) =
        MisskeyStreaming(transport, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)), host = "example.social", initialBackoffMs = 10)

    private fun channelFrame(id: String, type: String, body: String = "{}") =
        """{"type":"channel","body":{"id":"$id","type":"$type","body":$body}}"""

    @Test
    fun channelConnectsOnOpenAndReceivesOnlyItsMessages() = runTest {
        val transport = FakeTransport()
        val connection = streaming(transport).connection("tok")
        val events = mutableListOf<ChannelEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            connection.channel("localTimeline", JSONObject().put("withRenotes", true)).collect { events += it }
        }

        transport.latest.open()
        val connect = transport.latest.sentOfType("connect").single().getJSONObject("body")
        val id = connect.getString("id")
        transport.latest.receive(channelFrame(id, "note", """{"id":"n1"}"""))
        transport.latest.receive(channelFrame("other", "note", """{"id":"n2"}"""))

        assertEquals("https://example.social/streaming?i=tok", transport.urls.single())
        assertEquals("localTimeline", connect.getString("channel"))
        assertTrue(connect.getJSONObject("params").getBoolean("withRenotes"))
        assertEquals(ChannelEvent.Connected, events[0])
        assertEquals(2, events.size)
        assertEquals("n1", (events[1] as ChannelEvent.Message).body?.getString("id"))
    }

    @Test
    fun guestConnectionsOmitTheToken() = runTest {
        val transport = FakeTransport()
        val connection = streaming(transport).connection(null)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.channel("globalTimeline").collect {} }

        assertEquals("https://example.social/streaming", transport.urls.single())
    }

    @Test
    fun twoChannelsShareOneSocket() = runTest {
        val transport = FakeTransport()
        val streaming = streaming(transport)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { streaming.connection("tok").channel("main").collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { streaming.connection("tok").channel("homeTimeline").collect {} }

        transport.latest.open()

        assertEquals(1, transport.sockets.size)
        val channels = transport.latest.sentOfType("connect").map { it.getJSONObject("body").getString("channel") }
        assertEquals(listOf("main", "homeTimeline"), channels)
    }

    @Test
    fun subscribingToAnOpenSocketConnectsExactlyOnce() = runTest {
        val transport = FakeTransport()
        val connection = streaming(transport).connection("tok")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.channel("main").collect {} }
        transport.latest.open()

        val events = mutableListOf<ChannelEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            connection.channel("homeTimeline").collect { events += it }
        }

        val homeConnects = transport.latest.sentOfType("connect").filter {
            it.getJSONObject("body").getString("channel") == "homeTimeline"
        }
        assertEquals(1, homeConnects.size)
        assertEquals(listOf<ChannelEvent>(ChannelEvent.Connected), events)
    }

    @Test
    fun channelsReconnectAfterTheSocketDrops() = runTest {
        val transport = FakeTransport()
        val connection = streaming(transport).connection("tok")
        val events = mutableListOf<ChannelEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.channel("main").collect { events += it } }
        transport.latest.open()

        transport.latest.drop()
        advanceTimeBy(20)
        runCurrent()
        transport.latest.open()

        assertEquals(2, transport.sockets.size)
        assertEquals(1, transport.latest.sentOfType("connect").size)
        assertEquals(listOf<ChannelEvent>(ChannelEvent.Connected, ChannelEvent.Connected), events)
    }

    @Test
    fun cancellingAChannelDisconnectsIt() = runTest {
        val transport = FakeTransport()
        val connection = streaming(transport).connection("tok")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.channel("main").collect {} }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.channel("homeTimeline").collect {} }
        transport.latest.open()
        val homeId = transport.latest.sentOfType("connect")[1].getJSONObject("body").getString("id")

        job.cancel()

        val disconnect = transport.latest.sentOfType("disconnect").single()
        assertEquals(homeId, disconnect.getJSONObject("body").getString("id"))
    }

    @Test
    fun noteCapturesFollowTheWantedSetAndAreResentAfterReconnect() = runTest {
        val transport = FakeTransport()
        val connection = streaming(transport).connection("tok")
        val captured = MutableStateFlow(setOf("a", "b"))
        val updates = mutableListOf<StreamFrame.NoteUpdated>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            connection.noteUpdates(captured).collect { updates += it }
        }
        transport.latest.open()

        assertEquals(setOf("a", "b"), transport.latest.sentOfType("s").map { it.getJSONObject("body").getString("id") }.toSet())

        captured.value = setOf("b", "c")
        assertEquals(listOf("c"), transport.latest.sentOfType("s").drop(2).map { it.getJSONObject("body").getString("id") })
        assertEquals(listOf("a"), transport.latest.sentOfType("un").map { it.getJSONObject("body").getString("id") })

        transport.latest.receive("""{"type":"noteUpdated","body":{"id":"c","type":"reacted","body":{"reaction":"👍"}}}""")
        transport.latest.receive("""{"type":"noteUpdated","body":{"id":"a","type":"reacted","body":{"reaction":"👍"}}}""")
        assertEquals(listOf("c"), updates.map { it.noteId })

        transport.latest.drop()
        advanceTimeBy(20)
        runCurrent()
        transport.latest.open()
        assertEquals(setOf("b", "c"), transport.latest.sentOfType("s").map { it.getJSONObject("body").getString("id") }.toSet())
    }

    @Test
    fun streamFramesAreParsed() {
        val channel = parseStreamFrame("""{"type":"channel","body":{"id":"c1","type":"note","body":{"id":"n"}}}""")
        val updated = parseStreamFrame("""{"type":"noteUpdated","body":{"id":"n","type":"deleted","body":{"deletedAt":"x"}}}""")

        assertEquals("c1", (channel as StreamFrame.Channel).id)
        assertEquals("n", channel.body?.getString("id"))
        assertEquals("deleted", (updated as StreamFrame.NoteUpdated).type)
        assertNull(parseStreamFrame("not json"))
        assertNull(parseStreamFrame("""{"type":"channel"}"""))
        assertNull(parseStreamFrame("""{"type":"pong","body":{"id":"x","type":"y"}}"""))
    }
}

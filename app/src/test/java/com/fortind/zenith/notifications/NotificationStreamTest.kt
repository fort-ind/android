package com.fortind.zenith.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStreamTest {

    private fun channelMessage(channelId: String, type: String, body: String? = null) = buildString {
        append("""{"type":"channel","body":{"id":"$channelId","type":"$type"""")
        if (body != null) append(""","body":$body""")
        append("}}")
    }

    @Test
    fun notificationEventCarriesTheParsedNotification() {
        val message = channelMessage(
            "main",
            "notification",
            """{"id":"n9","createdAt":"2025-10-28T10:00:00.000Z","type":"follow",
               "user":{"id":"u1","username":"alice"}}""",
        )

        val event = parseStreamMessage(message)

        assertTrue(event is NotificationStreamEvent.Received)
        assertEquals("n9", (event as NotificationStreamEvent.Received).notification.id)
    }

    @Test
    fun readAllNotificationsBecomesAllRead() {
        assertEquals(
            NotificationStreamEvent.AllRead,
            parseStreamMessage(channelMessage("main", "readAllNotifications")),
        )
    }

    @Test
    fun otherMainChannelEventsAreIgnored() {
        assertNull(parseStreamMessage(channelMessage("main", "unreadMention")))
    }

    @Test
    fun otherChannelsAreIgnored() {
        assertNull(parseStreamMessage(channelMessage("homeTimeline", "readAllNotifications")))
    }

    @Test
    fun malformedMessagesAreIgnored() {
        assertNull(parseStreamMessage("not json"))
        assertNull(parseStreamMessage("""{"type":"channel"}"""))
        assertNull(parseStreamMessage(channelMessage("main", "notification", "null")))
    }
}

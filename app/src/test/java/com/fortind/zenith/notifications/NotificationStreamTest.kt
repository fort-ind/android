package com.fortind.zenith.notifications

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStreamTest {

    @Test
    fun notificationEventCarriesTheParsedNotification() {
        val body = JSONObject(
            """{"id":"n9","createdAt":"2025-10-28T10:00:00.000Z","type":"follow",
               "user":{"id":"u1","username":"alice"}}""",
        )

        val event = parseMainChannelEvent("notification", body)

        assertTrue(event is NotificationStreamEvent.Received)
        assertEquals("n9", (event as NotificationStreamEvent.Received).notification.id)
    }

    @Test
    fun readAllNotificationsBecomesAllRead() {
        assertEquals(NotificationStreamEvent.AllRead, parseMainChannelEvent("readAllNotifications", null))
    }

    @Test
    fun otherMainChannelEventsAreIgnored() {
        assertNull(parseMainChannelEvent("unreadMention", null))
    }

    @Test
    fun notificationsWithoutABodyAreIgnored() {
        assertNull(parseMainChannelEvent("notification", null))
        assertNull(parseMainChannelEvent("notification", JSONObject()))
    }
}

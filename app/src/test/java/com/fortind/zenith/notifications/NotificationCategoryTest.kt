package com.fortind.zenith.notifications

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCategoryTest {

    @Test
    fun everyNotificationTypeBelongsToACategory() {
        val mapped = NotificationType.entries.associateWith { it.category() }

        assertEquals(NotificationType.entries.size, mapped.size)
        assertEquals(NotificationCategory.Mentions, NotificationType.Reply.category())
        assertEquals(NotificationCategory.Mentions, NotificationType.Quote.category())
        assertEquals(NotificationCategory.Follows, NotificationType.FollowRequest.category())
        assertEquals(NotificationCategory.Other, NotificationType.Achievement.category())
    }

    @Test
    fun channelIdsAreUniquePerCategory() {
        val ids = NotificationCategory.entries.map { it.channelId }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun legacyPriorityAndSoundMatchTheChannelImportance() {
        assertEquals(NotificationManagerCompat.IMPORTANCE_HIGH, NotificationCategory.Mentions.alert.importance)
        assertEquals(NotificationCompat.PRIORITY_HIGH, NotificationCategory.Mentions.alert.legacyPriority)
        assertTrue(NotificationCategory.Mentions.alert.legacySound)

        assertEquals(NotificationManagerCompat.IMPORTANCE_DEFAULT, NotificationCategory.Follows.alert.importance)
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, NotificationCategory.Follows.alert.legacyPriority)
        assertTrue(NotificationCategory.Follows.alert.legacySound)

        listOf(NotificationCategory.Reactions, NotificationCategory.Renotes, NotificationCategory.Other).forEach { quiet ->
            assertEquals(NotificationManagerCompat.IMPORTANCE_LOW, quiet.alert.importance)
            assertEquals(NotificationCompat.PRIORITY_LOW, quiet.alert.legacyPriority)
            assertFalse(quiet.alert.legacySound)
        }
    }

    @Test
    fun noisySystemNotificationsAreOffByDefault() {
        assertTrue(NotificationCategory.Mentions in DefaultNotificationCategories)
        assertFalse(NotificationCategory.Other in DefaultNotificationCategories)
    }

    @Test
    fun reactionBodiesCombineTheEmojiWithThePostPreview() {
        val reaction = testNotification("1", NotificationType.Reaction).copy(reaction = "👍")

        assertEquals("👍 text 1", reaction.bodyText())
        assertEquals("text 2", testNotification("2").bodyText())
    }
}

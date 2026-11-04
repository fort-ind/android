package com.fortind.zenith.discovery

import com.fortind.zenith.ui.people.parseTagLink
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryParsingTest {

    @Test
    fun aHashtagNeedsATagAndAnAudience() {
        assertEquals(Hashtag("linux", 480), parseHashtag(JSONObject("""{"tag":"linux","mentionedUsersCount":480}""")))
        assertNull(parseHashtag(JSONObject("""{"tag":"quiet","mentionedUsersCount":0}""")))
        assertNull(parseHashtag(JSONObject("""{"mentionedUsersCount":4}""")))
    }

    @Test
    fun announcementsDefaultToInfoAndUnread() {
        val parsed = parseAnnouncement(JSONObject("""{"id":"a","title":"Hi","text":"**there**","icon":"mystery"}"""))!!

        assertEquals(AnnouncementKind.Info, parsed.kind)
        assertFalse(parsed.isRead)
        assertTrue(parsed.textMfm.isNotEmpty())
        assertEquals(AnnouncementKind.Warning, parseAnnouncement(JSONObject("""{"id":"b","icon":"warning"}"""))!!.kind)
    }

    @Test
    fun onlyUnreadAndUndismissedAnnouncementsShow() {
        val all = listOf(
            Announcement("read", "", emptyList(), AnnouncementKind.Info, isRead = true),
            Announcement("gone", "", emptyList(), AnnouncementKind.Info, isRead = false),
            Announcement("new", "", emptyList(), AnnouncementKind.Info, isRead = false),
        )

        assertEquals(listOf("new"), unreadAnnouncements(all, listOf("gone")).map { it.id })
    }

    @Test
    fun dismissedIdsKeepTheNewestFifty() {
        var ids = emptyList<String>()
        repeat(60) { ids = rememberDismissed(ids, "id$it") }

        assertEquals(MAX_DISMISSED_ANNOUNCEMENTS, ids.size)
        assertEquals("id59", ids.first())
        assertEquals(listOf("id3", "id59").first(), rememberDismissed(ids, "id3").first())
    }

    @Test
    fun listsAntennasAndMembersParse() {
        assertEquals(
            UserListInfo("l", "Friends", listOf("u1", "u2")),
            parseUserList(JSONObject("""{"id":"l","name":"Friends","userIds":["u1","u2"]}""")),
        )
        assertEquals(AntennaInfo("a", "Mentions", true), parseAntenna(JSONObject("""{"id":"a","name":"Mentions","hasUnreadNote":true}""")))
        assertNull(parseListMember(JSONObject("""{"id":"m"}""")))
        assertEquals(
            "u1",
            parseListMember(JSONObject("""{"id":"m","user":{"id":"u1","username":"alex","host":null}}"""))?.user?.id,
        )
    }

    @Test
    fun whatYouTypeDecidesWhereSearchGoes() {
        assertEquals(SearchIntent.Tag("linux"), searchIntent(" #linux ", signedIn = false))
        assertEquals(SearchIntent.Person("alex", null), searchIntent("@alex", signedIn = false))
        assertEquals(SearchIntent.Person("alex", "example.social"), searchIntent("@alex@example.social", signedIn = false))
        assertEquals(SearchIntent.Notes("two words"), searchIntent("two words", signedIn = true))
        assertEquals(SearchIntent.SignInNeeded, searchIntent("two words", signedIn = false))
        assertEquals(SearchIntent.Notes("#two words"), searchIntent("#two words", signedIn = true))
        assertNull(searchIntent("   ", signedIn = true))
    }

    @Test
    fun onlyThisInstancesTagLinksOpenInTheApp() {
        assertEquals("linux", parseTagLink("https://social.fort1nd.com/tags/linux"))
        assertEquals("日本語", parseTagLink("https://social.fort1nd.com/tags/%E6%97%A5%E6%9C%AC%E8%AA%9E"))
        assertNull(parseTagLink("https://example.social/tags/linux"))
        assertNull(parseTagLink("https://social.fort1nd.com/notes/linux"))
    }
}

package com.fortind.zenith.people

import com.fortind.zenith.account.REPORT_MAX_CODE_POINTS
import com.fortind.zenith.account.reportNotePrefix
import com.fortind.zenith.account.reportTextLimit
import com.fortind.zenith.timeline.testUser
import com.fortind.zenith.ui.nav.AppRoute
import com.fortind.zenith.ui.people.ProfileHandle
import com.fortind.zenith.ui.people.parseProfileLink
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDetailTest {

    private fun detail(
        followers: ListVisibility = ListVisibility.Public,
        following: ListVisibility = ListVisibility.Public,
        isFollowing: Boolean = false,
        id: String = "them",
    ) = UserDetail(user = testUser(id), followersVisibility = followers, followingVisibility = following, isFollowing = isFollowing)

    @Test
    fun parsesTheFieldsAProfileShows() {
        val json = JSONObject(
            """
            {"id":"u","username":"alice","host":"remote.example","name":"Alice","description":"hi",
             "bannerUrl":"https://b","location":"Town","birthday":"2000-03-04","createdAt":"2024-01-02T03:04:05.000Z",
             "notesCount":3,"followingCount":4,"followersCount":5,
             "followingVisibility":"followers","followersVisibility":"private",
             "isFollowing":true,"isFollowed":true,"isLocked":true,"movedTo":"https://elsewhere",
             "isMuted":true,"isRenoteMuted":true,"isBlocking":true,"isBlocked":true,
             "hasPendingFollowRequestFromYou":true,
             "fields":[{"name":"site","value":"https://a"},{"name":null,"value":null}],
             "roles":[{"name":"Mod","color":"#ff0000","iconUrl":null,"description":"d"},{"name":"  "}],
             "pinnedNotes":[]}
            """.trimIndent(),
        )

        val parsed = requireNotNull(parseUserDetail(json))

        assertEquals("alice", parsed.user.username)
        assertEquals("@alice@remote.example", parsed.user.fullHandle)
        assertEquals("https://b", parsed.bannerUrl)
        assertEquals("Town", parsed.location)
        assertEquals(3, parsed.notesCount)
        assertEquals(ListVisibility.Followers, parsed.followingVisibility)
        assertEquals(ListVisibility.Private, parsed.followersVisibility)
        assertTrue(parsed.isFollowing && parsed.isFollowed && parsed.isLocked && parsed.hasMoved)
        assertTrue(parsed.isMuted && parsed.isRenoteMuted && parsed.isBlocking && parsed.isBlocked)
        assertTrue(parsed.hasPendingRequestFromYou)
        assertEquals(listOf("site"), parsed.fields.map { (it.nameMfm.single() as com.fortind.zenith.mfm.MfmText).text })
        assertEquals(listOf("Mod"), parsed.roles.map { it.name })
        assertTrue(parsed.createdAtMillis > 0L)
    }

    @Test
    fun aMissingRelationshipReadsAsNone() {
        val parsed = requireNotNull(parseUserDetail(JSONObject("""{"id":"u","username":"a","movedTo":null}""")))

        assertFalse(parsed.isFollowing)
        assertFalse(parsed.hasMoved)
        assertEquals(ListVisibility.Unspecified, parsed.followersVisibility)
    }

    @Test
    fun aUserWithoutAnIdIsDropped() {
        assertNull(parseUserDetail(JSONObject("""{"username":"a"}""")))
    }

    @Test
    fun hiddenCountsDecideFromVisibilityNotFromZero() {
        assertTrue(detail(followers = ListVisibility.Public).showsFollowersCount("me"))
        assertTrue(detail(followers = ListVisibility.Unspecified).showsFollowersCount("me"))
        assertFalse(detail(followers = ListVisibility.Private).showsFollowersCount("me"))
        assertFalse(detail(followers = ListVisibility.Followers).showsFollowersCount("me"))
        assertTrue(detail(followers = ListVisibility.Followers, isFollowing = true).showsFollowersCount("me"))
        assertFalse(detail(following = ListVisibility.Private, isFollowing = true).showsFollowingCount("me"))
        assertTrue(detail(followers = ListVisibility.Private, id = "me").showsFollowersCount("me"))
        assertTrue(detail(following = ListVisibility.Private, id = "me").showsFollowingCount("me"))
    }

    @Test
    fun followStatesFollowTheRelationship() {
        val base = detail()
        assertEquals(FollowState.Follow, base.followState("me"))
        assertEquals(FollowState.FollowBack, base.copy(isFollowed = true).followState("me"))
        assertEquals(FollowState.RequestToFollow, base.copy(isLocked = true).followState("me"))
        assertEquals(FollowState.RequestToFollow, base.copy(isLocked = true, isFollowed = true).followState("me"))
        assertEquals(FollowState.Following, base.copy(isFollowing = true).followState("me"))
        assertEquals(FollowState.Requested, base.copy(hasPendingRequestFromYou = true).followState("me"))
        assertEquals(FollowState.Hidden, base.followState("them"))
        assertEquals(FollowState.Hidden, base.copy(isSuspended = true).followState("me"))
        assertEquals(FollowState.Hidden, base.copy(hasMoved = true).followState("me"))
        assertEquals(FollowState.Follow, base.followState(null))
    }

    @Test
    fun onlyUndoingAFollowAsksFirst() {
        assertTrue(FollowState.Following.asksFirst)
        assertTrue(FollowState.Requested.asksFirst)
        assertFalse(FollowState.Follow.asksFirst)
        assertTrue(FollowState.FollowBack.invitesFollow)
        assertFalse(FollowState.Requested.invitesFollow)
    }

    @Test
    fun aToggleMovesToTheExpectedState() {
        val base = detail()
        assertEquals(FollowState.Following, base.afterToggle(FollowState.Follow).followState("me"))
        assertEquals(FollowState.Follow, base.copy(isFollowing = true).afterToggle(FollowState.Following).followState("me"))
        val locked = base.copy(isLocked = true)
        assertEquals(FollowState.Requested, locked.afterToggle(FollowState.RequestToFollow).followState("me"))
        assertEquals(FollowState.RequestToFollow, locked.copy(hasPendingRequestFromYou = true).afterToggle(FollowState.Requested).followState("me"))
    }

    @Test
    fun reportCommentMatchesTheWebClientsPrefix() {
        assertEquals("Note: https://remote/n\n-----\n", reportNotePrefix("https://local/n", "https://remote/n"))
        assertEquals("Local Note: https://local/n\n-----\n", reportNotePrefix("https://local/n", null))
        assertEquals(REPORT_MAX_CODE_POINTS, reportTextLimit(""))
        assertEquals(REPORT_MAX_CODE_POINTS - 3, reportTextLimit("a😀b"))
    }

    @Test
    fun profileLinksOnlyMatchThisInstance() {
        assertEquals(ProfileHandle("alice", null), parseProfileLink("https://social.fort1nd.com/@alice"))
        assertEquals(ProfileHandle("alice", null), parseProfileLink("https://social.fort1nd.com/@alice@social.fort1nd.com"))
        assertEquals(ProfileHandle("alice", "remote.example"), parseProfileLink("https://social.fort1nd.com/@alice@remote.example"))
        assertEquals(ProfileHandle("alice", null), parseProfileLink("https://SOCIAL.fort1nd.com/@alice/"))
        assertNull(parseProfileLink("https://elsewhere.example/@alice"))
        assertNull(parseProfileLink("https://social.fort1nd.com/notes/abc"))
        assertNull(parseProfileLink("https://social.fort1nd.com/@alice/followers"))
        assertNull(parseProfileLink("https://social.fort1nd.com/@ali ce"))
    }

    @Test
    fun peopleRoutesSurviveEncoding() {
        val routes = listOf(
            AppRoute.Person("abc123"),
            AppRoute.PersonHandle("alice", null),
            AppRoute.PersonHandle("alice", "remote.example"),
            AppRoute.FollowList("abc123", FollowListKind.Requests),
        )
        routes.forEach { assertEquals(it, AppRoute.decode(it.encode())) }
    }

    @Test
    fun badPeopleRoutesAreDropped() {
        assertNull(AppRoute.decode("person:../etc"))
        assertNull(AppRoute.decode("handle:no host separator"))
        assertNull(AppRoute.decode("follows:Nope:abc"))
        assertNull(AppRoute.decode("follows:Following:a/b"))
        assertNull(AppRoute.decode("bookmarks"))
        assertNotNull(AppRoute.decode("follows:Following:abc"))
    }
}

package com.fortind.zenith.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.autocomplete.SuggestionsApi
import com.fortind.zenith.discovery.Announcement
import com.fortind.zenith.discovery.AnnouncementKind
import com.fortind.zenith.discovery.AntennaInfo
import com.fortind.zenith.discovery.CollectionsApi
import com.fortind.zenith.discovery.DiscoveryApi
import com.fortind.zenith.discovery.Hashtag
import com.fortind.zenith.discovery.InMemoryDismissedAnnouncementsStore
import com.fortind.zenith.discovery.ListMember
import com.fortind.zenith.discovery.UserListInfo
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import com.fortind.zenith.notifications.awaitTrue
import com.fortind.zenith.timeline.CollectionKind
import com.fortind.zenith.timeline.NoteCollection
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.testUser
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelsTest {
    private val models = ArrayList<ViewModel>()

    private class FakeCollections : CollectionsApi {
        var lists = listOf(UserListInfo("l1", "Friends", listOf("u1")), UserListInfo("l2", "Devs", emptyList()))
        var antennas = listOf(AntennaInfo("a1", "Mentions", true))
        var failure: IOException? = null
        val calls = ArrayList<String>()
        var members = listOf(ListMember("m1", testUser("u1")))

        private fun check(call: String) {
            calls += call
            failure?.let { throw it }
        }

        override suspend fun lists(token: String) = lists.also { check("lists") }

        override suspend fun antennas(token: String) = antennas.also { check("antennas") }

        override suspend fun createList(token: String, name: String): UserListInfo {
            check("create:$name")
            return UserListInfo("new", name, emptyList())
        }

        override suspend fun renameList(token: String, listId: String, name: String) = check("rename:$listId:$name")

        override suspend fun deleteList(token: String, listId: String) = check("delete:$listId")

        override suspend fun addToList(token: String, listId: String, userId: String) = check("push:$listId:$userId")

        override suspend fun removeFromList(token: String, listId: String, userId: String) = check("pull:$listId:$userId")

        override suspend fun members(token: String, listId: String, untilId: String?, limit: Int): List<ListMember> {
            check("members:$untilId")
            return if (untilId == null) members else emptyList()
        }
    }

    private class FakePeople : SuggestionsApi {
        var users = listOf(testUser("u1"), testUser("u9"))
        val queries = ArrayList<String>()

        override suspend fun users(token: String?, query: String, limit: Int): List<NoteUser> {
            queries += "user:$query"
            return users
        }

        override suspend fun frequentUsers(token: String, userId: String, limit: Int) = emptyList<NoteUser>()

        override suspend fun hashtags(token: String?, query: String, limit: Int): List<String> {
            queries += "tag:$query"
            return listOf("linux")
        }
    }

    private class FakeDiscovery : DiscoveryApi {
        var hashtagCalls = 0
        var hashtagFailure: IOException? = null
        var announcementCalls = 0
        var announcements = listOf(Announcement("a", "Hi", emptyList(), AnnouncementKind.Info, isRead = false))
        val read = ArrayList<String>()
        var readFailure: IOException? = null

        override suspend fun popularHashtags(): List<Hashtag> {
            hashtagCalls++
            hashtagFailure?.let { throw it }
            return listOf(Hashtag("linux", 3))
        }

        override suspend fun announcements(token: String?): List<Announcement> {
            announcementCalls++
            return announcements
        }

        override suspend fun markAnnouncementRead(token: String, id: String) {
            read += id
            readFailure?.let { throw it }
        }
    }

    private fun session(): UserSessionService {
        val session = UserSessionService(FakeTokenStore("token"), FakeUserApi())
        runBlocking { session.restore() }
        return session
    }

    private fun collections(api: FakeCollections = FakeCollections(), people: FakePeople = FakePeople()) =
        CollectionsViewModel(session(), api, people, searchDebounceMs = 0).also { models += it }

    private var now = 1_000_000L

    private fun discovery(
        api: FakeDiscovery = FakeDiscovery(),
        people: FakePeople = FakePeople(),
        store: InMemoryDismissedAnnouncementsStore = InMemoryDismissedAnnouncementsStore(),
    ) = DiscoveryViewModel(session(), api, people, store, clock = { now }, suggestDebounceMs = 0).also { models += it }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        runBlocking { models.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private suspend fun CollectionsViewModel.loaded(): CollectionsUiState {
        load()
        awaitTrue { uiState.value.status == CollectionsStatus.Ready }
        return uiState.value
    }

    @Test
    fun loadingPicksTheFirstListAndTheAntennaTabIsSeparate() = runTest {
        val model = collections()

        val state = model.loaded()

        assertEquals(NoteCollection(CollectionKind.UserList, "l1"), state.selected)
        model.selectTab(CollectionTab.Antennas)
        assertEquals(NoteCollection(CollectionKind.Antenna, "a1"), model.uiState.value.selected)
    }

    @Test
    fun openingAnAntennaClearsItsDotButLoadingAloneDoesNot() = runTest {
        val model = collections()

        assertTrue(model.loaded().antennas.single().hasUnread)

        model.selectTab(CollectionTab.Antennas)
        assertFalse(model.uiState.value.antennas.single().hasUnread)
    }

    @Test
    fun aListThatWentAwayFallsBackToTheFirst() = runTest {
        val api = FakeCollections()
        val model = collections(api)
        model.loaded()
        model.selectList("l2")

        api.lists = listOf(UserListInfo("l1", "Friends", emptyList()))
        model.load()

        awaitTrue { model.uiState.value.lists.size == 1 }
        assertEquals("l1", model.uiState.value.listId)
    }

    @Test
    fun creatingSelectsAndDeletingReselects() = runTest {
        val api = FakeCollections()
        val model = collections(api)
        model.loaded()

        model.createList("  New one ")
        assertEquals(CollectionsEvent.Created("New one"), model.events.first())
        assertEquals("new", model.uiState.value.listId)

        model.deleteList("new")
        assertEquals(CollectionsEvent.Deleted("New one"), model.events.first())
        assertEquals("l1", model.uiState.value.listId)
        assertTrue("delete:new" in api.calls)
    }

    @Test
    fun aBlankNameSendsNothing() = runTest {
        val api = FakeCollections()
        val model = collections(api)
        model.loaded()

        model.createList("   ")
        model.renameList("l1", " ")

        assertFalse(api.calls.any { it.startsWith("create") || it.startsWith("rename") })
    }

    @Test
    fun aMembershipTapAppliesAtOnceAndFlipsBackOnFailure() = runTest {
        val api = FakeCollections()
        val model = collections(api)
        model.loaded()

        model.setMembership("l2", "u5", member = true)
        awaitTrue { model.uiState.value.membershipBusy.isEmpty() }
        assertTrue("push:l2:u5" in api.calls)
        assertEquals(listOf("u5"), model.uiState.value.lists.first { it.id == "l2" }.userIds)

        api.failure = HttpStatusException(400, ApiErrorCodes.TOO_MANY_USERS)
        model.setMembership("l2", "u6", member = true)

        assertEquals(CollectionsEvent.Failed(CollectionFailure.TooManyUsers), model.events.first())
        awaitTrue { model.uiState.value.membershipBusy.isEmpty() }
        assertEquals(listOf("u5"), model.uiState.value.lists.first { it.id == "l2" }.userIds)
    }

    @Test
    fun failuresNameTheirCause() = runTest {
        val api = FakeCollections()
        val model = collections(api)
        model.loaded()

        api.failure = HttpStatusException(400, ApiErrorCodes.TOO_MANY_USERLISTS)
        model.createList("x")
        assertEquals(CollectionsEvent.Failed(CollectionFailure.TooManyLists), model.events.first())

        api.failure = HttpStatusException(400, ApiErrorCodes.YOU_HAVE_BEEN_BLOCKED)
        model.setMembership("l1", "u2", member = true)
        assertEquals(CollectionsEvent.Failed(CollectionFailure.Blocked), model.events.first())
    }

    @Test
    fun aMissingScopeAsksToReauthorize() = runTest {
        val api = FakeCollections()
        api.failure = MissingPermissionException()
        val model = collections(api)

        model.load()

        awaitTrue { model.uiState.value.needsPermission }
        assertEquals(CollectionsStatus.Failed, model.uiState.value.status)
    }

    @Test
    fun membersLoadAndSearchLeavesOutPeopleAlreadyOnTheList() = runTest {
        val model = collections()
        model.loaded()
        model.openMembers("l1")
        awaitTrue { model.uiState.value.members?.status == CollectionsStatus.Ready }

        model.searchPeople("@al")

        awaitTrue { model.uiState.value.members?.results?.isNotEmpty() == true }
        assertEquals(listOf("u9"), model.uiState.value.members?.results?.map { it.id })

        model.closeMembers()
        assertNull(model.uiState.value.members)
    }

    @Test
    fun addingAMemberReReadsTheList() = runTest {
        val api = FakeCollections()
        val model = collections(api)
        model.loaded()
        model.openMembers("l2")
        awaitTrue { model.uiState.value.members?.status == CollectionsStatus.Ready }
        api.members = listOf(ListMember("m1", testUser("u1")), ListMember("m2", testUser("u9")))

        model.addMember(testUser("u9"))

        awaitTrue { model.uiState.value.members?.members?.size == 2 }
        assertTrue("push:l2:u9" in api.calls)
        assertEquals(listOf("u9"), model.uiState.value.lists.first { it.id == "l2" }.userIds)
    }

    @Test
    fun hashtagsAreCachedOnlyWhenTheySucceed() = runTest {
        val api = FakeDiscovery()
        api.hashtagFailure = IOException("offline")
        val model = discovery(api)

        model.loadHashtags()
        awaitTrue { model.uiState.value.hashtagStatus == CollectionsStatus.Failed }

        api.hashtagFailure = null
        model.loadHashtags()
        awaitTrue { model.uiState.value.hashtagStatus == CollectionsStatus.Ready }
        assertEquals(2, api.hashtagCalls)

        model.loadHashtags()
        assertEquals(2, api.hashtagCalls)

        now += DiscoveryViewModel.HASHTAG_TTL_MS
        model.loadHashtags()
        awaitTrue { api.hashtagCalls == 3 }
    }

    @Test
    fun dismissingHidesFirstEvenIfTheServerCallFails() = runTest {
        val api = FakeDiscovery()
        api.readFailure = IOException("offline")
        val store = InMemoryDismissedAnnouncementsStore()
        val model = discovery(api, store = store)
        model.loadAnnouncements()
        awaitTrue { model.uiState.value.announcements.size == 1 }

        model.dismissAnnouncement("a")

        awaitTrue { model.uiState.value.announcements.isEmpty() }
        awaitTrue { api.read == listOf("a") }
        assertEquals(listOf("a"), store.dismissed.first())
    }

    @Test
    fun announcementsDoNotRefetchWithinTheirWindow() = runTest {
        val api = FakeDiscovery()
        val model = discovery(api)

        model.loadAnnouncements()
        awaitTrue { api.announcementCalls == 1 }
        model.loadAnnouncements()
        model.loadAnnouncements(force = true)
        assertEquals(1, api.announcementCalls)

        now += DiscoveryViewModel.ANNOUNCEMENT_FORCE_MIN_MS
        model.loadAnnouncements(force = true)
        awaitTrue { api.announcementCalls == 2 }
    }

    @Test
    fun suggestionsFollowTheTypedPrefix() = runTest {
        val people = FakePeople()
        val model = discovery(people = people)

        model.setQuery("#lin")
        awaitTrue { model.uiState.value.suggestions.query == "#lin" && model.uiState.value.suggestions.tags.isNotEmpty() }
        assertEquals(listOf("tag:lin"), people.queries)

        people.queries.clear()
        model.setQuery("@al")
        awaitTrue { model.uiState.value.suggestions.query == "@al" && model.uiState.value.suggestions.people.isNotEmpty() }
        assertEquals(listOf("user:al"), people.queries)

        people.queries.clear()
        model.setQuery("a")
        awaitTrue { model.uiState.value.suggestions.query == "a" }
        assertTrue(people.queries.isEmpty())
    }
}

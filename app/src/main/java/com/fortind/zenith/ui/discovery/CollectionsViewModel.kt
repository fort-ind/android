package com.fortind.zenith.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.autocomplete.SuggestionsApi
import com.fortind.zenith.discovery.AntennaInfo
import com.fortind.zenith.discovery.CollectionsApi
import com.fortind.zenith.discovery.ListMember
import com.fortind.zenith.discovery.UserListInfo
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.net.isRateLimited
import com.fortind.zenith.timeline.CollectionKind
import com.fortind.zenith.timeline.NoteCollection
import com.fortind.zenith.timeline.NoteUser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CollectionTab { Lists, Antennas }

enum class CollectionsStatus { Idle, Loading, Ready, Failed }

enum class CollectionFailure { Offline, RateLimited, TooManyLists, TooManyUsers, Blocked }

sealed interface CollectionsEvent {
    data class Failed(val failure: CollectionFailure) : CollectionsEvent

    data class Created(val name: String) : CollectionsEvent

    data class Deleted(val name: String) : CollectionsEvent
}

data class MembersState(
    val listId: String,
    val status: CollectionsStatus = CollectionsStatus.Loading,
    val members: List<ListMember> = emptyList(),
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val busy: Set<String> = emptySet(),
    val results: List<NoteUser> = emptyList(),
)

data class CollectionsUiState(
    val status: CollectionsStatus = CollectionsStatus.Idle,
    val lists: List<UserListInfo> = emptyList(),
    val antennas: List<AntennaInfo> = emptyList(),
    val tab: CollectionTab = CollectionTab.Lists,
    val listId: String? = null,
    val antennaId: String? = null,
    val needsPermission: Boolean = false,
    val membershipBusy: Set<String> = emptySet(),
    val members: MembersState? = null,
) {
    val selected: NoteCollection?
        get() = when (tab) {
            CollectionTab.Lists -> listId?.let { NoteCollection(CollectionKind.UserList, it) }
            CollectionTab.Antennas -> antennaId?.let { NoteCollection(CollectionKind.Antenna, it) }
        }

    val selectedList: UserListInfo? get() = lists.firstOrNull { it.id == listId }

    val selectedAntenna: AntennaInfo? get() = antennas.firstOrNull { it.id == antennaId }

    val tabIsEmpty: Boolean
        get() = status == CollectionsStatus.Ready && when (tab) {
            CollectionTab.Lists -> lists.isEmpty()
            CollectionTab.Antennas -> antennas.isEmpty()
        }
}

class CollectionsViewModel(
    private val session: UserSessionService,
    private val api: CollectionsApi,
    private val people: SuggestionsApi,
    private val searchDebounceMs: Long = SEARCH_DEBOUNCE_MS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    private val _events = Channel<CollectionsEvent>(Channel.BUFFERED)
    val events: Flow<CollectionsEvent> = _events.receiveAsFlow()

    private var peopleSearch: Job? = null
    private var membersJob: Job? = null

    init {
        viewModelScope.launch {
            session.credentialsVersion.drop(1).collect {
                membersJob?.cancel()
                peopleSearch?.cancel()
                _uiState.value = CollectionsUiState()
            }
        }
    }

    fun load() {
        if (_uiState.value.status == CollectionsStatus.Loading) return
        _uiState.update { it.copy(status = if (it.status == CollectionsStatus.Ready) it.status else CollectionsStatus.Loading) }
        viewModelScope.launch {
            try {
                val token = session.token() ?: return@launch _uiState.update { it.copy(status = CollectionsStatus.Idle) }
                val (lists, antennas) = coroutineScope {
                    val lists = async { api.lists(token) }
                    val antennas = async { api.antennas(token) }
                    lists.await() to antennas.await()
                }
                _uiState.update { state ->
                    val listId = state.listId?.takeIf { id -> lists.any { it.id == id } } ?: lists.firstOrNull()?.id
                    val antennaId = state.antennaId?.takeIf { id -> antennas.any { it.id == id } } ?: antennas.firstOrNull()?.id
                    val shown = antennaId.takeIf { state.tab == CollectionTab.Antennas }
                    state.copy(
                        status = CollectionsStatus.Ready,
                        lists = lists,
                        antennas = antennas.map { if (it.id == shown) it.copy(hasUnread = false) else it },
                        listId = listId,
                        antennaId = antennaId,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(status = if (it.status == CollectionsStatus.Ready) it.status else CollectionsStatus.Failed) }
                handleAuth(e)
            }
        }
    }

    fun permissionPromptHandled() {
        _uiState.update { it.copy(needsPermission = false) }
    }

    fun selectTab(tab: CollectionTab) {
        _uiState.update { state ->
            state.copy(
                tab = tab,
                antennas = if (tab == CollectionTab.Antennas) state.antennas.markedRead(state.antennaId) else state.antennas,
            )
        }
    }

    fun selectList(listId: String) {
        _uiState.update { it.copy(listId = listId) }
    }

    fun selectAntenna(antennaId: String) {
        _uiState.update { it.copy(antennaId = antennaId, antennas = it.antennas.markedRead(antennaId)) }
    }

    private fun List<AntennaInfo>.markedRead(id: String?) = map { if (it.id == id) it.copy(hasUnread = false) else it }

    fun createList(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        write {token ->
            val created = api.createList(token, clean)
            _uiState.update { it.copy(lists = it.lists + created, listId = created.id, tab = CollectionTab.Lists) }
            _events.trySend(CollectionsEvent.Created(created.name))
        }
    }

    fun renameList(listId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        write { token ->
            api.renameList(token, listId, clean)
            _uiState.update { state ->
                state.copy(lists = state.lists.map { if (it.id == listId) it.copy(name = clean) else it })
            }
        }
    }

    fun deleteList(listId: String) {
        write { token ->
            val name = _uiState.value.lists.firstOrNull { it.id == listId }?.name.orEmpty()
            api.deleteList(token, listId)
            _uiState.update { state ->
                val remaining = state.lists.filterNot { it.id == listId }
                state.copy(lists = remaining, listId = state.listId.takeIf { it != listId } ?: remaining.firstOrNull()?.id)
            }
            _events.trySend(CollectionsEvent.Deleted(name))
        }
    }

    fun setMembership(listId: String, userId: String, member: Boolean) {
        if (listId in _uiState.value.membershipBusy) return
        _uiState.update { state ->
            state.copy(membershipBusy = state.membershipBusy + listId, lists = state.lists.withMember(listId, userId, member))
        }
        viewModelScope.launch {
            try {
                val token = session.token() ?: throw TokenRejectedException(0)
                if (member) api.addToList(token, listId, userId) else api.removeFromList(token, listId, userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(lists = it.lists.withMember(listId, userId, !member)) }
                report(e)
            } finally {
                _uiState.update { it.copy(membershipBusy = it.membershipBusy - listId) }
            }
        }
    }

    private fun List<UserListInfo>.withMember(listId: String, userId: String, member: Boolean) = map { list ->
        if (list.id != listId) {
            list
        } else {
            list.copy(userIds = if (member) (list.userIds + userId).distinct() else list.userIds - userId)
        }
    }

    fun openMembers(listId: String) {
        membersJob?.cancel()
        _uiState.update { it.copy(members = MembersState(listId)) }
        membersJob = viewModelScope.launch { loadMembers(listId, null) }
    }

    fun closeMembers() {
        membersJob?.cancel()
        peopleSearch?.cancel()
        _uiState.update { it.copy(members = null) }
    }

    fun loadMoreMembers() {
        val members = _uiState.value.members ?: return
        val cursor = members.members.lastOrNull()?.membershipId ?: return
        if (members.loadingMore || members.endReached || members.status != CollectionsStatus.Ready) return
        updateMembers { it.copy(loadingMore = true) }
        membersJob = viewModelScope.launch { loadMembers(members.listId, cursor) }
    }

    private suspend fun loadMembers(listId: String, cursor: String?) {
        try {
            val token = session.token() ?: throw TokenRejectedException(0)
            val page = api.members(token, listId, cursor, MEMBERS_PAGE_SIZE)
            updateMembers { state ->
                val known = state.members.mapTo(HashSet()) { it.user.id }
                state.copy(
                    status = CollectionsStatus.Ready,
                    members = state.members + page.filter { known.add(it.user.id) },
                    loadingMore = false,
                    endReached = page.isEmpty(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            updateMembers { it.copy(status = if (it.members.isEmpty()) CollectionsStatus.Failed else CollectionsStatus.Ready, loadingMore = false) }
            handleAuth(e)
        }
    }

    fun addMember(user: NoteUser) = changeMember(user.id) { token, listId ->
        api.addToList(token, listId, user.id)
        _uiState.update { state -> state.copy(lists = state.lists.withMember(listId, user.id, true)) }
        updateMembers { it.copy(members = emptyList(), endReached = false, results = it.results.filterNot { found -> found.id == user.id }) }
        loadMembers(listId, null)
    }

    fun removeMember(user: NoteUser) = changeMember(user.id) { token, listId ->
        api.removeFromList(token, listId, user.id)
        _uiState.update { state -> state.copy(lists = state.lists.withMember(listId, user.id, false)) }
        updateMembers { state -> state.copy(members = state.members.filterNot { it.user.id == user.id }) }
    }

    private fun changeMember(userId: String, action: suspend (String, String) -> Unit) {
        val members = _uiState.value.members ?: return
        if (userId in members.busy) return
        updateMembers { it.copy(busy = it.busy + userId) }
        viewModelScope.launch {
            try {
                val token = session.token() ?: throw TokenRejectedException(0)
                action(token, members.listId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                report(e)
            } finally {
                updateMembers { it.copy(busy = it.busy - userId) }
            }
        }
    }

    fun searchPeople(query: String) {
        peopleSearch?.cancel()
        val clean = query.trim().removePrefix("@")
        if (clean.length < MIN_PEOPLE_QUERY) {
            updateMembers { it.copy(results = emptyList()) }
            return
        }
        peopleSearch = viewModelScope.launch {
            delay(searchDebounceMs)
            val found = try {
                people.users(session.token(), clean, PEOPLE_RESULTS)
            } catch (_: IOException) {
                emptyList()
            }
            updateMembers { state ->
                val existing = state.members.mapTo(HashSet()) { it.user.id }
                state.copy(results = found.filterNot { it.id in existing })
            }
        }
    }

    private fun updateMembers(transform: (MembersState) -> MembersState) {
        _uiState.update { state -> state.copy(members = state.members?.let(transform)) }
    }

    private fun write(action: suspend (String) -> Unit) {
        viewModelScope.launch {
            try {
                action(session.token() ?: throw TokenRejectedException(0))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                report(e)
            }
        }
    }

    private fun report(error: IOException) {
        if (!handleAuth(error)) _events.trySend(CollectionsEvent.Failed(error.toFailure()))
    }

    private fun handleAuth(error: IOException): Boolean = when (error) {
        is MissingPermissionException -> {
            _uiState.update { it.copy(needsPermission = true) }
            true
        }
        is TokenRejectedException -> {
            viewModelScope.launch { session.signOut() }
            true
        }
        else -> false
    }

    private fun IOException.toFailure(): CollectionFailure {
        val code = (this as? HttpStatusException)?.apiErrorCode
        return when {
            isRateLimited -> CollectionFailure.RateLimited
            code == ApiErrorCodes.TOO_MANY_USERLISTS -> CollectionFailure.TooManyLists
            code == ApiErrorCodes.TOO_MANY_USERS -> CollectionFailure.TooManyUsers
            code == ApiErrorCodes.BLOCKED || code == ApiErrorCodes.YOU_HAVE_BEEN_BLOCKED -> CollectionFailure.Blocked
            else -> CollectionFailure.Offline
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MEMBERS_PAGE_SIZE = 30
        const val PEOPLE_RESULTS = 8
        const val MIN_PEOPLE_QUERY = 2

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                CollectionsViewModel(
                    session = app.container.sessionService,
                    api = app.container.collectionsApi,
                    people = app.container.suggestionsApi,
                )
            }
        }
    }
}

package com.fortind.zenith.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.account.ModerationApi
import com.fortind.zenith.account.ReportApi
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.net.isRateLimited
import com.fortind.zenith.people.FollowApi
import com.fortind.zenith.people.FollowListKind
import com.fortind.zenith.people.FollowState
import com.fortind.zenith.people.RequestAnswerResult
import com.fortind.zenith.people.UserDetail
import com.fortind.zenith.people.UserListRow
import com.fortind.zenith.people.UsersApi
import com.fortind.zenith.people.afterToggle
import com.fortind.zenith.people.followState
import com.fortind.zenith.timeline.NoteUser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PersonStatus { Idle, Loading, Ready, Failed, NotFound }

data class PersonState(
    val seed: NoteUser? = null,
    val detail: UserDetail? = null,
    val status: PersonStatus = PersonStatus.Idle,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val loadedAtMillis: Long = 0L,
) {
    val user: NoteUser? get() = detail?.user ?: seed
}

enum class UserListStatus { Idle, Loading, Ready, Failed, Hidden, NeedsPermission }

data class UserListState(
    val rows: List<UserListRow> = emptyList(),
    val status: UserListStatus = UserListStatus.Idle,
    val cursor: String? = null,
    val endReached: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
)

data class FollowListKey(val userId: String, val kind: FollowListKind)

data class PendingRequests(val count: Int, val partial: Boolean)

enum class RequestAnswer { Accepting, Rejecting, Accepted, Rejected, Gone }

data class PeopleUiState(
    val myUserId: String? = null,
    val signedIn: Boolean = false,
    val people: Map<String, PersonState> = emptyMap(),
    val handles: Map<String, String> = emptyMap(),
    val failedHandles: Set<String> = emptySet(),
    val lists: Map<FollowListKey, UserListState> = emptyMap(),
    val pending: PendingRequests? = null,
    val answers: Map<String, RequestAnswer> = emptyMap(),
) {
    fun person(userId: String): PersonState = people[userId] ?: PersonState()

    fun list(key: FollowListKey): UserListState = lists[key] ?: UserListState()

    fun detailOf(userId: String): UserDetail? =
        person(userId).detail ?: lists.values.firstNotNullOfOrNull { list -> list.rows.firstOrNull { it.detail.id == userId }?.detail }
}

enum class FollowFailure { BlockedByThem, YouBlocked, RateLimited, Other }

enum class ModerationAction { Mute, Unmute, HideRenotes, ShowRenotes, Block, Unblock }

enum class ReportFailure { Yourself, Admin, RateLimited, Other }

sealed interface PeopleEvent {
    data object SignInRequired : PeopleEvent

    data object PermissionMissing : PeopleEvent

    data class FollowFailed(val name: String, val failure: FollowFailure) : PeopleEvent

    data class RequestFailed(val name: String, val accept: Boolean) : PeopleEvent

    data class Moderated(val userId: String, val name: String, val action: ModerationAction) : PeopleEvent

    data object ModerationFailed : PeopleEvent

    data object Reported : PeopleEvent

    data class ReportFailed(val failure: ReportFailure) : PeopleEvent
}

private sealed interface PeopleAuth {
    data object Pending : PeopleAuth

    data object Guest : PeopleAuth

    data class SignedIn(val token: String, val userId: String?) : PeopleAuth
}

class PeopleViewModel(
    private val session: UserSessionService,
    private val users: UsersApi,
    private val follows: FollowApi,
    private val moderation: ModerationApi,
    private val reports: ReportApi,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState: StateFlow<PeopleUiState> = _uiState.asStateFlow()

    private val _events = Channel<PeopleEvent>(Channel.BUFFERED)
    val events: Flow<PeopleEvent> = _events.receiveAsFlow()

    private var auth: PeopleAuth = PeopleAuth.Pending
    private val personJobs = HashMap<String, Job>()
    private val listJobs = HashMap<FollowListKey, Job>()

    private val token: String? get() = (auth as? PeopleAuth.SignedIn)?.token

    private val myUserId: String? get() = (auth as? PeopleAuth.SignedIn)?.userId

    init {
        viewModelScope.launch {
            combine(session.state, session.credentialsVersion) { state, _ -> state }
                .map { state ->
                    when (state) {
                        is SessionState.SignedIn -> session.token()?.let { PeopleAuth.SignedIn(it, state.user?.id) } ?: PeopleAuth.Pending
                        SessionState.Guest -> PeopleAuth.Guest
                        SessionState.Restoring, SessionState.SignedOut -> PeopleAuth.Pending
                    }
                }
                .distinctUntilChanged()
                .collectLatest { next -> reset(next) }
        }
    }

    private fun reset(next: PeopleAuth) {
        auth = next
        personJobs.values.forEach { it.cancel() }
        personJobs.clear()
        listJobs.values.forEach { it.cancel() }
        listJobs.clear()
        _uiState.value = PeopleUiState(
            myUserId = (next as? PeopleAuth.SignedIn)?.userId,
            signedIn = next is PeopleAuth.SignedIn,
        )
    }

    fun seed(user: NoteUser) {
        _uiState.update { state ->
            val current = state.person(user.id)
            if (current.detail != null || current.seed != null) state else state.withPerson(user.id, current.copy(seed = user))
        }
    }

    fun open(userId: String) {
        val current = _uiState.value.person(userId)
        val fresh = current.detail != null && clock() - current.loadedAtMillis < STALE_AFTER_MS
        if (fresh || personJobs[userId]?.isActive == true) return
        load(userId)
    }

    fun refresh(userId: String) {
        if (personJobs[userId]?.isActive == true) return
        load(userId)
    }

    private fun load(userId: String) {
        updatePerson(userId) { person ->
            if (person.detail != null) person.copy(refreshing = true) else person.copy(status = PersonStatus.Loading)
        }
        personJobs[userId] = viewModelScope.launch {
            try {
                apply(users.show(token, userId))
            } catch (e: IOException) {
                handleFailure(e)
                updatePerson(userId) { person ->
                    person.copy(
                        refreshing = false,
                        status = when {
                            person.detail != null -> PersonStatus.Ready
                            e.isNoSuchUser -> PersonStatus.NotFound
                            else -> PersonStatus.Failed
                        },
                    )
                }
            }
        }
    }

    fun openHandle(username: String, host: String?) {
        val handle = handleKey(username, host)
        if (handle in _uiState.value.handles) return
        _uiState.update { it.copy(failedHandles = it.failedHandles - handle) }
        viewModelScope.launch {
            try {
                val detail = users.showByHandle(token, username, host)
                apply(detail)
                _uiState.update { it.copy(handles = it.handles + (handle to detail.id)) }
            } catch (e: IOException) {
                handleFailure(e)
                _uiState.update { it.copy(failedHandles = it.failedHandles + handle) }
            }
        }
    }

    fun toggleFollow(userId: String) {
        val token = token ?: return signInRequired()
        val person = _uiState.value.person(userId)
        val detail = _uiState.value.detailOf(userId) ?: return
        val state = detail.followState(myUserId)
        if (state == FollowState.Hidden || person.busy) return
        updatePerson(userId) { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                when (state) {
                    FollowState.Following -> follows.unfollow(token, userId)
                    FollowState.Requested -> follows.cancelRequest(token, userId)
                    else -> follows.follow(token, userId)
                }
                apply(detail.afterToggle(state))
                reread(userId)
                rereadMe()
            } catch (e: IOException) {
                handleFailure(e) {
                    _events.trySend(PeopleEvent.FollowFailed(detail.user.displayName, e.toFollowFailure()))
                }
                reread(userId)
            } finally {
                updatePerson(userId) { it.copy(busy = false) }
            }
        }
    }

    fun moderate(userId: String, action: ModerationAction, muteDurationMillis: Long? = null) {
        val token = token ?: return signInRequired()
        val person = _uiState.value.person(userId)
        val detail = _uiState.value.detailOf(userId) ?: return
        if (person.busy) return
        updatePerson(userId) { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                when (action) {
                    ModerationAction.Mute -> moderation.mute(token, userId, muteDurationMillis?.let { clock() + it })
                    ModerationAction.Unmute -> moderation.unmute(token, userId)
                    ModerationAction.HideRenotes -> moderation.hideRenotes(token, userId)
                    ModerationAction.ShowRenotes -> moderation.showRenotes(token, userId)
                    ModerationAction.Block -> moderation.block(token, userId)
                    ModerationAction.Unblock -> moderation.unblock(token, userId)
                }
                apply(detail.after(action))
                _events.trySend(PeopleEvent.Moderated(userId, detail.user.displayName, action))
                reread(userId)
                if (action == ModerationAction.Block || action == ModerationAction.Unblock) rereadMe()
            } catch (e: IOException) {
                handleFailure(e) { _events.trySend(PeopleEvent.ModerationFailed) }
            } finally {
                updatePerson(userId) { it.copy(busy = false) }
            }
        }
    }

    fun relationChanged(userId: String) {
        if (userId in _uiState.value.people) viewModelScope.launch { reread(userId) }
    }

    fun report(userId: String, comment: String) {
        val token = token ?: return signInRequired()
        viewModelScope.launch {
            try {
                reports.report(token, userId, comment)
                _events.trySend(PeopleEvent.Reported)
            } catch (e: IOException) {
                handleFailure(e) { _events.trySend(PeopleEvent.ReportFailed(e.toReportFailure())) }
            }
        }
    }

    fun loadList(key: FollowListKey) {
        val current = _uiState.value.list(key)
        if (listJobs[key]?.isActive == true) return
        updateList(key) { it.copy(status = if (current.rows.isEmpty()) UserListStatus.Loading else it.status, loadMoreFailed = false) }
        listJobs[key] = viewModelScope.launch {
            try {
                val rows = page(key, untilId = null)
                updateList(key) {
                    UserListState(
                        rows = rows,
                        status = UserListStatus.Ready,
                        cursor = rows.lastOrNull()?.pagingId,
                        endReached = rows.isEmpty(),
                    )
                }
                if (key.kind == FollowListKind.Requests) refreshPending()
            } catch (e: IOException) {
                handleFailure(e)
                updateList(key) {
                    it.copy(
                        status = when {
                            e is MissingPermissionException -> UserListStatus.NeedsPermission
                            e.isHiddenList -> UserListStatus.Hidden
                            it.rows.isEmpty() -> UserListStatus.Failed
                            else -> UserListStatus.Ready
                        },
                    )
                }
            }
        }
    }

    fun loadMore(key: FollowListKey) {
        val state = _uiState.value.list(key)
        val cursor = state.cursor ?: return
        if (state.status != UserListStatus.Ready || state.loadingMore || state.endReached) return
        updateList(key) { it.copy(loadingMore = true, loadMoreFailed = false) }
        listJobs[key] = viewModelScope.launch {
            try {
                val rows = page(key, untilId = cursor)
                val known = _uiState.value.list(key).rows.mapTo(HashSet()) { it.pagingId }
                updateList(key) {
                    it.copy(
                        rows = it.rows + rows.filterNot { row -> row.pagingId in known },
                        cursor = rows.lastOrNull()?.pagingId ?: cursor,
                        endReached = rows.isEmpty(),
                        loadingMore = false,
                    )
                }
            } catch (e: IOException) {
                handleFailure(e)
                updateList(key) { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    private suspend fun page(key: FollowListKey, untilId: String?): List<UserListRow> = when (key.kind) {
        FollowListKind.Requests -> users.followRequests(requireNotNull(token) { "Requests list needs a token" }, untilId, LIST_PAGE_SIZE)
        else -> users.follows(token, key.userId, key.kind, untilId, LIST_PAGE_SIZE)
    }

    fun refreshPending() {
        val token = token ?: return
        viewModelScope.launch {
            try {
                val rows = users.followRequests(token, untilId = null, limit = PENDING_SNAPSHOT_SIZE)
                _uiState.update { it.copy(pending = PendingRequests(rows.size, partial = rows.size >= PENDING_SNAPSHOT_SIZE)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TokenRejectedException) {
                session.signOut()
            } catch (_: IOException) {
                _uiState.update { it.copy(pending = null) }
            }
        }
    }

    fun answerRequest(userId: String, accept: Boolean) {
        val token = token ?: return signInRequired()
        val answers = _uiState.value.answers
        if (answers[userId] == RequestAnswer.Accepting || answers[userId] == RequestAnswer.Rejecting) return
        val name = _uiState.value.detailOf(userId)?.user?.displayName.orEmpty()
        _uiState.update { it.copy(answers = it.answers + (userId to if (accept) RequestAnswer.Accepting else RequestAnswer.Rejecting)) }
        viewModelScope.launch {
            try {
                val result = follows.answerRequest(token, userId, accept)
                val answer = when {
                    result == RequestAnswerResult.Gone -> RequestAnswer.Gone
                    accept -> RequestAnswer.Accepted
                    else -> RequestAnswer.Rejected
                }
                _uiState.update { state ->
                    state.copy(
                        answers = state.answers + (userId to answer),
                        pending = state.pending?.let { it.copy(count = (it.count - 1).coerceAtLeast(0)) },
                    )
                }
                if (accept) rereadMe()
            } catch (e: IOException) {
                _uiState.update { it.copy(answers = it.answers - userId) }
                handleFailure(e) { _events.trySend(PeopleEvent.RequestFailed(name, accept)) }
            }
        }
    }

    private suspend fun reread(userId: String) {
        try {
            apply(users.show(token, userId))
        } catch (e: IOException) {
            handleFailure(e)
        }
    }

    private suspend fun rereadMe() {
        val me = myUserId ?: return
        if (me in _uiState.value.people) reread(me)
    }

    private fun apply(detail: UserDetail) {
        _uiState.update { state ->
            val person = state.person(detail.id).copy(
                detail = detail,
                status = PersonStatus.Ready,
                refreshing = false,
                loadedAtMillis = clock(),
            )
            state.withPerson(detail.id, person).copy(
                lists = state.lists.mapValues { (_, list) ->
                    if (list.rows.none { it.detail.id == detail.id }) {
                        list
                    } else {
                        list.copy(rows = list.rows.map { row -> if (row.detail.id == detail.id) row.copy(detail = detail) else row })
                    }
                },
            )
        }
    }

    private fun updatePerson(userId: String, transform: (PersonState) -> PersonState) {
        _uiState.update { state -> state.withPerson(userId, transform(state.person(userId))) }
    }

    private fun updateList(key: FollowListKey, transform: (UserListState) -> UserListState) {
        _uiState.update { state ->
            val lists = state.lists - key + (key to transform(state.list(key)))
            state.copy(lists = if (lists.size > MAX_LISTS) lists - lists.keys.first() else lists)
        }
    }

    private fun PeopleUiState.withPerson(userId: String, person: PersonState): PeopleUiState {
        val updated = people - userId + (userId to person)
        val keepMe = myUserId
        val trimmed = if (updated.size > MAX_PEOPLE) {
            updated.keys.firstOrNull { it != keepMe && it != userId }?.let { updated - it } ?: updated
        } else {
            updated
        }
        return copy(people = trimmed)
    }

    private fun handleFailure(error: IOException, onOther: () -> Unit = {}) {
        when (error) {
            is MissingPermissionException -> _events.trySend(PeopleEvent.PermissionMissing)
            is TokenRejectedException -> viewModelScope.launch { session.signOut() }
            else -> onOther()
        }
    }

    private fun signInRequired() {
        _events.trySend(PeopleEvent.SignInRequired)
    }

    companion object {
        const val LIST_PAGE_SIZE = 20
        const val PENDING_SNAPSHOT_SIZE = 100
        const val STALE_AFTER_MS = 60_000L
        const val MAX_PEOPLE = 32
        const val MAX_LISTS = 12

        fun handleKey(username: String, host: String?): String = if (host == null) username else "$username@$host"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                PeopleViewModel(
                    session = app.container.sessionService,
                    users = app.container.usersApi,
                    follows = app.container.followApi,
                    moderation = app.container.moderationApi,
                    reports = app.container.reportApi,
                )
            }
        }
    }
}

private fun UserDetail.after(action: ModerationAction): UserDetail = when (action) {
    ModerationAction.Mute -> copy(isMuted = true)
    ModerationAction.Unmute -> copy(isMuted = false)
    ModerationAction.HideRenotes -> copy(isRenoteMuted = true)
    ModerationAction.ShowRenotes -> copy(isRenoteMuted = false)
    ModerationAction.Block -> copy(isBlocking = true, isFollowing = false, isFollowed = false, hasPendingRequestFromYou = false)
    ModerationAction.Unblock -> copy(isBlocking = false)
}

private val Throwable.isNoSuchUser: Boolean
    get() = this is HttpStatusException &&
        (apiErrorCode == ApiErrorCodes.NO_SUCH_USER || apiErrorCode == ApiErrorCodes.FAILED_TO_RESOLVE_REMOTE_USER)

private val Throwable.isHiddenList: Boolean
    get() = this is HttpStatusException && apiErrorCode == ApiErrorCodes.FORBIDDEN

private fun Throwable.toFollowFailure(): FollowFailure = when {
    this is HttpStatusException && apiErrorCode == ApiErrorCodes.BLOCKED -> FollowFailure.BlockedByThem
    this is HttpStatusException && apiErrorCode == ApiErrorCodes.BLOCKING -> FollowFailure.YouBlocked
    isRateLimited -> FollowFailure.RateLimited
    else -> FollowFailure.Other
}

private fun Throwable.toReportFailure(): ReportFailure = when {
    this is HttpStatusException && apiErrorCode == ApiErrorCodes.CANNOT_REPORT_YOURSELF -> ReportFailure.Yourself
    this is HttpStatusException && apiErrorCode == ApiErrorCodes.CANNOT_REPORT_THE_ADMIN -> ReportFailure.Admin
    isRateLimited -> ReportFailure.RateLimited
    else -> ReportFailure.Other
}

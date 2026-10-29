package com.fortind.zenith.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.notifications.FortNotification
import com.fortind.zenith.notifications.MissingPermissionException
import com.fortind.zenith.notifications.NotificationStream
import com.fortind.zenith.notifications.NotificationStreamEvent
import com.fortind.zenith.notifications.NotificationType
import com.fortind.zenith.notifications.NotificationsApi
import java.io.IOException
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

enum class NotificationFilter(val types: List<NotificationType>?) {
    All(null),
    Mentions(listOf(NotificationType.Mention, NotificationType.Reply, NotificationType.Quote)),
    Reactions(listOf(NotificationType.Reaction)),
    Renotes(listOf(NotificationType.Renote)),
    Follows(
        listOf(
            NotificationType.Follow,
            NotificationType.FollowRequest,
            NotificationType.FollowRequestAccepted,
        )
    );

    fun accepts(type: NotificationType): Boolean = types == null || type in types
}

enum class FollowRequestProgress { Accepting, Rejecting, Accepted, Rejected }

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState
    data object Guest : NotificationsUiState
    data object NeedsPermission : NotificationsUiState

    data class Ready(
        val filter: NotificationFilter = NotificationFilter.All,
        val items: List<FortNotification> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val failed: Boolean = false,
        val endReached: Boolean = false,
        val followRequests: Map<String, FollowRequestProgress> = emptyMap(),
    ) : NotificationsUiState
}

enum class NotificationsEvent { MarkReadFailed, FollowRequestFailed }

private sealed interface Auth {
    data object Pending : Auth
    data object Guest : Auth
    data class Token(val value: String) : Auth
}

class NotificationsViewModel(
    private val session: UserSessionService,
    private val api: NotificationsApi,
    private val stream: NotificationStream,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _events = Channel<NotificationsEvent>(Channel.BUFFERED)
    val events: Flow<NotificationsEvent> = _events.receiveAsFlow()

    private val foreground = MutableStateFlow(false)
    private var token: String? = null
    private var loadJob: Job? = null
    private var streamSeen = false

    init {
        viewModelScope.launch {
            authStates().collectLatest { auth ->
                when (auth) {
                    Auth.Pending -> reset(NotificationsUiState.Loading)
                    Auth.Guest -> reset(NotificationsUiState.Guest)
                    is Auth.Token -> serve(auth.value)
                }
            }
        }
    }

    fun setForeground(active: Boolean) {
        foreground.value = active
    }

    fun logIn() = session.leaveGuest()

    fun setFilter(filter: NotificationFilter) {
        val current = ready() ?: return
        if (current.filter == filter) return
        _uiState.value = current.copy(
            filter = filter,
            items = emptyList(),
            loading = true,
            loadingMore = false,
            failed = false,
            endReached = false,
        )
        loadFirstPage()
    }

    fun refresh() {
        val current = ready() ?: return
        if (current.loading) return
        _uiState.value = current.copy(loading = true, loadingMore = false, failed = false)
        loadFirstPage()
    }

    fun retry() {
        val current = ready() ?: return
        if (current.items.isEmpty()) {
            if (current.loading) return
            _uiState.value = current.copy(loading = true, failed = false)
            loadFirstPage()
        } else {
            loadMore(force = true)
        }
    }

    fun loadMore(force: Boolean = false) {
        val token = token ?: return
        val current = ready() ?: return
        val last = current.items.lastOrNull() ?: return
        if (current.loading || current.loadingMore || current.endReached) return
        if (current.failed && !force) return
        _uiState.value = current.copy(loadingMore = true, failed = false)
        loadJob = viewModelScope.launch {
            try {
                val page = api.fetch(token, current.filter.types, last.id, PAGE_SIZE)
                _uiState.update { state ->
                    if (state is NotificationsUiState.Ready && state.filter == current.filter) {
                        val known = state.items.mapTo(HashSet()) { it.id }
                        state.copy(
                            items = state.items + page.filter { it.id !in known },
                            loadingMore = false,
                            endReached = page.size < PAGE_SIZE,
                        )
                    } else {
                        state
                    }
                }
            } catch (e: IOException) {
                handleFailure(e) { markFailed() }
            }
        }
    }

    fun markAllAsRead() {
        val token = token ?: return
        viewModelScope.launch {
            try {
                api.markAllAsRead(token)
                if (this@NotificationsViewModel.token == token) _unreadCount.value = 0
            } catch (e: IOException) {
                handleFailure(e) { _events.trySend(NotificationsEvent.MarkReadFailed) }
            }
        }
    }

    fun respondToFollowRequest(notification: FortNotification, accept: Boolean) {
        val token = token ?: return
        val actor = notification.actor ?: return
        val current = ready() ?: return
        if (current.followRequests.containsKey(notification.id)) return
        setFollowRequestProgress(
            notification.id,
            if (accept) FollowRequestProgress.Accepting else FollowRequestProgress.Rejecting,
        )
        viewModelScope.launch {
            try {
                api.respondToFollowRequest(token, actor.id, accept)
                setFollowRequestProgress(
                    notification.id,
                    if (accept) FollowRequestProgress.Accepted else FollowRequestProgress.Rejected,
                )
            } catch (e: IOException) {
                setFollowRequestProgress(notification.id, null)
                handleFailure(e) { _events.trySend(NotificationsEvent.FollowRequestFailed) }
            }
        }
    }

    private fun authStates(): Flow<Auth> = combine(session.state, session.credentialsVersion) { state, _ -> state }
        .map { state ->
            when (state) {
                is SessionState.SignedIn -> session.token()?.let(Auth::Token) ?: Auth.Pending
                SessionState.Guest -> Auth.Guest
                SessionState.Restoring, SessionState.SignedOut -> Auth.Pending
            }
        }
        .distinctUntilChanged()

    private fun reset(state: NotificationsUiState) {
        token = null
        loadJob?.cancel()
        _unreadCount.value = 0
        _uiState.value = state
    }

    private suspend fun serve(token: String) {
        reset(NotificationsUiState.Ready(loading = true))
        this.token = token
        streamSeen = false
        loadFirstPage()
        foreground.collectLatest { active -> if (active) listen(token) }
    }

    private suspend fun listen(token: String) {
        stream.events(token).collect { event ->
            when (event) {
                NotificationStreamEvent.Connected -> {
                    if (streamSeen) catchUp(token)
                    streamSeen = true
                }
                is NotificationStreamEvent.Received -> onReceived(event.notification)
                NotificationStreamEvent.AllRead -> _unreadCount.value = 0
            }
        }
    }

    private fun loadFirstPage() {
        val token = token ?: return
        val filter = ready()?.filter ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val page = api.fetch(token, filter.types, null, PAGE_SIZE)
                _uiState.update { state ->
                    if (state is NotificationsUiState.Ready && state.filter == filter) {
                        state.copy(
                            items = page,
                            loading = false,
                            loadingMore = false,
                            failed = false,
                            endReached = page.size < PAGE_SIZE,
                        )
                    } else {
                        state
                    }
                }
            } catch (e: IOException) {
                handleFailure(e) { markFailed() }
            }
            refreshUnreadCount(token)
        }
    }

    private suspend fun catchUp(token: String) {
        val filter = ready()?.filter ?: return
        try {
            val page = api.fetch(token, filter.types, null, PAGE_SIZE)
            _uiState.update { state ->
                if (state is NotificationsUiState.Ready && state.filter == filter && !state.loading) {
                    val known = state.items.mapTo(HashSet()) { it.id }
                    val newer = page.filter { it.id !in known }
                    state.copy(items = (newer + state.items).sortedByDescending { it.createdAtMillis })
                } else {
                    state
                }
            }
        } catch (e: IOException) {
            handleFailure(e) {}
        }
        refreshUnreadCount(token)
    }

    private fun onReceived(notification: FortNotification) {
        _unreadCount.update { it + 1 }
        _uiState.update { state ->
            if (state is NotificationsUiState.Ready &&
                state.filter.accepts(notification.type) &&
                state.items.none { it.id == notification.id }
            ) {
                state.copy(items = listOf(notification) + state.items)
            } else {
                state
            }
        }
    }

    private suspend fun refreshUnreadCount(token: String) {
        try {
            val count = api.unreadCount(token)
            if (this.token == token) _unreadCount.value = count
        } catch (e: IOException) {
            handleFailure(e) {}
        }
    }

    private fun setFollowRequestProgress(id: String, progress: FollowRequestProgress?) {
        _uiState.update { state ->
            if (state is NotificationsUiState.Ready) {
                state.copy(
                    followRequests = if (progress == null) state.followRequests - id else state.followRequests + (id to progress)
                )
            } else {
                state
            }
        }
    }

    private fun markFailed() {
        _uiState.update { state ->
            if (state is NotificationsUiState.Ready) state.copy(loading = false, loadingMore = false, failed = true) else state
        }
    }

    private fun handleFailure(error: IOException, onOther: () -> Unit) {
        when (error) {
            is MissingPermissionException -> {
                loadJob?.cancel()
                _uiState.value = NotificationsUiState.NeedsPermission
            }
            is TokenRejectedException -> viewModelScope.launch { session.signOut() }
            else -> onOther()
        }
    }

    private fun ready(): NotificationsUiState.Ready? = _uiState.value as? NotificationsUiState.Ready

    companion object {
        const val PAGE_SIZE = 30

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                NotificationsViewModel(
                    session = app.container.sessionService,
                    api = app.container.notificationsApi,
                    stream = app.container.notificationStream,
                )
            }
        }
    }
}

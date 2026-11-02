package com.fortind.zenith.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.account.ModerationApi
import com.fortind.zenith.account.ModerationEntry
import com.fortind.zenith.account.ModerationKind
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.net.isRateLimited
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ListStatus { Idle, Loading, Ready, Failed }

data class ModerationList(
    val status: ListStatus = ListStatus.Idle,
    val items: List<ModerationEntry> = emptyList(),
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val endReached: Boolean = false,
)

data class ModerationUiState(
    val lists: Map<ModerationKind, ModerationList> = ModerationKind.entries.associateWith { ModerationList() },
    val busy: Set<String> = emptySet(),
    val needsPermission: Boolean = false,
    val signedIn: Boolean = true,
) {
    fun list(kind: ModerationKind): ModerationList = lists.getValue(kind)
}

sealed interface ModerationEvent {
    data class Removed(val entry: ModerationEntry) : ModerationEvent

    data class Restored(val entry: ModerationEntry) : ModerationEvent

    data object RateLimited : ModerationEvent

    data object Failed : ModerationEvent
}

fun busyKey(entry: ModerationEntry): String = "${entry.kind.ordinal}:${entry.user.id}"

class ModerationViewModel(
    private val session: UserSessionService,
    private val api: ModerationApi,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModerationUiState())
    val uiState: StateFlow<ModerationUiState> = _uiState.asStateFlow()

    private val _events = Channel<ModerationEvent>(Channel.BUFFERED)
    val events: Flow<ModerationEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            session.credentialsVersion.drop(1).collect { _uiState.value = ModerationUiState() }
        }
    }

    fun load(kind: ModerationKind) {
        if (_uiState.value.list(kind).status != ListStatus.Idle) return
        refresh(kind)
    }

    fun refresh(kind: ModerationKind) {
        update(kind) { it.copy(status = ListStatus.Loading, loadMoreFailed = false) }
        viewModelScope.launch {
            try {
                val token = session.token()
                if (token == null) {
                    _uiState.update { it.copy(signedIn = false) }
                    update(kind) { ModerationList(status = ListStatus.Failed) }
                    return@launch
                }
                val items = api.list(token, kind, untilId = null, limit = PAGE_SIZE)
                update(kind) { ModerationList(status = ListStatus.Ready, items = items, endReached = items.isEmpty()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                update(kind) { it.copy(status = ListStatus.Failed) }
                handleAuthFailure(e)
            }
        }
    }

    fun loadMore(kind: ModerationKind) {
        val current = _uiState.value.list(kind)
        if (current.status != ListStatus.Ready || current.loadingMore || current.endReached) return
        val cursor = current.items.lastOrNull()?.id ?: return
        update(kind) { it.copy(loadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            try {
                val token = session.token() ?: return@launch update(kind) { it.copy(loadingMore = false, loadMoreFailed = true) }
                val page = api.list(token, kind, untilId = cursor, limit = PAGE_SIZE)
                update(kind) { list ->
                    val known = list.items.mapTo(HashSet()) { it.id }
                    list.copy(
                        items = list.items + page.filter { known.add(it.id) },
                        loadingMore = false,
                        endReached = page.isEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                update(kind) { it.copy(loadingMore = false, loadMoreFailed = true) }
                handleAuthFailure(e)
            }
        }
    }

    fun remove(entry: ModerationEntry) {
        runAction(entry) { token ->
            when (entry.kind) {
                ModerationKind.Mute -> api.unmute(token, entry.user.id)
                ModerationKind.RenoteMute -> api.showRenotes(token, entry.user.id)
                ModerationKind.Block -> api.unblock(token, entry.user.id)
            }
            update(entry.kind) { list -> list.copy(items = list.items.filterNot { it.id == entry.id }) }
            _events.trySend(ModerationEvent.Removed(entry))
        }
    }

    fun undo(entry: ModerationEntry) {
        runAction(entry) { token ->
            when (entry.kind) {
                ModerationKind.Mute -> api.mute(token, entry.user.id, entry.expiresAtMillis?.takeIf { it > clock() })
                ModerationKind.RenoteMute -> api.hideRenotes(token, entry.user.id)
                ModerationKind.Block -> api.block(token, entry.user.id)
            }
            _events.trySend(ModerationEvent.Restored(entry))
            refresh(entry.kind)
        }
    }

    private fun runAction(entry: ModerationEntry, action: suspend (String) -> Unit) {
        val key = busyKey(entry)
        if (key in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            try {
                val token = session.token()
                if (token == null) {
                    _uiState.update { it.copy(signedIn = false) }
                    return@launch
                }
                action(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (!handleAuthFailure(e)) _events.trySend(if (e.isRateLimited) ModerationEvent.RateLimited else ModerationEvent.Failed)
            } finally {
                _uiState.update { it.copy(busy = it.busy - key) }
            }
        }
    }

    private fun handleAuthFailure(error: IOException): Boolean = when (error) {
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

    private fun update(kind: ModerationKind, transform: (ModerationList) -> ModerationList) {
        _uiState.update { state -> state.copy(lists = state.lists + (kind to transform(state.list(kind)))) }
    }

    companion object {
        const val PAGE_SIZE = 30

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                ModerationViewModel(
                    session = app.container.sessionService,
                    api = app.container.moderationApi,
                )
            }
        }
    }
}

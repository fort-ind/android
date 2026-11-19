package com.fortind.zenith.ui.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.net.isRateLimited
import com.fortind.zenith.schedule.ScheduleApi
import com.fortind.zenith.schedule.ScheduledNote
import com.fortind.zenith.window.WindowEvent
import com.fortind.zenith.window.WindowLink
import com.fortind.zenith.window.windowLink
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScheduledStatus { Idle, Loading, Ready, Failed, NeedsPermission, SignedOut }

data class ScheduledUiState(
    val status: ScheduledStatus = ScheduledStatus.Idle,
    val items: List<ScheduledNote> = emptyList(),
    val busy: Set<String> = emptySet(),
    val max: Int? = null,
) {
    val count: Int? get() = if (status == ScheduledStatus.Ready) items.size else null
}

sealed interface ScheduledEvent {
    data object Deleted : ScheduledEvent

    data object RateLimited : ScheduledEvent

    data object Failed : ScheduledEvent
}

class ScheduledViewModel(
    private val session: UserSessionService,
    private val api: ScheduleApi,
    private val windows: WindowLink = WindowLink.Alone,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduledUiState())
    val uiState: StateFlow<ScheduledUiState> = _uiState.asStateFlow()

    private val _events = Channel<ScheduledEvent>(Channel.BUFFERED)
    val events: Flow<ScheduledEvent> = _events.receiveAsFlow()

    private var generation = 0

    init {
        viewModelScope.launch {
            session.credentialsVersion.drop(1).collect {
                generation++
                _uiState.value = ScheduledUiState()
            }
        }
        viewModelScope.launch {
            windows.fromOtherWindows.filterIsInstance<WindowEvent.ScheduledChanged>().collect {
                if (_uiState.value.status != ScheduledStatus.Idle) refresh(quiet = true)
            }
        }
    }

    fun load() {
        if (_uiState.value.status == ScheduledStatus.Idle) refresh(quiet = false)
    }

    fun changed() {
        refresh(quiet = true)
        windows.publish(WindowEvent.ScheduledChanged())
    }

    fun refresh(quiet: Boolean) {
        val round = ++generation
        if (!quiet || _uiState.value.status == ScheduledStatus.Idle) _uiState.update { it.copy(status = ScheduledStatus.Loading) }
        viewModelScope.launch {
            try {
                val token = session.token()
                if (token == null) {
                    publish(round) { it.copy(status = ScheduledStatus.SignedOut, items = emptyList()) }
                    return@launch
                }
                if (!session.hasScope(FortInstance.SCOPE_READ_NOTES_SCHEDULE)) {
                    publish(round) { it.copy(status = ScheduledStatus.NeedsPermission, items = emptyList()) }
                    return@launch
                }
                val items = api.list(token, untilId = null, limit = PAGE_SIZE).sortedBy { it.scheduledAtMillis }
                publish(round) { it.copy(status = ScheduledStatus.Ready, items = items, max = scheduleNoteMax()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                publish(round) { state ->
                    when (e) {
                        is MissingPermissionException -> state.copy(status = ScheduledStatus.NeedsPermission, items = emptyList())
                        is TokenRejectedException -> state.copy(status = ScheduledStatus.SignedOut, items = emptyList())
                        else -> if (quiet && state.status == ScheduledStatus.Ready) state else state.copy(status = ScheduledStatus.Failed)
                    }
                }
                if (e is TokenRejectedException) session.signOut()
            }
        }
    }

    fun delete(note: ScheduledNote) {
        if (note.id in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + note.id) }
        viewModelScope.launch {
            try {
                val token = session.token()
                if (token == null) {
                    _uiState.update { it.copy(status = ScheduledStatus.SignedOut) }
                    return@launch
                }
                api.delete(token, note.id)
                _uiState.update { state -> state.copy(items = state.items.filterNot { it.id == note.id }) }
                windows.publish(WindowEvent.ScheduledChanged())
                _events.trySend(ScheduledEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                when (e) {
                    is MissingPermissionException -> _uiState.update { it.copy(status = ScheduledStatus.NeedsPermission) }
                    is TokenRejectedException -> session.signOut()
                    else -> _events.trySend(if (e.isRateLimited) ScheduledEvent.RateLimited else ScheduledEvent.Failed)
                }
            } finally {
                _uiState.update { it.copy(busy = it.busy - note.id) }
            }
        }
    }

    private fun publish(round: Int, transform: (ScheduledUiState) -> ScheduledUiState) {
        if (round == generation) _uiState.update(transform)
    }

    private fun scheduleNoteMax(): Int? = (session.state.value as? SessionState.SignedIn)?.user?.scheduleNoteMax

    companion object {
        const val PAGE_SIZE = 100

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                ScheduledViewModel(
                    session = app.container.sessionService,
                    api = app.container.scheduleApi,
                    windows = windowLink(app.container.windowEvents),
                )
            }
        }
    }
}

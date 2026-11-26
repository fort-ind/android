package com.fortind.zenith.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.composer.DraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data class Loaded(val user: CurrentUser) : ProfileUiState

    data object Unavailable : ProfileUiState
    data object Guest : ProfileUiState
}

fun SessionState.toProfileUiState(): ProfileUiState? = when (this) {
    is SessionState.SignedIn -> user?.let(ProfileUiState::Loaded) ?: ProfileUiState.Unavailable
    SessionState.Guest -> ProfileUiState.Guest
    SessionState.Restoring, SessionState.SignedOut -> null
}

fun UserSessionService.profileUiState(scope: CoroutineScope, stopTimeoutMs: Long): StateFlow<ProfileUiState> = state
    .mapNotNull { it.toProfileUiState() }
    .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMs), state.value.toProfileUiState() ?: ProfileUiState.Unavailable)

class ProfileViewModel(
    private val session: UserSessionService,
    private val drafts: DraftStore,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = session.profileUiState(viewModelScope, stopTimeoutMs = 5_000)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            session.refreshUser()
            _refreshing.value = false
        }
    }

    fun logOut() {
        viewModelScope.launch {
            (session.state.value as? SessionState.SignedIn)?.user?.id?.let { drafts.forget(it) }
            session.signOut()
        }
    }

    fun logIn() = session.leaveGuest()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                ProfileViewModel(app.container.sessionService, app.container.draftStore)
            }
        }
    }
}

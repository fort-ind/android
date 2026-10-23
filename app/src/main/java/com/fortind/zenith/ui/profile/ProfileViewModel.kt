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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data class Loaded(val user: CurrentUser) : ProfileUiState

    /** Signed in, but the profile couldn't be loaded (the app was restored while offline). */
    data object Unavailable : ProfileUiState
    data object Guest : ProfileUiState
}

class ProfileViewModel(private val session: UserSessionService) : ViewModel() {

    // Restoring/SignedOut mean the screen is already on its way out, so keep showing the last state.
    val uiState: StateFlow<ProfileUiState> = session.state
        .mapNotNull { state ->
            when (state) {
                is SessionState.SignedIn -> state.user?.let(ProfileUiState::Loaded) ?: ProfileUiState.Unavailable
                SessionState.Guest -> ProfileUiState.Guest
                SessionState.Restoring, SessionState.SignedOut -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Unavailable)

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
        viewModelScope.launch { session.signOut() }
    }

    /** From the guest profile: head back to the login screen. */
    fun logIn() = session.leaveGuest()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                ProfileViewModel(app.container.sessionService)
            }
        }
    }
}

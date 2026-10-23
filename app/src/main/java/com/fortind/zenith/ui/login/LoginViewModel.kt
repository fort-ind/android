package com.fortind.zenith.ui.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.MiAuthPoller
import com.fortind.zenith.auth.MiAuthUrl
import com.fortind.zenith.auth.PollOutcome
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class LoginError { Cancelled, TimedOut, Network, Rejected, NoBrowser }

data class LoginUiState(
    val signingIn: Boolean = false,
    val error: LoginError? = null,
)

sealed interface LoginEvent {
    data class OpenAuthPage(val url: String) : LoginEvent
}

class LoginViewModel(
    private val savedState: SavedStateHandle,
    private val poller: MiAuthPoller,
    private val sessionService: UserSessionService,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    private var authJob: Job? = null
    private var tabOpened = false
    private var tabClosed = false
    private var completing = false

    init {
        // The process died while the browser was open: the poller is gone, but the session may have
        // been approved in the meantime, so make one last check and then give up.
        savedState.get<String>(KEY_SESSION)?.let { session ->
            tabClosed = true
            _uiState.value = LoginUiState(signingIn = true)
            startPolling(session)
        }
    }

    fun onSignInClick() {
        if (_uiState.value.signingIn) return
        val session = newSessionId()
        savedState[KEY_SESSION] = session
        tabOpened = false
        tabClosed = false
        completing = false
        _uiState.value = LoginUiState(signingIn = true)
        startPolling(session)
        _events.trySend(LoginEvent.OpenAuthPage(MiAuthUrl.authorize(session = session)))
    }

    fun onAuthPageLaunched() {
        tabOpened = true
    }

    fun onAuthPageLaunchFailed() {
        authJob?.cancel()
        fail(LoginError.NoBrowser)
    }

    /** Called on every resume. Returning from the Custom Tab means the user closed it. */
    fun onReturnedToApp() {
        if (!tabOpened || completing) return
        tabOpened = false
        tabClosed = true
    }

    /** The user backed out while we were waiting; returns to a clean idle state without an error. */
    fun onCancel() {
        authJob?.cancel()
        reset()
    }

    private fun startPolling(session: String) {
        authJob = viewModelScope.launch {
            when (val outcome = poller.poll(session, tabClosed = { tabClosed })) {
                is PollOutcome.Approved -> complete(outcome.token)
                PollOutcome.TimedOut -> fail(LoginError.TimedOut)
                PollOutcome.Abandoned -> fail(LoginError.Cancelled)
                is PollOutcome.NetworkFailure -> fail(LoginError.Network)
            }
        }
    }

    private suspend fun complete(token: String) {
        completing = true
        try {
            sessionService.signIn(token)
            reset()
        } catch (_: TokenRejectedException) {
            fail(LoginError.Rejected)
        } catch (_: IOException) {
            fail(LoginError.Network)
        }
    }

    private fun fail(error: LoginError) {
        savedState.remove<String>(KEY_SESSION)
        tabOpened = false
        tabClosed = false
        completing = false
        _uiState.value = LoginUiState(error = error)
    }

    private fun reset() {
        savedState.remove<String>(KEY_SESSION)
        tabOpened = false
        tabClosed = false
        completing = false
        _uiState.value = LoginUiState()
    }

    companion object {
        private const val KEY_SESSION = "miauth_session"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                LoginViewModel(
                    savedState = createSavedStateHandle(),
                    poller = MiAuthPoller(app.container.miAuthApi),
                    sessionService = app.container.sessionService,
                )
            }
        }
    }
}

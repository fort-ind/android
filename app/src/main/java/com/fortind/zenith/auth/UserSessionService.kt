package com.fortind.zenith.auth

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface SessionState {
    data object Restoring : SessionState
    data object SignedOut : SessionState

    /** Browsing the app without an account. Not persisted: a restart returns to the login screen. */
    data object Guest : SessionState

    /** [user] is null only when a stored token couldn't be verified because the device is offline. */
    data class SignedIn(val user: CurrentUser?) : SessionState
}

/** Process-wide owner of the signed-in session; the single source of truth for "who am I". */
class UserSessionService(
    private val tokenStore: TokenStore,
    private val userApi: UserApi,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Loads a stored token, if any, and verifies it against `/api/i`. */
    suspend fun restore() {
        val token = withContext(Dispatchers.IO) { tokenStore.read() }
        if (token == null) {
            _state.value = SessionState.SignedOut
            return
        }
        _state.value = try {
            SessionState.SignedIn(userApi.fetchCurrentUser(token))
        } catch (_: TokenRejectedException) {
            withContext(Dispatchers.IO) { tokenStore.clear() }
            SessionState.SignedOut
        } catch (_: IOException) {
            SessionState.SignedIn(user = null)
        }
    }


    suspend fun signIn(token: String) {
        val user = userApi.fetchCurrentUser(token)
        withContext(Dispatchers.IO) { tokenStore.write(token) }
        _state.value = SessionState.SignedIn(user)
    }

    suspend fun refreshUser(): Boolean {
        val token = token() ?: return false.also { _state.value = SessionState.SignedOut }
        return try {
            _state.value = SessionState.SignedIn(userApi.fetchCurrentUser(token))
            true
        } catch (_: TokenRejectedException) {
            signOut()
            false
        } catch (_: IOException) {
            false
        }
    }

    fun browseAsGuest() {
        _state.value = SessionState.Guest
    }


    fun leaveGuest() {
        if (_state.value == SessionState.Guest) _state.value = SessionState.SignedOut
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) { tokenStore.clear() }
        _state.value = SessionState.SignedOut
    }


    suspend fun token(): String? = withContext(Dispatchers.IO) { tokenStore.read() }
}

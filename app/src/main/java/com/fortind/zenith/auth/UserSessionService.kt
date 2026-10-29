package com.fortind.zenith.auth

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

sealed interface SessionState {
    data object Restoring : SessionState
    data object SignedOut : SessionState

    data object Guest : SessionState

    data class SignedIn(val user: CurrentUser?) : SessionState
}

class UserSessionService(
    private val tokenStore: TokenStore,
    private val userApi: UserApi,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _credentialsVersion = MutableStateFlow(0)
    val credentialsVersion: StateFlow<Int> = _credentialsVersion.asStateFlow()

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
        _credentialsVersion.update { it + 1 }
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

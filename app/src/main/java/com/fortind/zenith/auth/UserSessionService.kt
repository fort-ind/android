package com.fortind.zenith.auth

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val scopeStamps: ScopeStampStore = InMemoryScopeStampStore(),
    private val restoreRetryDelaysMs: List<Long> = DEFAULT_RESTORE_RETRY_DELAYS_MS,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _credentialsVersion = MutableStateFlow(0)
    val credentialsVersion: StateFlow<Int> = _credentialsVersion.asStateFlow()

    val scopesOutdated: Flow<Boolean> = combine(_state, scopeStamps.stamp) { state, stamp ->
        val userId = (state as? SessionState.SignedIn)?.user?.id
        userId != null && stamp.bannerWanted(userId, FortInstance.SCOPES)
    }

    suspend fun hasScope(scope: String): Boolean = scopeStamps.stamp.first().covers(listOf(scope))

    suspend fun markScopesStale() = scopeStamps.markStale()

    suspend fun dismissScopeBanner() {
        val userId = (_state.value as? SessionState.SignedIn)?.user?.id ?: return
        scopeStamps.dismissBanner(userId)
    }

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
        if ((_state.value as? SessionState.SignedIn)?.user == null) retryUntilUserKnown()
    }

    private suspend fun retryUntilUserKnown() {
        for (waitMs in restoreRetryDelaysMs) {
            delay(waitMs)
            val state = _state.value as? SessionState.SignedIn ?: return
            if (state.user != null) return
            if (refreshUser()) return
        }
    }

    suspend fun signIn(token: String) {
        val user = userApi.fetchCurrentUser(token)
        withContext(Dispatchers.IO) { tokenStore.write(token) }
        scopeStamps.grant(FortInstance.SCOPES.toSet())
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

    fun applyUpdatedUser(user: CurrentUser) {
        if (_state.value is SessionState.SignedIn) _state.value = SessionState.SignedIn(user)
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

private val DEFAULT_RESTORE_RETRY_DELAYS_MS = listOf(5_000L, 15_000L, 45_000L, 120_000L)

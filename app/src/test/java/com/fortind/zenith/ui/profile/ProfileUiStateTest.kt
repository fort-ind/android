package com.fortind.zenith.ui.profile

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileUiStateTest {

    private val user = CurrentUser("me", "me", null, null, null)

    @Test
    fun signedInWithAUserIsLoaded() {
        assertEquals(ProfileUiState.Loaded(user), SessionState.SignedIn(user).toProfileUiState())
    }

    @Test
    fun signedInWithoutAUserIsUnavailable() {
        assertEquals(ProfileUiState.Unavailable, SessionState.SignedIn(user = null).toProfileUiState())
    }

    @Test
    fun guestIsGuest() {
        assertEquals(ProfileUiState.Guest, SessionState.Guest.toProfileUiState())
    }

    @Test
    fun restoringAndSignedOutKeepTheLastValue() {
        assertNull(SessionState.Restoring.toProfileUiState())
        assertNull(SessionState.SignedOut.toProfileUiState())
    }

    @Test
    fun theFirstValueIsTheSessionsCurrentStateWithoutCollecting() = runTest {
        val session = UserSessionService(FakeTokenStore("token"), FakeUserApi().apply { failure = IOException("offline") }, restoreRetryDelaysMs = emptyList())
        session.restore()

        assertEquals(ProfileUiState.Unavailable, session.profileUiState(backgroundScope, stopTimeoutMs = 0).value)
    }
}

package com.fortind.zenith.auth

import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSessionServiceTest {

    private class FlakyUserApi(private val failures: Int, private val error: IOException = IOException("offline")) : UserApi {
        var calls = 0
        private val real = FakeUserApi()

        override suspend fun fetchCurrentUser(token: String): CurrentUser {
            calls++
            if (calls <= failures) throw error
            return real.fetchCurrentUser(token)
        }
    }

    private fun session(api: UserApi, token: String? = "token") =
        UserSessionService(FakeTokenStore(token), api, restoreRetryDelaysMs = listOf(10L, 20L, 40L))

    @Test
    fun anOfflineRestoreKeepsTryingUntilTheProfileArrives() = runTest {
        val api = FlakyUserApi(failures = 2)
        val session = session(api)

        session.restore()

        assertEquals("me", (session.state.value as SessionState.SignedIn).user?.id)
        assertEquals(3, api.calls)
    }

    @Test
    fun itGivesUpAfterTheLastDelayAndStaysSignedIn() = runTest {
        val api = FlakyUserApi(failures = 100)
        val session = session(api)

        session.restore()

        assertEquals(SessionState.SignedIn(user = null), session.state.value)
        assertEquals(4, api.calls)
    }

    @Test
    fun aRejectedTokenDuringTheRetriesSignsOut() = runTest {
        val store = FakeTokenStore("token")
        val session = UserSessionService(store, object : UserApi {
            var first = true
            override suspend fun fetchCurrentUser(token: String): CurrentUser {
                if (first) {
                    first = false
                    throw IOException("offline")
                }
                throw TokenRejectedException(401)
            }
        }, restoreRetryDelaysMs = listOf(10L))

        session.restore()

        assertEquals(SessionState.SignedOut, session.state.value)
        assertNull(store.token)
    }
}

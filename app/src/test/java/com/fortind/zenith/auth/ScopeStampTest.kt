package com.fortind.zenith.auth

import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeStampTest {

    private fun session(store: ScopeStampStore = InMemoryScopeStampStore(), token: String? = "token") =
        UserSessionService(FakeTokenStore(token), FakeUserApi(), store)

    @Test
    fun aTokenWithNoStampAsksOnce() = runTest {
        val session = session()
        session.restore()

        assertTrue(session.scopesOutdated.first())
        assertFalse(session.hasScope("read:drive"))

        session.dismissScopeBanner()

        assertFalse(session.scopesOutdated.first())
        assertFalse(session.hasScope("read:drive"))
    }

    @Test
    fun signingInStampsEveryRequestedScope() = runTest {
        val session = session(token = null)

        session.signIn("fresh")

        assertFalse(session.scopesOutdated.first())
        FortInstance.SCOPES.forEach { assertTrue(session.hasScope(it)) }
    }

    @Test
    fun aStampMissingANewScopeCountsAsOutdated() = runTest {
        val store = InMemoryScopeStampStore(ScopeStamp(granted = setOf("read:account")))
        val session = session(store)
        session.restore()

        assertTrue(session.hasScope("read:account"))
        assertFalse(session.hasScope("read:drive"))
        assertTrue(session.scopesOutdated.first())
    }

    @Test
    fun aServerDenialMakesTheStampStale() = runTest {
        val session = session(token = null)
        session.signIn("fresh")

        session.markScopesStale()

        assertFalse(session.hasScope("read:account"))
        assertTrue(session.scopesOutdated.first())
    }

    @Test
    fun guestsAndSignedOutPeopleSeeNoBanner() = runTest {
        val session = session(token = null)

        session.restore()
        assertFalse(session.scopesOutdated.first())

        session.browseAsGuest()
        assertFalse(session.scopesOutdated.first())
    }

    @Test
    fun dismissingIsPerAccount() {
        val stamp = ScopeStamp(bannerDismissedFor = setOf("a"))

        assertFalse(stamp.bannerWanted("a", FortInstance.SCOPES))
        assertTrue(stamp.bannerWanted("b", FortInstance.SCOPES))
    }
}

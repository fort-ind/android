package com.fortind.zenith.widget

import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.settings.WindowSizeDp
import com.fortind.zenith.ui.nav.ShellRequest
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WidgetLoadTest {
    @Test
    fun aStoredTokenMeansSignedIn() = runTest {
        val size = WindowSizeDp(600, 700)
        assertEquals(
            WidgetLoad.Ready(signedIn = true, composerSize = size),
            loadWidget(readToken = { "token" }, readComposerSize = { size }),
        )
    }

    @Test
    fun noTokenMeansSignedOut() = runTest {
        assertEquals(
            WidgetLoad.Ready(signedIn = false, composerSize = null),
            loadWidget(readToken = { null }, readComposerSize = { null }),
        )
    }

    @Test
    fun aFailingTokenReadIsAnErrorState() = runTest {
        val result = loadWidget(readToken = { throw IllegalStateException("keystore") }, readComposerSize = { null })
        assertTrue(result is WidgetLoad.Failed)
        assertEquals("keystore", (result as WidgetLoad.Failed).cause?.message)
    }

    @Test
    fun aHangingTokenReadTimesOutIntoAnErrorState() = runTest {
        val result = loadWidget(readToken = { awaitCancellation() }, readComposerSize = { null }, timeoutMs = 1_000)
        assertEquals(WidgetLoad.Failed(cause = null), result)
    }

    @Test
    fun aFailingSettingsReadOnlyLosesTheRememberedComposerSize() = runTest {
        val result = loadWidget(readToken = { "token" }, readComposerSize = { throw IOException("datastore") })
        assertEquals(WidgetLoad.Ready(signedIn = true, composerSize = null), result)
    }

    @Test
    fun cancellationIsNotTurnedIntoAnErrorState() = runTest {
        try {
            loadWidget(readToken = { throw CancellationException("session closed") }, readComposerSize = { null })
            fail("expected the cancellation to propagate")
        } catch (e: CancellationException) {
            assertEquals("session closed", e.message)
        }
    }

    @Test
    fun whileRestoringTheWidgetUsesWhatItLoaded() {
        assertEquals(true, signedInFrom(SessionState.Restoring, WidgetLoad.Ready(signedIn = true, composerSize = null)))
        assertEquals(false, signedInFrom(SessionState.Restoring, WidgetLoad.Ready(signedIn = false, composerSize = null)))
        assertNull(signedInFrom(SessionState.Restoring, WidgetLoad.Failed(cause = null)))
    }

    @Test
    fun aKnownSessionWinsOverTheLoad() {
        val failed = WidgetLoad.Failed(cause = null)
        assertEquals(true, signedInFrom(SessionState.SignedIn(user = null), failed))
        assertEquals(false, signedInFrom(SessionState.SignedOut, WidgetLoad.Ready(signedIn = true, composerSize = null)))
        assertEquals(false, signedInFrom(SessionState.Guest, WidgetLoad.Ready(signedIn = true, composerSize = null)))
    }

    @Test
    fun shellRequestsParseByNameAndIgnoreAnythingElse() {
        assertEquals(ShellRequest.SEARCH, ShellRequest.fromName("SEARCH"))
        assertEquals(ShellRequest.SIGN_IN, ShellRequest.fromName("SIGN_IN"))
        assertNull(ShellRequest.fromName("search"))
        assertNull(ShellRequest.fromName(""))
        assertNull(ShellRequest.fromName(null))
        assertTrue(ShellRequest.entries.all { ShellRequest.fromName(it.name) == it })
    }
}

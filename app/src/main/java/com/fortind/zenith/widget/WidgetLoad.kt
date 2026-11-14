package com.fortind.zenith.widget

import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.settings.WindowSizeDp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

const val WIDGET_LOAD_TIMEOUT_MS = 5_000L

sealed interface WidgetLoad {
    data class Ready(val signedIn: Boolean, val composerSize: WindowSizeDp?) : WidgetLoad

    data class Failed(val cause: Exception?) : WidgetLoad
}

suspend fun loadWidget(
    readToken: suspend () -> String?,
    readComposerSize: suspend () -> WindowSizeDp?,
    timeoutMs: Long = WIDGET_LOAD_TIMEOUT_MS,
): WidgetLoad = try {
    withTimeoutOrNull(timeoutMs) {
        val signedIn = readToken() != null
        WidgetLoad.Ready(signedIn = signedIn, composerSize = composerSizeOrNull(readComposerSize))
    } ?: WidgetLoad.Failed(cause = null)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    WidgetLoad.Failed(e)
}

private suspend fun composerSizeOrNull(read: suspend () -> WindowSizeDp?): WindowSizeDp? = try {
    read()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

fun signedInFrom(session: SessionState, load: WidgetLoad): Boolean? = when (session) {
    SessionState.Restoring -> (load as? WidgetLoad.Ready)?.signedIn
    is SessionState.SignedIn -> true
    SessionState.Guest, SessionState.SignedOut -> false
}

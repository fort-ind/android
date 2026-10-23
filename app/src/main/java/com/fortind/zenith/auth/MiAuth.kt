package com.fortind.zenith.auth

import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

object MiAuthUrl {
    private val SCOPE_FORMAT = Regex("[a-z-]+:[a-z-]+(:[a-z-]+)?")

    /** `https://<host>/miauth/<session>?name=<name>&permission=<a,b,c>` */
    fun authorize(
        host: String = FortInstance.HOST,
        session: String,
        appName: String = FortInstance.APP_NAME,
        scopes: List<String> = FortInstance.SCOPES,
    ): String {
        require(scopes.isNotEmpty() && scopes.all { SCOPE_FORMAT.matches(it) }) { "Bad scopes: $scopes" }
        val name = URLEncoder.encode(appName, "UTF-8").replace("+", "%20")
        return "https://$host/miauth/$session?name=$name&permission=${scopes.joinToString(",")}"
    }

    fun check(host: String = FortInstance.HOST, session: String): String =
        "https://$host/api/miauth/$session/check"
}

sealed interface CheckResult {
    data object Pending : CheckResult
    data class Approved(val token: String) : CheckResult
}

interface MiAuthApi {
    /** One poll of the check endpoint. Throws [IOException] on transport or parse failure. */
    suspend fun check(session: String): CheckResult
}

sealed interface PollOutcome {
    data class Approved(val token: String) : PollOutcome
    data object TimedOut : PollOutcome

    /** The user left the browser without approving. */
    data object Abandoned : PollOutcome
    data class NetworkFailure(val cause: IOException) : PollOutcome
}

class MiAuthPoller(
    private val api: MiAuthApi,
    private val intervalMs: Long = 2_000,
    private val timeoutMs: Long = 5 * 60_000,
    private val maxConsecutiveFailures: Int = 5,
) {
    /**
     * Polls until the session is approved, [timeoutMs] elapses, or the caller cancels.
     *
     * Custom Tabs can't report that they were closed, so the caller flips [tabClosed] once the app is
     * foregrounded again. A check that *started after* that flip is required before giving up, so an
     * approval made just before the tab closed is never missed. MiAuth hands the token out only once,
     * so that last check matters.
     */
    suspend fun poll(session: String, tabClosed: () -> Boolean = { false }): PollOutcome =
        withTimeoutOrNull(timeoutMs) { pollUntilDecided(session, tabClosed) } ?: PollOutcome.TimedOut

    private suspend fun pollUntilDecided(session: String, tabClosed: () -> Boolean): PollOutcome {
        var failures = 0
        while (true) {
            val closedBeforeCheck = tabClosed()
            try {
                when (val result = api.check(session)) {
                    is CheckResult.Approved -> return PollOutcome.Approved(result.token)
                    CheckResult.Pending -> {
                        failures = 0
                        if (closedBeforeCheck) return PollOutcome.Abandoned
                    }
                }
            } catch (e: IOException) {
                if (++failures >= maxConsecutiveFailures) return PollOutcome.NetworkFailure(e)
            }
            delay(intervalMs)
        }
    }
}

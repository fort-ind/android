package com.fortind.zenith.net

import java.util.concurrent.ConcurrentHashMap

class RateLimitGate(private val clock: () -> Long = System::currentTimeMillis) {
    private val heldUntil = ConcurrentHashMap<String, Long>()

    fun isHeld(path: String): Boolean {
        val until = heldUntil[path] ?: return false
        if (clock() < until) return true
        heldUntil.remove(path, until)
        return false
    }

    fun hold(path: String, retryAfter: String?, rateLimitClear: String?) {
        heldUntil[path] = clock() + holdMillis(retryAfter, rateLimitClear)
    }

    private fun holdMillis(retryAfter: String?, rateLimitClear: String?): Long {
        val seconds = retryAfter?.trim()?.toDoubleOrNull()
            ?: rateLimitClear?.trim()?.toDoubleOrNull()
            ?: DEFAULT_HOLD_SECONDS
        return (seconds * MILLIS_PER_SECOND).toLong().coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)
    }

    private companion object {
        const val DEFAULT_HOLD_SECONDS = 5.0
        const val MILLIS_PER_SECOND = 1_000
        const val MIN_HOLD_MS = 1_000L
        const val MAX_HOLD_MS = 120_000L
    }
}

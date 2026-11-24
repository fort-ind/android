package com.fortind.zenith.timeline

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimelinePrefetch(
    private val api: NotesApi,
    private val scope: CoroutineScope,
    private val maxAgeMs: Long = MAX_AGE_MS,
) {
    private class Fetch(val token: String, val kind: TimelineKind, val limit: Int, val page: Deferred<List<Note>>)

    private val pending = AtomicReference<Fetch?>(null)

    fun start(token: String, kind: TimelineKind, limit: Int) {
        val fetch = Fetch(token, kind, limit, scope.async { api.timeline(token, kind, untilId = null, sinceId = null, limit = limit) })
        pending.getAndSet(fetch)?.page?.cancel()
        scope.launch {
            delay(maxAgeMs)
            if (pending.compareAndSet(fetch, null)) fetch.page.cancel()
        }
    }

    suspend fun take(token: String, kind: TimelineKind, limit: Int): List<Note>? {
        val fetch = pending.getAndSet(null) ?: return null
        if (fetch.token != token || fetch.kind != kind || fetch.limit != limit) {
            fetch.page.cancel()
            return null
        }
        return try {
            fetch.page.await()
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        const val MAX_AGE_MS = 60_000L
    }
}

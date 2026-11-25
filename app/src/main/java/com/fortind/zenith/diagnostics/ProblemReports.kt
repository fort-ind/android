package com.fortind.zenith.diagnostics

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ProblemReports(
    private val store: ProblemReportStore,
    private val exits: ExitHistory?,
    private val environment: AppEnvironment,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _report = MutableStateFlow<ProblemReport?>(null)
    val report: StateFlow<ProblemReport?> = _report.asStateFlow()
    private val loaded = AtomicBoolean(false)

    suspend fun load() {
        if (!loaded.compareAndSet(false, true)) return
        _report.value = withContext(io) {
            exits?.let(::absorb)
            store.read()
        }
    }

    suspend fun markAnnounced() {
        val current = _report.value?.takeUnless { it.announced } ?: return
        val announced = current.copy(announced = true)
        _report.value = announced
        withContext(io) { store.write(announced) }
    }

    suspend fun delete() {
        _report.value = null
        withContext(io) { store.clear() }
    }

    private fun absorb(history: ExitHistory) {
        val recent = history.recent()
        val newest = recent.maxOfOrNull { it.timeMillis }
        val floor = store.exitFloor()
        if (floor == null) {
            store.setExitFloor(newest ?: 0L)
            return
        }
        val problem = recent.filter { it.kind != null && it.timeMillis > floor }.maxByOrNull { it.timeMillis }
        val kind = problem?.kind
        if (problem != null && kind != null) {
            val stored = store.read()
            if (stored == null || stored.timeMillis < problem.timeMillis) {
                store.write(ProblemReport(kind, problem.timeMillis, environment, problem.details()))
            }
        }
        if (newest != null && newest > floor) store.setExitFloor(newest)
    }
}

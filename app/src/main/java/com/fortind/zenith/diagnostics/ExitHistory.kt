package com.fortind.zenith.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.IOException

class ProcessExit(val timeMillis: Long, val kind: ProblemKind?, val details: () -> String)

fun interface ExitHistory {
    fun recent(): List<ProcessExit>
}

fun androidExitHistory(context: Context): ExitHistory? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) AndroidExitHistory(context.applicationContext) else null

@RequiresApi(Build.VERSION_CODES.R)
private class AndroidExitHistory(private val context: Context) : ExitHistory {
    override fun recent(): List<ProcessExit> {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        val exits = try {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
        } catch (_: SecurityException) {
            return emptyList()
        }
        return exits
            .filter { it.processName == context.packageName }
            .map { info -> ProcessExit(info.timestamp, info.problemKind()) { info.problemDetails() } }
    }

    private fun ApplicationExitInfo.problemKind(): ProblemKind? = when (reason) {
        ApplicationExitInfo.REASON_ANR -> ProblemKind.NotResponding
        ApplicationExitInfo.REASON_CRASH_NATIVE -> ProblemKind.NativeCrash
        else -> null
    }

    private fun ApplicationExitInfo.problemDetails(): String {
        val description = description.orEmpty()
        if (reason != ApplicationExitInfo.REASON_ANR) return description.cappedDetails()
        val mainThread = try {
            traceInputStream?.bufferedReader()?.useLines(::mainThreadOf).orEmpty()
        } catch (_: IOException) {
            ""
        }
        return listOf(description, mainThread).filter { it.isNotBlank() }.joinToString("\n\n").cappedDetails()
    }

    private companion object {
        const val MAX_EXITS = 8
    }
}

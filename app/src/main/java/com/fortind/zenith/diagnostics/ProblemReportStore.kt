package com.fortind.zenith.diagnostics

import android.content.Context
import java.io.File
import java.io.IOException

fun problemReportStore(context: Context): ProblemReportStore {
    val app = context.applicationContext
    return FileProblemReportStore { File(app.noBackupFilesDir, DIAGNOSTICS_DIR) }
}

private const val DIAGNOSTICS_DIR = "diagnostics"

interface ProblemReportStore {
    fun read(): ProblemReport?
    fun write(report: ProblemReport)
    fun clear()
    fun exitFloor(): Long?
    fun setExitFloor(timeMillis: Long)
}

class FileProblemReportStore(private val directory: () -> File) : ProblemReportStore {

    override fun read(): ProblemReport? = try {
        reportFile().takeIf { it.isFile }?.readText()?.let(::parseProblemReport)
    } catch (_: IOException) {
        null
    }

    override fun write(report: ProblemReport) {
        writeAtomically(reportFile(), report.toJson())
    }

    override fun clear() {
        reportFile().delete()
    }

    override fun exitFloor(): Long? = try {
        floorFile().takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
    } catch (_: IOException) {
        null
    }

    override fun setExitFloor(timeMillis: Long) {
        writeAtomically(floorFile(), timeMillis.toString())
    }

    private fun reportFile() = File(directory(), REPORT_FILE)

    private fun floorFile() = File(directory(), FLOOR_FILE)

    private fun writeAtomically(target: File, text: String) {
        val dir = target.parentFile ?: return
        dir.mkdirs()
        val staging = File(dir, "${target.name}.tmp")
        staging.writeText(text)
        if (!staging.renameTo(target)) {
            target.delete()
            if (!staging.renameTo(target)) staging.delete()
        }
    }

    private companion object {
        const val REPORT_FILE = "problem_report.json"
        const val FLOOR_FILE = "exit_floor"
    }
}

package com.fortind.zenith.diagnostics

import java.io.PrintWriter
import java.io.StringWriter
import org.json.JSONException
import org.json.JSONObject

enum class ProblemKind { Crash, NotResponding, NativeCrash }

data class AppEnvironment(
    val appVersion: String,
    val appBuild: Int,
    val androidRelease: String,
    val sdk: Int,
    val device: String,
)

data class ProblemReport(
    val kind: ProblemKind,
    val timeMillis: Long,
    val environment: AppEnvironment,
    val details: String,
    val thread: String? = null,
    val announced: Boolean = false,
)

const val MAX_REPORT_DETAILS = 32 * 1024

fun crashDetails(error: Throwable): String =
    StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd().cappedDetails()

fun mainThreadOf(anrTrace: Sequence<String>): String {
    val block = StringBuilder()
    var inMain = false
    for (line in anrTrace) {
        if (!inMain && line.startsWith(MAIN_THREAD_HEADER)) inMain = true
        if (!inMain) continue
        if (line.isBlank()) break
        block.append(line).append('\n')
        if (block.length >= MAX_REPORT_DETAILS) break
    }
    return block.toString().trimEnd().cappedDetails()
}

fun String.cappedDetails(): String = if (length <= MAX_REPORT_DETAILS) this else take(MAX_REPORT_DETAILS)

fun ProblemReport.toJson(): String = JSONObject()
    .put(KEY_KIND, kind.name)
    .put(KEY_TIME, timeMillis)
    .put(KEY_APP_VERSION, environment.appVersion)
    .put(KEY_APP_BUILD, environment.appBuild)
    .put(KEY_ANDROID, environment.androidRelease)
    .put(KEY_SDK, environment.sdk)
    .put(KEY_DEVICE, environment.device)
    .put(KEY_DETAILS, details)
    .put(KEY_THREAD, thread ?: JSONObject.NULL)
    .put(KEY_ANNOUNCED, announced)
    .toString()

fun parseProblemReport(json: String): ProblemReport? = try {
    val root = JSONObject(json)
    val kind = ProblemKind.entries.firstOrNull { it.name == root.getString(KEY_KIND) }
    kind?.let { found ->
        ProblemReport(
            kind = found,
            timeMillis = root.getLong(KEY_TIME),
            environment = AppEnvironment(
                appVersion = root.getString(KEY_APP_VERSION),
                appBuild = root.getInt(KEY_APP_BUILD),
                androidRelease = root.getString(KEY_ANDROID),
                sdk = root.getInt(KEY_SDK),
                device = root.getString(KEY_DEVICE),
            ),
            details = root.getString(KEY_DETAILS).cappedDetails(),
            thread = if (root.isNull(KEY_THREAD)) null else root.optString(KEY_THREAD).ifEmpty { null },
            announced = root.optBoolean(KEY_ANNOUNCED, false),
        )
    }
} catch (_: JSONException) {
    null
}

private const val MAIN_THREAD_HEADER = "\"main\""
private const val KEY_KIND = "kind"
private const val KEY_TIME = "time"
private const val KEY_APP_VERSION = "appVersion"
private const val KEY_APP_BUILD = "appBuild"
private const val KEY_ANDROID = "android"
private const val KEY_SDK = "sdk"
private const val KEY_DEVICE = "device"
private const val KEY_DETAILS = "details"
private const val KEY_THREAD = "thread"
private const val KEY_ANNOUNCED = "announced"

package com.fortind.zenith.diagnostics

import android.os.Build
import com.fortind.zenith.BuildConfig

fun currentAppEnvironment(): AppEnvironment = AppEnvironment(
    appVersion = BuildConfig.VERSION_NAME,
    appBuild = BuildConfig.VERSION_CODE,
    androidRelease = Build.VERSION.RELEASE.orEmpty(),
    sdk = Build.VERSION.SDK_INT,
    device = listOf(Build.MANUFACTURER, Build.MODEL).filterNot { it.isNullOrBlank() }.joinToString(" "),
)

class CrashRecorder(
    private val store: ProblemReportStore,
    private val environment: AppEnvironment,
    private val clock: () -> Long = System::currentTimeMillis,
) : Thread.UncaughtExceptionHandler {
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current === this) return
        previous = current
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            store.write(
                ProblemReport(
                    kind = ProblemKind.Crash,
                    timeMillis = clock(),
                    environment = environment,
                    details = crashDetails(error),
                    thread = thread.name,
                )
            )
        } catch (_: Throwable) {
        }
        previous?.uncaughtException(thread, error)
    }
}

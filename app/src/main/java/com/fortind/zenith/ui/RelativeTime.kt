package com.fortind.zenith.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private const val CLOCK_TICK_MS = 60_000L

fun relativeTime(createdAtMillis: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(
        createdAtMillis,
        maxOf(now, createdAtMillis),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

fun relativeTimeFromNow(millis: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

fun absoluteTime(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
fun rememberNow(): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initialValue = System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = System.currentTimeMillis()
            while (true) {
                delay(CLOCK_TICK_MS)
                value = System.currentTimeMillis()
            }
        }
    }
}

package com.fortind.zenith.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

private const val CLOCK_TICK_MS = 60_000L

fun relativeTime(createdAtMillis: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(
        createdAtMillis,
        maxOf(now, createdAtMillis),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

@Composable
fun rememberNow(): State<Long> = produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
        delay(CLOCK_TICK_MS)
        value = System.currentTimeMillis()
    }
}

package com.fortind.zenith.composer

import com.fortind.zenith.account.codePointLength
import java.util.Calendar
import java.util.TimeZone

const val SCHEDULE_CW_LIMIT = 100
const val SCHEDULE_POLL_CHOICE_LIMIT = 50
const val SCHEDULE_MIN_LEAD_MILLIS = 60_000L

enum class ScheduleProblem { LocalOnly, ContentWarningTooLong, PollChoiceTooLong, PollEndsBeforePost, TooSoon }

fun scheduleProblem(localOnly: Boolean, contentWarning: String?, poll: PollDraft?): ScheduleProblem? = when {
    localOnly -> ScheduleProblem.LocalOnly
    contentWarning != null && contentWarning.codePointLength() > SCHEDULE_CW_LIMIT -> ScheduleProblem.ContentWarningTooLong
    poll != null && poll.cleanChoices.any { it.codePointLength() > SCHEDULE_POLL_CHOICE_LIMIT } -> ScheduleProblem.PollChoiceTooLong
    else -> null
}

fun scheduleTimeProblem(atMillis: Long, nowMillis: Long, poll: PollDraft?): ScheduleProblem? = when {
    atMillis < nowMillis + SCHEDULE_MIN_LEAD_MILLIS -> ScheduleProblem.TooSoon
    poll?.endsAtMillis != null && poll.endsAtMillis <= atMillis -> ScheduleProblem.PollEndsBeforePost
    else -> null
}

fun nextWholeHour(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val earliest = Calendar.getInstance(zone).apply { timeInMillis = nowMillis + SCHEDULE_MIN_LEAD_MILLIS }
    val onTheHour = earliest.get(Calendar.MINUTE) == 0 && earliest.get(Calendar.SECOND) == 0 && earliest.get(Calendar.MILLISECOND) == 0
    if (onTheHour) return earliest.timeInMillis
    return earliest.apply {
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.HOUR_OF_DAY, 1)
    }.timeInMillis
}

fun localDateAsUtcMillis(instantMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val local = Calendar.getInstance(zone).apply { timeInMillis = instantMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        timeInMillis
    }
}

fun hourOf(instantMillis: Long, zone: TimeZone = TimeZone.getDefault()): Int =
    Calendar.getInstance(zone).apply { timeInMillis = instantMillis }.get(Calendar.HOUR_OF_DAY)

fun minuteOf(instantMillis: Long, zone: TimeZone = TimeZone.getDefault()): Int =
    Calendar.getInstance(zone).apply { timeInMillis = instantMillis }.get(Calendar.MINUTE)

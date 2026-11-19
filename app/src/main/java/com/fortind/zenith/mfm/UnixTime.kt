package com.fortind.zenith.mfm

enum class AgoUnit { Years, Months, Weeks, Days, Hours, Minutes, Seconds }

sealed interface Ago {
    data object JustNow : Ago

    data class Past(val unit: AgoUnit, val count: Int) : Ago

    data class Ahead(val unit: AgoUnit, val count: Int) : Ago
}

private const val MINUTE = 60.0
private const val HOUR = 3_600.0
private const val DAY = 86_400.0
private const val WEEK = 604_800.0
private const val MONTH = 2_592_000.0
private const val YEAR = 31_536_000.0
private const val MIN_SECONDS = -62_135_596_800L
private const val MAX_SECONDS = 253_402_300_799L
private const val MAX_DIGITS = 12
private const val JUST_NOW_FROM = 10.0
private const val JUST_NOW_AHEAD = -3.0

fun parseUnixSeconds(content: String): Long? {
    var index = 0
    while (index < content.length && content[index].isWhitespace()) index++
    val negative = index < content.length && content[index] == '-'
    if (negative || (index < content.length && content[index] == '+')) index++
    val start = index
    while (index < content.length && content[index] in '0'..'9') index++
    val digits = index - start
    if (digits == 0 || digits > MAX_DIGITS) return null
    val magnitude = content.substring(start, index).toLongOrNull() ?: return null
    val seconds = if (negative) -magnitude else magnitude
    return seconds.takeIf { it in MIN_SECONDS..MAX_SECONDS }
}

fun ago(timeSeconds: Long, nowMillis: Long): Ago {
    val elapsed = (nowMillis - timeSeconds * 1_000L) / 1_000.0
    return when {
        elapsed >= YEAR -> Ago.Past(AgoUnit.Years, rounded(elapsed / YEAR))
        elapsed >= MONTH -> Ago.Past(AgoUnit.Months, rounded(elapsed / MONTH))
        elapsed >= WEEK -> Ago.Past(AgoUnit.Weeks, rounded(elapsed / WEEK))
        elapsed >= DAY -> Ago.Past(AgoUnit.Days, rounded(elapsed / DAY))
        elapsed >= HOUR -> Ago.Past(AgoUnit.Hours, rounded(elapsed / HOUR))
        elapsed >= MINUTE -> Ago.Past(AgoUnit.Minutes, (elapsed / MINUTE).toInt())
        elapsed >= JUST_NOW_FROM -> Ago.Past(AgoUnit.Seconds, (elapsed % MINUTE).toInt())
        elapsed >= JUST_NOW_AHEAD -> Ago.JustNow
        else -> ahead(-elapsed)
    }
}

private fun ahead(seconds: Double): Ago = when {
    seconds > YEAR -> Ago.Ahead(AgoUnit.Years, rounded(seconds / YEAR))
    seconds > MONTH -> Ago.Ahead(AgoUnit.Months, rounded(seconds / MONTH))
    seconds > WEEK -> Ago.Ahead(AgoUnit.Weeks, rounded(seconds / WEEK))
    seconds > DAY -> Ago.Ahead(AgoUnit.Days, rounded(seconds / DAY))
    seconds > HOUR -> Ago.Ahead(AgoUnit.Hours, rounded(seconds / HOUR))
    seconds > MINUTE -> Ago.Ahead(AgoUnit.Minutes, (seconds / MINUTE).toInt())
    else -> Ago.Ahead(AgoUnit.Seconds, (seconds % MINUTE).toInt())
}

private fun rounded(value: Double): Int = Math.floor(value + 0.5).toInt()

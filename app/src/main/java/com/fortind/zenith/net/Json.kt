package com.fortind.zenith.net

import java.util.Calendar
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

fun JSONObject.optIntOrNull(name: String): Int? =
    if (isNull(name) || !has(name)) null else optInt(name)

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { index ->
    if (isNull(index)) null else optString(index).takeIf { it.isNotEmpty() }
}

fun JSONObject.stringMap(): Map<String, String> = buildMap {
    keys().forEach { key -> optStringOrNull(key)?.let { put(key, it) } }
}

fun JSONObject.intMap(): Map<String, Int> = buildMap {
    keys().forEach { key -> if (!isNull(key)) put(key, optInt(key)) }
}

private val TimestampPattern =
    Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(?:Z|([+-])(\d{2}):?(\d{2}))$""")

fun parseTimestamp(text: String?): Long {
    val match = TimestampPattern.matchEntire(text ?: return 0L) ?: return 0L
    val (year, month, day, hour, minute, second, fraction, sign, offsetHours, offsetMinutes) = match.destructured
    if (month.toInt() !in 1..12 || day.toInt() !in 1..31 || hour.toInt() > 23 || minute.toInt() > 59 || second.toInt() > 59) return 0L
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
    }
    val millis = fraction.take(3).padEnd(3, '0').toInt()
    val offsetMillis = if (sign.isEmpty()) {
        0L
    } else {
        (offsetHours.toLong() * 60 + offsetMinutes.toLong()) * 60_000L * (if (sign == "-") -1 else 1)
    }
    return utc.timeInMillis + millis - offsetMillis
}

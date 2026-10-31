package com.fortind.zenith.net

import java.time.Instant
import java.time.format.DateTimeParseException
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

fun parseTimestamp(text: String?): Long = if (text == null) 0L else try {
    Instant.parse(text).toEpochMilli()
} catch (_: DateTimeParseException) {
    0L
}

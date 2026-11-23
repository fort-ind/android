package com.fortind.zenith.media

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

fun zoomStops(minRatio: Float, maxRatio: Float, lensRatios: Collection<Float>): List<Float> {
    if (maxRatio - minRatio < MIN_ZOOM_SPAN) return emptyList()
    val stops = mutableListOf<Float>()
    if (minRatio < WIDE_STOP_BELOW) stops += minRatio
    stops += 1f.coerceIn(minRatio, maxRatio)
    if (maxRatio >= 2f) stops += 2f
    lensRatios.filter { it in TELE_STOP_FROM..maxRatio }.maxOrNull()?.let { stops += it }
    val distinct = stops.sorted().fold(mutableListOf<Float>()) { kept, stop ->
        if (kept.isEmpty() || stop - kept.last() > STOP_TOLERANCE) kept += stop
        kept
    }
    return if (distinct.size < 2) emptyList() else distinct
}

fun activeZoomStop(stops: List<Float>, ratio: Float): Int {
    if (stops.isEmpty()) return -1
    return stops.indexOfLast { it <= ratio + STOP_TOLERANCE }.coerceAtLeast(0)
}

fun isAtZoomStop(stop: Float, ratio: Float): Boolean = abs(stop - ratio) <= STOP_TOLERANCE

fun keyZoom(current: Float, zoomIn: Boolean, minRatio: Float, maxRatio: Float): Float =
    steppedZoom(current, if (zoomIn) 1f else -1f, minRatio, maxRatio)

fun wheelZoom(current: Float, scrollY: Float, minRatio: Float, maxRatio: Float): Float =
    steppedZoom(current, -scrollY * WHEEL_STEPS_PER_NOTCH, minRatio, maxRatio)

private fun steppedZoom(current: Float, steps: Float, minRatio: Float, maxRatio: Float): Float =
    (current * ZOOM_STEP_FACTOR.pow(steps)).coerceIn(minRatio, maxRatio)

fun zoomText(ratio: Float, locale: Locale): String =
    DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale)).format((ratio * 10).roundToInt() / 10.0)

fun exposureValue(index: Int, stepNumerator: Int, stepDenominator: Int): Float =
    if (stepDenominator == 0) 0f else index * stepNumerator / stepDenominator.toFloat()

fun exposureText(value: Float, locale: Locale): String {
    val rounded = (value * 10).roundToInt() / 10.0
    if (rounded == 0.0) return DecimalFormat("0", DecimalFormatSymbols.getInstance(locale)).format(0)
    return DecimalFormat("+0.#;-0.#", DecimalFormatSymbols.getInstance(locale)).format(rounded)
}

private const val MIN_ZOOM_SPAN = 0.05f
private const val WIDE_STOP_BELOW = 0.95f
private const val TELE_STOP_FROM = 2.5f
private const val STOP_TOLERANCE = 0.05f
private const val ZOOM_STEP_FACTOR = 1.25f
private const val WHEEL_STEPS_PER_NOTCH = 0.5f

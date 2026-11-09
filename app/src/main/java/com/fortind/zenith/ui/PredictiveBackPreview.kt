package com.fortind.zenith.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private val PreviewEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private const val PreviewSeekRatio = 0.1f
private const val PreviewMinScale = 0.95f

fun predictiveBackSeekFraction(progress: Float): Float = PreviewEasing.transform(progress) * PreviewSeekRatio

fun predictiveBackScale(seekFraction: Float): Float {
    val delta = 1f - PreviewMinScale
    return delta * delta / 2f / (seekFraction + delta / 2f) + PreviewMinScale
}

private class LastValue<T>(var value: T?)

@Composable
fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { LastValue<T>(null) }
    if (value != null) last.value = value
    return last.value
}

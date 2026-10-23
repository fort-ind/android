package com.fortind.zenith.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A [Shape] that morphs to [target] with the theme's expressive spatial spring whenever it changes.
 * It starts at [start] and morphs to [target] on first composition. With [reduceMotion] it snaps instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberMorphingShape(
    target: RoundedPolygon,
    reduceMotion: Boolean,
    start: RoundedPolygon = target,
): Shape {
    var from by remember { mutableStateOf(if (reduceMotion) target else start) }
    var to by remember { mutableStateOf(from) }
    val progress = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(target, reduceMotion) {
        if (target == to && progress.value == 1f) return@LaunchedEffect
        if (reduceMotion) {
            from = target
            to = target
            progress.snapTo(1f)
        } else {
            // If a morph is interrupted halfway, continue from whichever shape is closer.
            from = if (progress.value < 0.5f) from else to
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        }
    }

    val morph = remember(from, to) { Morph(from, to) }
    return MorphShape(morph, progress.value)
}

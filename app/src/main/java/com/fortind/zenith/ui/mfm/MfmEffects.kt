package com.fortind.zenith.ui.mfm

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fortind.zenith.mfm.MfmFn
import com.fortind.zenith.mfm.toPlainText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val MAX_SCALE = 5f
private const val MAX_POSITION_EM = 5f

@Composable
internal fun MfmEffectBox(fn: MfmFn, environment: MfmEnvironment, style: TextStyle) {
    val fontPx = with(LocalDensity.current) {
        (style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp).toPx()
    }
    val effect = if (fn.name in AnimatedFunctions) Modifier.animatedEffect(fn, fontPx) else Modifier.staticEffect(fn, fontPx)
    Box(modifier = Modifier.fillMaxSize().then(effect), contentAlignment = Alignment.Center) {
        if (fn.name == "ruby") {
            Ruby(fn, style)
        } else {
            MfmParagraph(fn.children, environment, style, boxDepth = 1)
        }
    }
}

@Composable
private fun Ruby(fn: MfmFn, style: TextStyle) {
    val text = fn.children.toPlainText()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text.substringAfter(' ', ""), style = style.copy(fontSize = style.fontSize * 0.5f), maxLines = 1)
        Text(text.substringBefore(' '), style = style, maxLines = 1)
    }
}

private fun Modifier.staticEffect(fn: MfmFn, fontPx: Float): Modifier {
    val args = fn.args
    return this then when (fn.name) {
        "flip" -> {
            val vertical = "v" in args
            val horizontal = "h" in args || !vertical
            Modifier.graphicsLayer {
                scaleX = if (horizontal) -1f else 1f
                scaleY = if (vertical) -1f else 1f
            }
        }
        "rotate" -> {
            val degrees = args["deg"]?.toFloatOrNull() ?: 90f
            Modifier.graphicsLayer { rotationZ = degrees }
        }
        "scale" -> {
            val x = (args["x"]?.toFloatOrNull() ?: 1f).coerceIn(-MAX_SCALE, MAX_SCALE)
            val y = (args["y"]?.toFloatOrNull() ?: 1f).coerceIn(-MAX_SCALE, MAX_SCALE)
            Modifier.graphicsLayer {
                scaleX = x
                scaleY = y
            }
        }
        "position" -> {
            val x = (args["x"]?.toFloatOrNull() ?: 0f).coerceIn(-MAX_POSITION_EM, MAX_POSITION_EM)
            val y = (args["y"]?.toFloatOrNull() ?: 0f).coerceIn(-MAX_POSITION_EM, MAX_POSITION_EM)
            Modifier.graphicsLayer {
                translationX = x * fontPx
                translationY = y * fontPx
            }
        }
        "border" -> {
            val width = (args["width"]?.toFloatOrNull() ?: 1f).coerceIn(0f, 8f)
            val radius = (args["radius"]?.toFloatOrNull() ?: 0f).coerceIn(0f, 32f)
            val color = parseColor(args["color"]) ?: Color.Gray
            Modifier.border(width.dp, color, RoundedCornerShape(radius.dp))
        }
        else -> Modifier
    }
}

private fun parseDurationMs(value: String?, default: Int): Int {
    val text = value?.trim() ?: return default
    val millis = when {
        text.endsWith("ms") -> text.removeSuffix("ms").toFloatOrNull()
        text.endsWith("s") -> text.removeSuffix("s").toFloatOrNull()?.times(1000)
        else -> null
    } ?: return default
    return millis.toInt().coerceIn(100, 20_000)
}

@Composable
private fun Modifier.animatedEffect(fn: MfmFn, fontPx: Float): Modifier {
    val defaultMs = when (fn.name) {
        "spin" -> 1_500
        "jump", "bounce" -> 750
        "shake", "twitch" -> 500
        else -> 1_000
    }
    val duration = parseDurationMs(fn.args["speed"], defaultMs)
    val alternate = "alternate" in fn.args
    val spec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = duration, easing = LinearEasing),
        repeatMode = if (alternate) RepeatMode.Reverse else RepeatMode.Restart,
    )
    val progress: State<Float> = rememberInfiniteTransition(label = "mfm-${fn.name}").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = spec,
        label = "mfm-${fn.name}-progress",
    )
    return this then when (fn.name) {
        "spin" -> {
            val direction = if ("left" in fn.args) -1f else 1f
            Modifier.graphicsLayer {
                val degrees = progress.value * 360f * direction
                when {
                    "x" in fn.args -> rotationX = degrees
                    "y" in fn.args -> rotationY = degrees
                    else -> rotationZ = degrees
                }
            }
        }
        "jelly" -> Modifier.graphicsLayer {
            val wave = sin(progress.value * 2 * PI).toFloat() * (1f - progress.value)
            scaleX = 1f + wave * 0.25f
            scaleY = 1f - wave * 0.25f
        }
        "tada" -> Modifier.graphicsLayer {
            val t = progress.value
            val pulse = if (t < 0.2f) 0.9f else if (t < 0.9f) 1.1f else 1f
            scaleX = pulse * 1.5f
            scaleY = pulse * 1.5f
            rotationZ = if (t in 0.2f..0.9f) sin(t * 10 * PI).toFloat() * 3f else 0f
        }
        "jump" -> Modifier.graphicsLayer {
            val t = progress.value
            translationY = if (t < 0.5f) -sin(t * 2 * PI).toFloat() * fontPx * 0.75f else 0f
        }
        "bounce" -> Modifier.graphicsLayer {
            val t = progress.value
            val height = abs(sin(t * PI)).toFloat()
            translationY = -height * fontPx * 0.5f
            scaleY = if (t > 0.9f || t < 0.1f) 0.8f else 1f
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
        "shake" -> Modifier.graphicsLayer {
            val t = progress.value * 2 * PI
            translationX = sin(t * 5).toFloat() * fontPx * 0.08f
            translationY = sin(t * 7).toFloat() * fontPx * 0.06f
            rotationZ = sin(t * 3).toFloat() * 4f
        }
        "twitch" -> Modifier.graphicsLayer {
            val t = progress.value * 2 * PI
            translationX = sin(t * 13).toFloat() * fontPx * 0.12f
            translationY = sin(t * 11).toFloat() * fontPx * 0.12f
        }
        "rainbow" -> Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(color = Color.hsv(progress.value * 360f, 0.8f, 0.9f), blendMode = BlendMode.SrcIn)
            }
        "sparkle" -> Modifier.drawWithContent {
            drawContent()
            drawSparkles(progress.value)
        }
        else -> Modifier
    }
}

private val SparklePositions = listOf(Offset(0.1f, 0.2f), Offset(0.85f, 0.15f), Offset(0.5f, 0.85f), Offset(0.9f, 0.75f))

private fun DrawScope.drawSparkles(progress: Float) {
    SparklePositions.forEachIndexed { index, position ->
        val phase = (progress + index / SparklePositions.size.toFloat()) % 1f
        val alpha = sin(phase * PI).toFloat()
        val radius = size.minDimension * 0.18f * alpha
        if (radius <= 0f) return@forEachIndexed
        val center = Offset(size.width * position.x, size.height * position.y)
        val star = Path().apply {
            moveTo(center.x, center.y - radius)
            quadraticTo(center.x, center.y, center.x + radius, center.y)
            quadraticTo(center.x, center.y, center.x, center.y + radius)
            quadraticTo(center.x, center.y, center.x - radius, center.y)
            quadraticTo(center.x, center.y, center.x, center.y - radius)
            close()
        }
        drawPath(star, color = lerp(Color.White, Color(0xFFFFD54F), alpha), alpha = alpha)
    }
}

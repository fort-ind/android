package com.fortind.zenith.ui.mfm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fortind.zenith.mfm.MfmBold
import com.fortind.zenith.mfm.MfmContainer
import com.fortind.zenith.mfm.MfmEmojiCode
import com.fortind.zenith.mfm.MfmFn
import com.fortind.zenith.mfm.MfmHashtag
import com.fortind.zenith.mfm.MfmInlineCode
import com.fortind.zenith.mfm.MfmItalic
import com.fortind.zenith.mfm.MfmLink
import com.fortind.zenith.mfm.MfmMathInline
import com.fortind.zenith.mfm.MfmMention
import com.fortind.zenith.mfm.MfmNode
import com.fortind.zenith.mfm.MfmSmall
import com.fortind.zenith.mfm.MfmStrike
import com.fortind.zenith.mfm.MfmText
import com.fortind.zenith.mfm.MfmUrl
import com.fortind.zenith.mfm.toPlainText
import com.fortind.zenith.ui.media.rememberImageRequest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private const val EMOJI_SCALE = 1.25f
private const val BOX_PADDING_SP = 2f

object EmojiAspects {
    private val ratios = ConcurrentHashMap<String, Float>()
    var version by mutableIntStateOf(0)
        private set

    fun ratio(url: String): Float = ratios[url] ?: 1f

    fun record(url: String, ratio: Float) {
        if (ratio <= 0f || ratio.isNaN()) return
        val previous = ratios.put(url, ratio) ?: 1f
        if (abs(previous - ratio) > 0.05f) version++
    }
}

internal val AnimatedFunctions = setOf("tada", "jelly", "jump", "bounce", "spin", "shake", "twitch", "rainbow", "sparkle")
private val BoxedFunctions = setOf("flip", "rotate", "scale", "position", "border", "ruby")

internal class MfmAnnotatedBuilder(
    private val environment: MfmEnvironment,
    private val baseStyle: TextStyle,
    private val measurer: TextMeasurer,
    private val revealed: Set<Int>,
    private val onReveal: (Int) -> Unit,
    private val boxDepth: Int,
) {
    private val builder = AnnotatedString.Builder()
    private val inline = LinkedHashMap<String, InlineTextContent>()
    private var boxCount = 0
    private var blurCount = 0
    private val baseSize: Float = baseStyle.fontSize.takeIf { it != TextUnit.Unspecified }?.value ?: 16f

    fun build(nodes: List<MfmNode>): BuiltText {
        append(nodes, 1f)
        return BuiltText(builder.toAnnotatedString(), inline)
    }

    private fun append(nodes: List<MfmNode>, scale: Float) {
        nodes.forEach { append(it, scale) }
    }

    private fun append(node: MfmNode, scale: Float) {
        when (node) {
            is MfmText -> builder.append(node.text)
            is MfmBold -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(node.children, scale) }
            is MfmItalic -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(node.children, scale) }
            is MfmStrike -> builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(node.children, scale) }
            is MfmSmall -> builder.withStyle(SpanStyle(fontSize = (baseSize * scale * 0.8f).sp, color = environment.subtleColor)) {
                append(node.children, scale * 0.8f)
            }
            is MfmInlineCode -> builder.withStyle(codeStyle()) { append(node.code) }
            is MfmMathInline -> builder.withStyle(codeStyle()) { append(node.formula) }
            is MfmEmojiCode -> appendEmoji(node.name, scale)
            is MfmMention -> builder.withLink(linkAnnotation(environment.mentionUrl(node), environment)) { append(node.acct) }
            is MfmHashtag -> builder.withLink(linkAnnotation(environment.hashtagUrl(node.tag), environment)) { append("#${node.tag}") }
            is MfmUrl -> builder.withLink(linkAnnotation(node.url, environment)) { append(displayUrl(node.url)) }
            is MfmLink -> builder.withLink(linkAnnotation(node.url, environment)) { append(node.children, scale) }
            is MfmFn -> appendFunction(node, scale)
            is MfmContainer -> append(node.children, scale)
            else -> builder.append(listOf(node).toPlainText())
        }
    }

    private fun codeStyle() = SpanStyle(fontFamily = FontFamily.Monospace, background = environment.codeBackground)

    private fun displayUrl(url: String): String = url.removePrefix("https://").removePrefix("http://").let {
        if (it.length > 48) it.take(45) + "…" else it
    }

    private fun appendEmoji(name: String, scale: Float) {
        val url = environment.emojiUrl(name)
        val heightSp = baseSize * scale * EMOJI_SCALE
        val widthSp = heightSp * EmojiAspects.ratio(url)
        val key = "e:$name:$scale"
        if (key !in inline) {
            inline[key] = InlineTextContent(
                Placeholder(widthSp.sp, heightSp.sp, PlaceholderVerticalAlign.TextCenter),
            ) { CustomEmojiImage(name, url, environment.animate) }
        }
        builder.appendInlineContent(key, ":$name:")
    }

    private fun appendFunction(fn: MfmFn, scale: Float) {
        when (fn.name) {
            "x2", "x3", "x4" -> {
                val factor = fn.name.substring(1).toFloat()
                builder.withStyle(SpanStyle(fontSize = (baseSize * scale * factor).sp)) { append(fn.children, scale * factor) }
            }
            "fg" -> builder.withStyle(SpanStyle(color = parseColor(fn.args["color"]) ?: Color.Unspecified)) { append(fn.children, scale) }
            "bg" -> builder.withStyle(SpanStyle(background = parseColor(fn.args["color"]) ?: Color.Unspecified)) { append(fn.children, scale) }
            "font" -> builder.withStyle(SpanStyle(fontFamily = fontFamily(fn.args.keys))) { append(fn.children, scale) }
            "blur" -> appendSpoiler(fn, scale)
            "unixtime" -> builder.append(formatUnixTime(fn.children.toPlainText()) ?: fn.children.toPlainText())
            else -> when {
                fn.name in AnimatedFunctions && environment.animate && boxDepth == 0 -> appendBox(fn, scale)
                fn.name == "tada" -> builder.withStyle(SpanStyle(fontSize = (baseSize * scale * 1.5f).sp)) { append(fn.children, scale * 1.5f) }
                fn.name in BoxedFunctions && boxDepth == 0 -> appendBox(fn, scale)
                fn.name == "ruby" -> appendRubyInline(fn, scale)
                else -> append(fn.children, scale)
            }
        }
    }

    private fun appendSpoiler(fn: MfmFn, scale: Float) {
        val index = blurCount++
        if (index in revealed) {
            append(fn.children, scale)
            return
        }
        val hidden = SpanStyle(color = Color.Transparent, background = environment.spoilerColor)
        builder.withLink(LinkAnnotation.Clickable("blur:$index") { onReveal(index) }) {
            builder.withStyle(hidden) { append(fn.children.toPlainText()) }
        }
    }

    private fun appendRubyInline(fn: MfmFn, scale: Float) {
        val text = fn.children.toPlainText()
        val base = text.substringBefore(' ')
        val reading = text.substringAfter(' ', "")
        builder.append(base)
        if (reading.isNotEmpty()) {
            builder.withStyle(SpanStyle(fontSize = (baseSize * scale * 0.6f).sp, color = environment.subtleColor)) { append("($reading)") }
        }
    }

    private fun appendBox(fn: MfmFn, scale: Float) {
        val key = "b:${boxCount++}"
        val sizeSp = baseSize * scale
        val measureText = measuringText(fn.children)
        val style = baseStyle.copy(fontSize = sizeSp.sp)
        val layout = measurer.measure(AnnotatedString(measureText), style, softWrap = false, maxLines = 1)
        val density = layout.layoutInput.density
        val toSp = { px: Int -> px / (density.density * density.fontScale) }
        var widthSp = toSp(layout.size.width) + BOX_PADDING_SP * 2
        var heightSp = maxOf(toSp(layout.size.height), sizeSp * EMOJI_SCALE) + BOX_PADDING_SP * 2
        if (fn.name == "ruby") {
            val reading = measureText.substringAfter(' ', "")
            val base = measureText.substringBefore(' ')
            val baseWidth = toSp(measurer.measure(AnnotatedString(base), style, softWrap = false).size.width)
            val readingWidth = toSp(measurer.measure(AnnotatedString(reading), style.copy(fontSize = (sizeSp * 0.5f).sp), softWrap = false).size.width)
            widthSp = maxOf(baseWidth, readingWidth) + BOX_PADDING_SP * 2
            heightSp += sizeSp * 0.6f
        }
        inline[key] = InlineTextContent(
            Placeholder(widthSp.sp, heightSp.sp, PlaceholderVerticalAlign.TextCenter),
        ) {
            MfmEffectBox(fn, environment, style)
        }
        builder.appendInlineContent(key, fn.children.toPlainText())
    }

    private fun measuringText(nodes: List<MfmNode>): String = buildString {
        fun walk(list: List<MfmNode>) {
            list.forEach { node ->
                when (node) {
                    is MfmEmojiCode -> append("　")
                    is MfmContainer -> walk(node.children)
                    else -> append(listOf(node).toPlainText())
                }
            }
        }
        walk(nodes)
    }

    private fun fontFamily(keys: Set<String>): FontFamily? = when {
        "serif" in keys -> FontFamily.Serif
        "monospace" in keys -> FontFamily.Monospace
        "cursive" in keys -> FontFamily.Cursive
        "fantasy" in keys -> FontFamily.SansSerif
        else -> null
    }
}

internal fun parseColor(value: String?): Color? {
    val hex = value?.trim()?.removePrefix("#") ?: return null
    if (hex.isEmpty() || hex.any { it !in "0123456789abcdefABCDEF" }) return null
    val expanded = when (hex.length) {
        3, 4 -> hex.map { "$it$it" }.joinToString("")
        6, 8 -> hex
        else -> return null
    }
    val rgb = expanded.substring(0, 6).toLong(16)
    val alpha = if (expanded.length == 8) expanded.substring(6, 8).toLong(16) else 0xFF
    return Color((alpha shl 24 or rgb).toInt())
}

internal fun formatUnixTime(seconds: String): String? {
    val value = seconds.trim().toLongOrNull() ?: return null
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value * 1000))
}

@Composable
fun CustomEmojiImage(name: String, url: String, animate: Boolean, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(":$name:", maxLines = 1) }
        return
    }
    AsyncImage(
        model = rememberImageRequest(url, animate),
        contentDescription = ":$name:",
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) EmojiAspects.record(url, size.width / size.height)
        },
        onError = { failed = true },
        modifier = modifier.fillMaxSize(),
    )
}

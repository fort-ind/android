package com.fortind.zenith.ui.mfm

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.mfm.MfmBlockCode
import com.fortind.zenith.mfm.MfmCenter
import com.fortind.zenith.mfm.MfmMathBlock
import com.fortind.zenith.mfm.MfmMention
import com.fortind.zenith.mfm.MfmNode
import com.fortind.zenith.mfm.MfmQuote
import com.fortind.zenith.mfm.MfmSearch
import com.fortind.zenith.mfm.isBlock
import java.net.URLEncoder

class MfmEnvironment(
    val emojiUrl: (String) -> String,
    val animate: Boolean,
    val linkStyles: TextLinkStyles,
    val codeBackground: Color,
    val subtleColor: Color,
    val accentColor: Color,
    val spoilerColor: Color,
) {
    fun mentionUrl(mention: MfmMention): String = "https://${FortInstance.HOST}/${mention.acct}"

    fun hashtagUrl(tag: String): String = "https://${FortInstance.HOST}/tags/${URLEncoder.encode(tag, "UTF-8")}"

    fun searchUrl(query: String): String = "https://${FortInstance.HOST}/search?q=${URLEncoder.encode(query, "UTF-8")}"

    fun withEmoji(emojiUrl: (String) -> String) =
        MfmEnvironment(emojiUrl, animate, linkStyles, codeBackground, subtleColor, accentColor, spoilerColor)
}

@Composable
fun rememberMfmEnvironment(emojiUrl: (String) -> String, animate: Boolean): MfmEnvironment {
    val colors = MaterialTheme.colorScheme
    return remember(emojiUrl, animate, colors) {
        val link = SpanStyle(color = colors.primary)
        MfmEnvironment(
            emojiUrl = emojiUrl,
            animate = animate,
            linkStyles = TextLinkStyles(
                style = link,
                focusedStyle = link.copy(background = colors.primary.copy(alpha = 0.16f)),
                hoveredStyle = link.copy(textDecoration = TextDecoration.Underline),
                pressedStyle = link.copy(background = colors.primary.copy(alpha = 0.24f)),
            ),
            codeBackground = colors.surfaceContainerHighest,
            subtleColor = colors.onSurfaceVariant,
            accentColor = colors.primary,
            spoilerColor = colors.onSurfaceVariant,
        )
    }
}

private sealed interface MfmBlock {
    data class Paragraph(val nodes: List<MfmNode>) : MfmBlock

    data class Block(val node: MfmNode) : MfmBlock
}

private fun groupBlocks(nodes: List<MfmNode>): List<MfmBlock> {
    val blocks = mutableListOf<MfmBlock>()
    val paragraph = mutableListOf<MfmNode>()
    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += MfmBlock.Paragraph(paragraph.toList())
            paragraph.clear()
        }
    }
    nodes.forEach { node ->
        if (node.isBlock) {
            flush()
            blocks += MfmBlock.Block(node)
        } else {
            paragraph += node
        }
    }
    flush()
    return blocks
}

@Composable
fun MfmContent(
    nodes: List<MfmNode>,
    environment: MfmEnvironment,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val blocks = remember(nodes) { groupBlocks(nodes) }
    val single = blocks.singleOrNull()
    if (single is MfmBlock.Paragraph) {
        MfmParagraph(single.nodes, environment, style, modifier, color, maxLines)
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MfmBlock.Paragraph -> MfmParagraph(block.nodes, environment, style, color = color)
                is MfmBlock.Block -> MfmBlockView(block.node, environment, style, color)
            }
        }
    }
}

@Composable
private fun MfmBlockView(node: MfmNode, environment: MfmEnvironment, style: TextStyle, color: Color) {
    when (node) {
        is MfmQuote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(environment.accentColor.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            )
            MfmContent(
                nodes = node.children,
                environment = environment,
                style = style,
                color = environment.subtleColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        is MfmCenter -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MfmParagraph(node.children, environment, style.copy(textAlign = TextAlign.Center), color = color)
        }
        is MfmBlockCode -> CodeSurface(node.code)
        is MfmMathBlock -> CodeSurface(node.formula)
        is MfmSearch -> SearchRow(node.query, environment)
        else -> Unit
    }
}

@Composable
private fun CodeSurface(code: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

@Composable
private fun SearchRow(query: String, environment: MfmEnvironment) {
    val uriHandler = LocalUriHandler.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { uriHandler.openUri(environment.searchUrl(query)) }) {
                Icon(Icons.Default.Search, contentDescription = null)
                Text(stringResource(R.string.mfm_search), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun MfmParagraph(
    nodes: List<MfmNode>,
    environment: MfmEnvironment,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    boxDepth: Int = 0,
) {
    val measurer = rememberTextMeasurer()
    var revealed by remember(nodes) { mutableStateOf(emptySet<Int>()) }
    val aspectVersion = EmojiAspects.version
    val built = remember(nodes, environment, style, revealed, boxDepth, aspectVersion) {
        MfmAnnotatedBuilder(
            environment = environment,
            baseStyle = style,
            measurer = measurer,
            revealed = revealed,
            onReveal = { revealed = revealed + it },
            boxDepth = boxDepth,
        ).build(nodes)
    }
    Text(
        text = built.text,
        inlineContent = built.inline,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        softWrap = boxDepth == 0,
        modifier = modifier,
    )
}

internal class BuiltText(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

internal fun linkAnnotation(url: String, environment: MfmEnvironment) = LinkAnnotation.Url(url, environment.linkStyles)

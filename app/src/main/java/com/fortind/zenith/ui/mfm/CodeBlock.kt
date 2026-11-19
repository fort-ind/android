package com.fortind.zenith.ui.mfm

import android.content.ClipData
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.code.CodeHighlights
import com.fortind.zenith.code.expandTabs
import com.fortind.zenith.ui.focusRing
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.horizontalScrollWithBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COPIED_MS = 1_500L
private val ArrowScroll = 80.dp

@Composable
internal fun CodeBlock(code: String, language: String?) {
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val colors = remember(background) { codeColors(background) }
    var highlight by remember(code, language) { mutableStateOf(CodeHighlights.peek(code, language)) }
    LaunchedEffect(code, language) {
        if (highlight == null) highlight = CodeHighlights.highlight(code, language)
    }
    val display = highlight?.text ?: remember(code) { expandTabs(code) }
    val text = remember(highlight, display, colors) {
        buildAnnotatedString {
            append(display)
            highlight?.tokens?.forEach { token ->
                val color = colors.byKind[token.kind]
                if (color != null && token.start >= 0 && token.end <= display.length && token.start < token.end) {
                    addStyle(SpanStyle(color = color), token.start, token.end)
                }
            }
        }
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { ArrowScroll.toPx() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = background,
        modifier = Modifier.fillMaxWidth().testTag("mfm:code"),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            ) {
                Text(
                    text = highlight?.label.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                CopyButton(code)
            }
            Box(
                modifier = Modifier
                    .focusRing(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                    .focusable()
                    .onKeyEvent { event ->
                        val direction = when (event.key) {
                            Key.DirectionRight -> 1f
                            Key.DirectionLeft -> -1f
                            else -> return@onKeyEvent false
                        }
                        if (event.type != KeyEventType.KeyDown || scroll.maxValue == 0) return@onKeyEvent false
                        scope.launch { scroll.animateScrollBy(direction * step) }
                        true
                    },
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    softWrap = false,
                    modifier = Modifier
                        .horizontalScrollWithBar(scroll)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CopyButton(code: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    val copy = stringResource(R.string.code_copy)
    TooltipIconButton(
        label = if (copied) stringResource(R.string.code_copied) else copy,
        icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copy, code)))
                copied = true
            }
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

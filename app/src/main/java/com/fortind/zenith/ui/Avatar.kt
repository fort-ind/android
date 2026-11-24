package com.fortind.zenith.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.text.BreakIterator

@Composable
fun Avatar(name: String, url: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = remember(name) { avatarInitial(name) },
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val CustomEmojiCode = Regex(""":[\p{L}\p{N}_+\-]+(?:@[\p{L}\p{N}.\-]+)?:""")

internal fun avatarInitial(name: String): String {
    val text = CustomEmojiCode.replace(name, " ")
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (Character.isLetterOrDigit(codePoint)) {
            var end = index + Character.charCount(codePoint)
            while (end < text.length && isAttachedMark(text.codePointAt(end))) end += Character.charCount(text.codePointAt(end))
            return text.substring(index, end).uppercase()
        }
        index += Character.charCount(codePoint)
    }
    return firstVisibleCluster(text)
}

private fun isAttachedMark(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
    Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
    else -> false
}

private fun firstVisibleCluster(text: String): String {
    val clusters = BreakIterator.getCharacterInstance().apply { setText(text) }
    var start = clusters.first()
    var end = clusters.next()
    while (end != BreakIterator.DONE) {
        val cluster = text.substring(start, end)
        if (cluster.isNotBlank()) return cluster
        start = end
        end = clusters.next()
    }
    return ""
}

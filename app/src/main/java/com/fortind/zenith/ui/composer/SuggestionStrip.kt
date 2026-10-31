package com.fortind.zenith.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.autocomplete.CustomEmojiSuggestion
import com.fortind.zenith.autocomplete.HashtagSuggestion
import com.fortind.zenith.autocomplete.Suggestion
import com.fortind.zenith.autocomplete.SuggestionsState
import com.fortind.zenith.autocomplete.UnicodeEmojiSuggestion
import com.fortind.zenith.autocomplete.UserSuggestion
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.mfm.CustomEmojiImage

@Composable
fun SuggestionStrip(
    state: SuggestionsState,
    animate: Boolean,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.selected, state.items.size) {
        if (animate) listState.animateScrollToItem(state.selected) else listState.scrollToItem(state.selected)
    }
    val summary = pluralStringResource(R.plurals.composer_suggestions, state.items.size, state.items.size)
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.semantics { paneTitle = summary },
    ) {
        itemsIndexed(state.items, key = { _, item -> item.key }, contentType = { _, item -> item::class }) { index, item ->
            SuggestionItem(item, selected = index == state.selected, animate = animate, onClick = { onPick(index) })
        }
    }
}

@Composable
private fun SuggestionItem(item: Suggestion, selected: Boolean, animate: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = item.description()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        modifier = Modifier.semantics {
            role = Role.Button
            contentDescription = description
            this.selected = selected
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = ItemMaxWidth).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            when (item) {
                is UserSuggestion -> {
                    Avatar(name = item.user.displayName, url = item.user.avatarUrl, size = 28.dp)
                    Column {
                        Text(
                            item.user.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.user.handle,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                is HashtagSuggestion -> Text("#${item.tag}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                is CustomEmojiSuggestion -> {
                    Box(modifier = Modifier.size(24.dp)) { CustomEmojiImage(item.emoji.name, item.emoji.url, animate) }
                    Text(":${item.emoji.name}:", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                is UnicodeEmojiSuggestion -> {
                    Text(item.emoji.emoji, style = MaterialTheme.typography.titleMedium)
                    Text(item.emoji.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Suggestion.description(): String = when (this) {
    is UserSuggestion -> stringResource(R.string.composer_suggestion_mention, user.displayName, user.handle)
    is HashtagSuggestion -> stringResource(R.string.composer_suggestion_hashtag, tag)
    is CustomEmojiSuggestion -> stringResource(R.string.composer_suggestion_emoji, emoji.name)
    is UnicodeEmojiSuggestion -> stringResource(R.string.composer_suggestion_emoji, emoji.name)
}

private val ItemMaxWidth = 240.dp

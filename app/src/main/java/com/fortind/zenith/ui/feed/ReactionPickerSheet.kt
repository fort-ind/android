package com.fortind.zenith.ui.feed

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.emoji.CustomEmoji
import com.fortind.zenith.emoji.EmojiIndex
import com.fortind.zenith.emoji.UnicodeEmojiGroup
import com.fortind.zenith.emoji.search
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.Reactions
import com.fortind.zenith.timeline.acceptsSensitiveReactions
import com.fortind.zenith.ui.mfm.CustomEmojiImage
import com.fortind.zenith.ui.pointerHover

private sealed interface PickerEntry {
    val reaction: String

    data class Custom(val emoji: CustomEmoji) : PickerEntry {
        override val reaction: String get() = ":${emoji.name}:"
    }

    data class Unicode(val emoji: String, val name: String) : PickerEntry {
        override val reaction: String get() = emoji
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionPickerSheet(
    note: Note,
    environment: NoteEnvironment,
    emojis: EmojiIndex?,
    unicode: List<UnicodeEmojiGroup>,
    recent: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var query by rememberSaveable { mutableStateOf("") }
    val allowSensitive = note.acceptsSensitiveReactions()
    val custom = remember(emojis, allowSensitive) {
        emojis?.emojis?.filter { !it.restricted && (allowSensitive || !it.isSensitive) }.orEmpty()
    }
    val customNames = remember(custom) { custom.mapTo(HashSet()) { it.name } }
    val results = remember(query, custom, unicode) {
        if (query.isBlank()) {
            emptyList()
        } else {
            EmojiIndex(custom).search(query).map { PickerEntry.Custom(it) } +
                unicode.search(query).map { PickerEntry.Unicode(it.emoji, it.name) }
        }
    }
    val hasKeyboard = LocalConfiguration.current.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(hasKeyboard) { if (hasKeyboard) focusRequester.requestFocus() }
    val recentLabel = stringResource(R.string.picker_recent)
    val otherLabel = stringResource(R.string.picker_other)
    val pick = { reaction: String ->
        onPick(reaction)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.picker_search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { results.firstOrNull()?.let { pick(it.reaction) } }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            results.firstOrNull()?.let { pick(it.reaction) }
                            true
                        } else {
                            false
                        }
                    },
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(52.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (query.isNotBlank()) {
                    if (results.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            Text(
                                stringResource(R.string.picker_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    entries("results", results, environment, pick)
                    return@LazyVerticalGrid
                }
                if (recent.isNotEmpty()) {
                    header("recent", recentLabel)
                    entries("recent", recent.map { reaction -> recentEntry(reaction, emojis) }, environment, pick)
                }
                emojis?.categories?.forEach { (category, list) ->
                    val visible = list.filter { it.name in customNames }
                    if (visible.isEmpty()) return@forEach
                    header("custom:$category", category ?: otherLabel)
                    entries("custom:$category", visible.map { PickerEntry.Custom(it) }, environment, pick)
                }
                unicode.forEach { group ->
                    header("unicode:${group.name}", group.name)
                    entries("unicode:${group.name}", group.emoji.map { PickerEntry.Unicode(it.emoji, it.name) }, environment, pick)
                }
            }
        }
    }
}

private fun recentEntry(reaction: String, emojis: EmojiIndex?): PickerEntry {
    val name = Reactions.customName(reaction)
    val emoji = name?.let { emojis?.get(it) }
    return if (emoji != null) PickerEntry.Custom(emoji) else PickerEntry.Unicode(reaction, reaction)
}

private fun LazyGridScope.header(key: String, title: String) {
    item(key = "header:$key", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
        )
    }
}

private fun LazyGridScope.entries(
    section: String,
    entries: List<PickerEntry>,
    environment: NoteEnvironment,
    onPick: (String) -> Unit,
) {
    items(entries, key = { "$section:${it.reaction}" }, contentType = { if (it is PickerEntry.Custom) "custom" else "unicode" }) { entry ->
        PickerCell(entry, environment, onPick)
    }
}

@Composable
private fun PickerCell(entry: PickerEntry, environment: NoteEnvironment, onPick: (String) -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val label = when (entry) {
        is PickerEntry.Custom -> ":${entry.emoji.name}:"
        is PickerEntry.Unicode -> entry.name
    }
    Surface(
        onClick = { onPick(entry.reaction) },
        shape = MaterialTheme.shapes.medium,
        color = if (hovered) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .padding(2.dp)
            .size(48.dp)
            .pointerHover { hovered = it }
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (entry) {
                is PickerEntry.Custom -> Box(modifier = Modifier.size(32.dp)) {
                    CustomEmojiImage(entry.emoji.name, entry.emoji.url, environment.animate)
                }
                is PickerEntry.Unicode -> Text(entry.emoji, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.discovery

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.DpOffset
import com.fortind.zenith.mfm.MfmText
import com.fortind.zenith.ui.contextMenuTrigger
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.people.PeopleActions
import com.fortind.zenith.ui.people.PersonMenuItems
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.R
import com.fortind.zenith.discovery.SearchIntent
import com.fortind.zenith.discovery.searchIntent
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.pointerHover
import com.fortind.zenith.ui.rememberReduceMotion
import kotlinx.coroutines.launch

class SearchActions(
    val openTag: (String) -> Unit,
    val openPerson: (NoteUser) -> Unit,
    val openHandle: (String, String?) -> Unit,
    val openNotes: (String) -> Unit,
    val logIn: () -> Unit,
    val people: PeopleActions,
)

@Composable
fun rememberSearchInput(
    state: SearchBarState,
    text: TextFieldState,
    signedIn: Boolean,
    actions: SearchActions,
): @Composable () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(state, text, signedIn, actions) {
        @Composable {
            val close = { scope.launch { state.animateToCollapsed() } }
            SearchBarDefaults.InputField(
                textFieldState = text,
                searchBarState = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search:input")
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.key != Key.Escape || state.currentValue != SearchBarValue.Expanded) {
                            return@onPreviewKeyEvent false
                        }
                        if (text.text.isNotEmpty()) text.clearText() else close()
                        true
                    },
                onSearch = { submitted ->
                    val intent = searchIntent(submitted, signedIn)
                    if (intent != null && intent != SearchIntent.SignInNeeded) {
                        scope.launch {
                            state.snapTo(0f)
                            text.clearText()
                            when (intent) {
                                is SearchIntent.Tag -> actions.openTag(intent.tag)
                                is SearchIntent.Person -> actions.openHandle(intent.username, intent.host)
                                is SearchIntent.Notes -> actions.openNotes(intent.query)
                                SearchIntent.SignInNeeded -> Unit
                            }
                        }
                    }
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                },
                leadingIcon = {
                    if (state.currentValue == SearchBarValue.Expanded) {
                        TooltipIconButton(
                            label = stringResource(R.string.search_back),
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = { close() },
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (text.text.isNotEmpty()) {
                        TooltipIconButton(
                            label = stringResource(R.string.search_clear),
                            icon = Icons.Default.Close,
                            onClick = { text.clearText() },
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun CollapsedSearchField(state: SearchBarState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.search_placeholder)
    Surface(
        onClick = { scope.launch { state.animateToExpanded() } },
        color = Color.Transparent,
        shape = SearchBarDefaults.inputFieldShape,
        modifier = modifier
            .fillMaxWidth()
            .height(SearchBarDefaults.InputFieldHeight)
            .testTag("search:field")
            .semantics { contentDescription = label },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
fun SearchOverlay(
    state: SearchBarState,
    text: TextFieldState,
    inputField: @Composable () -> Unit,
    dockedWidth: Dp,
    signedIn: Boolean,
    discovery: DiscoveryViewModel,
    actions: SearchActions,
) {
    val scope = rememberCoroutineScope()
    val compact = !currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val pick: (() -> Unit) -> Unit = { open ->
        scope.launch {
            state.snapTo(0f)
            text.clearText()
            open()
        }
    }
    val content = @Composable {
        SearchContent(text, signedIn, discovery, actions, pick)
    }
    LaunchedEffect(state, discovery) {
        snapshotFlow { state.currentValue == SearchBarValue.Expanded }.collect { expanded ->
            if (expanded) discovery.loadHashtags()
        }
    }
    LaunchedEffect(text, discovery) {
        snapshotFlow { text.text.toString() }.collect(discovery::setQuery)
    }
    if (compact) {
        ExpandedFullScreenContainedSearchBar(state = state, inputField = inputField) { content() }
    } else {
        ExpandedDockedSearchBar(
            state = state,
            inputField = inputField,
            modifier = if (dockedWidth.isSpecified) Modifier.width(dockedWidth) else Modifier,
        ) { content() }
    }
}

@Composable
private fun SearchContent(
    text: TextFieldState,
    signedIn: Boolean,
    discovery: DiscoveryViewModel,
    actions: SearchActions,
    pick: (() -> Unit) -> Unit,
) {
    val state by discovery.uiState.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    val query = text.text.toString().trim()
    LazyColumn(modifier = Modifier.fillMaxWidth().testTag("search:results")) {
        if (query.isEmpty()) {
            item(key = "popular:title", contentType = "title") { SearchHeading(stringResource(R.string.search_popular_hashtags)) }
            when (state.hashtagStatus) {
                CollectionsStatus.Failed -> item(key = "popular:failed", contentType = "message") {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { discovery.loadHashtags(force = true) }) { Text(stringResource(R.string.search_hashtags_retry)) }
                    }
                }
                CollectionsStatus.Idle, CollectionsStatus.Loading -> item(key = "popular:loading", contentType = "message") {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        if (reduceMotion) Text(stringResource(R.string.search_loading)) else LoadingIndicator()
                    }
                }
                CollectionsStatus.Ready -> items(state.hashtags, key = { "popular:${it.tag}" }, contentType = { "tag" }) { hashtag ->
                    SearchRow(
                        headline = "#${hashtag.tag}",
                        supporting = pluralStringResource(R.plurals.hashtag_used_by, hashtag.usedBy, hashtag.usedBy),
                        leading = { Icon(Icons.Default.Tag, contentDescription = null) },
                        onClick = { pick { actions.openTag(hashtag.tag) } },
                    )
                }
            }
            return@LazyColumn
        }
        when (val intent = searchIntent(query, signedIn)) {
            is SearchIntent.Notes -> item(key = "notes", contentType = "row") {
                SearchRow(
                    headline = stringResource(R.string.search_notes_for, intent.query),
                    leading = { Icon(Icons.Default.Search, contentDescription = null) },
                    onClick = { pick { actions.openNotes(intent.query) } },
                    modifier = Modifier.testTag("search:notes"),
                )
            }
            is SearchIntent.Tag -> item(key = "tag", contentType = "row") {
                SearchRow(
                    headline = "#${intent.tag}",
                    leading = { Icon(Icons.Default.Tag, contentDescription = null) },
                    onClick = { pick { actions.openTag(intent.tag) } },
                )
            }
            is SearchIntent.Person -> item(key = "handle", contentType = "row") {
                SearchRow(
                    headline = query,
                    leading = { Icon(Icons.Default.Search, contentDescription = null) },
                    onClick = { pick { actions.openHandle(intent.username, intent.host) } },
                )
            }
            SearchIntent.SignInNeeded -> item(key = "guest", contentType = "row") {
                SearchRow(
                    headline = stringResource(R.string.search_sign_in),
                    leading = { Icon(Icons.Default.Search, contentDescription = null) },
                    onClick = { pick(actions.logIn) },
                )
            }
            null -> Unit
        }
        val suggestions = state.suggestions.takeIf { it.query == query }
        items(suggestions?.tags.orEmpty(), key = { "tag:$it" }, contentType = { "tag" }) { tag ->
            SearchRow(
                headline = "#$tag",
                leading = { Icon(Icons.Default.Tag, contentDescription = null) },
                onClick = { pick { actions.openTag(tag) } },
            )
        }
        items(suggestions?.people.orEmpty(), key = { "person:${it.id}" }, contentType = { "person" }) { person ->
            PersonSearchRow(person, signedIn, actions.people) { pick { actions.openPerson(person) } }
        }
    }
}

@Composable
internal fun PersonSearchRow(person: NoteUser, signedIn: Boolean, actions: PeopleActions, onOpen: () -> Unit) {
    var menuOffset by remember { mutableStateOf<DpOffset?>(null) }
    Box {
        SearchRow(
            headline = person.displayName,
            headlineContent = { PersonName(person) },
            supporting = person.fullHandle,
            leading = { Avatar(name = person.displayName, url = person.avatarUrl, size = 40.dp) },
            onClick = onOpen,
            onLongClick = { menuOffset = DpOffset.Zero },
            modifier = Modifier.contextMenuTrigger(onOpen = { menuOffset = it }).testTag("search:person"),
        )
        menuOffset?.let { offset ->
            DropdownMenu(expanded = true, onDismissRequest = { menuOffset = null }, offset = offset) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.person_menu_view)) },
                    onClick = {
                        menuOffset = null
                        onOpen()
                    },
                )
                PersonMenuItems(person, detail = null, myUserId = null, signedIn = signedIn, actions = actions) { menuOffset = null }
            }
        }
    }
}

@Composable
private fun SearchHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SearchRow(
    headline: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    headlineContent: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    var hovered by remember { mutableStateOf(false) }
    ListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = leading,
        supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        colors = ListItemDefaults.colors(containerColor = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
        modifier = modifier.fillMaxWidth().pointerHover { hovered = it },
    ) {
        if (headlineContent != null) headlineContent() else Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PersonName(person: NoteUser) {
    PersonName(person, LocalNoteEnvironment.current)
}

@Composable
internal fun PersonName(person: NoteUser, environment: NoteEnvironment?) {
    if (environment == null) {
        Text(person.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val nameEnvironment = remember(person, environment.mfm) { environment.mfm.withEmoji(environment.userEmoji(person)) }
    MfmContent(
        nodes = person.nameMfm.ifEmpty { listOf(MfmText(person.username)) },
        environment = nameEnvironment,
        style = LocalTextStyle.current,
        maxLines = 1,
    )
}

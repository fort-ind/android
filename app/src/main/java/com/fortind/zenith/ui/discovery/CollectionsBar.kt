@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.R
import com.fortind.zenith.discovery.LIST_NAME_MAX_LENGTH
import com.fortind.zenith.discovery.UserListInfo
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.feed.FeedLoading
import com.fortind.zenith.ui.feed.FeedMaxWidth
import com.fortind.zenith.ui.feed.TooltipIconButton
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch

private val SheetMaxWidth = 640.dp

@Composable
fun CollectionsBar(collections: CollectionsViewModel, modifier: Modifier = Modifier) {
    val state by collections.uiState.collectAsStateWithLifecycle()
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val selectedList = state.selectedList

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth().testTag("collections:bar")) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .widthIn(max = FeedMaxWidth)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth().selectableGroup(),
            ) {
                CollectionTab.entries.forEachIndexed { index, tab ->
                    val unread = tab == CollectionTab.Antennas && state.antennas.any { it.hasUnread }
                    ToggleButton(
                        checked = state.tab == tab,
                        onCheckedChange = { collections.selectTab(tab) },
                        shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("collections:tab:${tab.name}")
                            .semantics { role = Role.RadioButton },
                    ) {
                        Text(stringResource(if (tab == CollectionTab.Lists) R.string.collections_tab_lists else R.string.collections_tab_antennas))
                        if (unread) {
                            Spacer(Modifier.size(6.dp))
                            UnreadDot()
                        }
                    }
                }
            }
            if (state.status == CollectionsStatus.Ready && !state.tabIsEmpty) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CollectionPicker(state, collections, Modifier.weight(1f))
                    if (state.tab == CollectionTab.Lists) {
                        TooltipIconButton(label = stringResource(R.string.collections_new), icon = Icons.Default.Add, onClick = { naming = "" })
                        if (selectedList != null) {
                            ListMenu(
                                onRename = { naming = selectedList.name },
                                onMembers = { collections.openMembers(selectedList.id) },
                                onDelete = { confirmingDelete = true },
                            )
                        }
                    }
                }
            }
        }
    }

    naming?.let { initial ->
        ListNameDialog(
            initial = initial,
            renaming = initial.isNotEmpty(),
            onConfirm = { name ->
                val list = selectedList
                if (initial.isNotEmpty() && list != null) collections.renameList(list.id, name) else collections.createList(name)
                naming = null
            },
            onDismiss = { naming = null },
        )
    }
    if (confirmingDelete && selectedList != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.collections_delete_title, selectedList.name)) },
            text = { Text(stringResource(R.string.collections_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        collections.deleteList(selectedList.id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.collections_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.note_dialog_cancel)) } },
        )
    }
    state.members?.let { members ->
        ListMembersSheet(
            members = members,
            listName = state.lists.firstOrNull { it.id == members.listId }?.name.orEmpty(),
            collections = collections,
        )
    }
}

@Composable
private fun UnreadDot() {
    Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
}

@Composable
private fun CollectionPicker(state: CollectionsUiState, collections: CollectionsViewModel, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = when (state.tab) {
        CollectionTab.Lists -> state.selectedList?.name
        CollectionTab.Antennas -> state.selectedAntenna?.name
    }.orEmpty()
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("collections:picker"),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.collections_choose))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val check = @Composable { selected: Boolean ->
                if (selected) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.settings_choice_selected))
            }
            when (state.tab) {
                CollectionTab.Lists -> state.lists.forEach { list ->
                    DropdownMenuItem(
                        text = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { check(list.id == state.listId) },
                        onClick = {
                            open = false
                            collections.selectList(list.id)
                        },
                    )
                }
                CollectionTab.Antennas -> state.antennas.forEach { antenna ->
                    DropdownMenuItem(
                        text = { Text(antenna.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (antenna.hasUnread) {
                            { UnreadDot() }
                        } else {
                            null
                        },
                        trailingIcon = { check(antenna.id == state.antennaId) },
                        onClick = {
                            open = false
                            collections.selectAntenna(antenna.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListMenu(onRename: () -> Unit, onMembers: () -> Unit, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        TooltipIconButton(label = stringResource(R.string.collections_options), icon = Icons.Default.MoreVert, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.collections_members)) }, onClick = { open = false; onMembers() })
            DropdownMenuItem(text = { Text(stringResource(R.string.collections_rename)) }, onClick = { open = false; onRename() })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.collections_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun ListNameDialog(initial: String, renaming: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val name = rememberTextFieldState(initial)
    val clean = name.text.toString().trim()
    val valid = clean.isNotEmpty() && clean.length <= LIST_NAME_MAX_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (renaming) R.string.collections_rename_title else R.string.collections_new_title)) },
        text = {
            OutlinedTextField(
                state = name,
                label = { Text(stringResource(R.string.collections_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = clean.length > LIST_NAME_MAX_LENGTH,
                supportingText = { Text(stringResource(R.string.account_counter, clean.length, LIST_NAME_MAX_LENGTH)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(clean) }, enabled = valid) {
                Text(stringResource(if (renaming) R.string.collections_rename_confirm else R.string.collections_new_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) } },
    )
}

@Composable
fun CollectionsMessage(state: CollectionsUiState, collections: CollectionsViewModel, modifier: Modifier = Modifier) {
    var creating by rememberSaveable { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(32.dp),
    ) {
        val failed = state.status == CollectionsStatus.Failed
        Text(
            text = stringResource(
                when {
                    failed -> R.string.collections_failed
                    state.tab == CollectionTab.Lists -> R.string.collections_no_lists
                    else -> R.string.collections_no_antennas
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        when {
            failed -> TextButton(onClick = collections::load) { Text(stringResource(R.string.feed_retry)) }
            state.tab == CollectionTab.Lists -> TextButton(onClick = { creating = true }) { Text(stringResource(R.string.collections_create_first)) }
        }
    }
    if (creating) {
        ListNameDialog(
            initial = "",
            renaming = false,
            onConfirm = { name ->
                creating = false
                collections.createList(name)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun ListMembersSheet(members: MembersState, listName: String, collections: CollectionsViewModel) {
    val query = rememberTextFieldState()
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect(collections::searchPeople)
    }
    ModalBottomSheet(onDismissRequest = collections::closeMembers, sheetMaxWidth = SheetMaxWidth) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).testTag("collections:members"),
        ) {
            item(key = "title", contentType = "title") {
                Text(
                    text = stringResource(R.string.collections_members_title, listName),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item(key = "search", contentType = "search") {
                OutlinedTextField(
                    state = query,
                    label = { Text(stringResource(R.string.collections_members_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            items(members.results, key = { "result:${it.id}" }, contentType = { "person" }) { person ->
                PersonRow(person) {
                    TextButton(onClick = { collections.addMember(person) }, enabled = person.id !in members.busy) {
                        Text(stringResource(R.string.collections_members_add))
                    }
                }
            }
            when (members.status) {
                CollectionsStatus.Idle, CollectionsStatus.Loading -> item(key = "loading", contentType = "message") { FeedLoading(Modifier.padding(24.dp)) }
                CollectionsStatus.Failed -> item(key = "failed", contentType = "message") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { collections.openMembers(members.listId) }) { Text(stringResource(R.string.feed_retry)) }
                    }
                }
                CollectionsStatus.Ready -> {
                    if (members.members.isEmpty()) {
                        item(key = "empty", contentType = "message") {
                            Text(
                                text = stringResource(R.string.collections_members_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                    items(members.members, key = { "member:${it.user.id}" }, contentType = { "person" }) { member ->
                        PersonRow(member.user) {
                            TooltipIconButton(
                                label = stringResource(R.string.collections_members_remove, member.user.displayName),
                                icon = Icons.Default.Close,
                                onClick = { collections.removeMember(member.user) },
                                enabled = member.user.id !in members.busy,
                            )
                        }
                    }
                    if (!members.endReached && members.members.isNotEmpty()) {
                        item(key = "more", contentType = "message") {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (members.loadingMore) {
                                    FeedLoading(Modifier.padding(16.dp))
                                } else {
                                    TextButton(onClick = collections::loadMoreMembers) { Text(stringResource(R.string.moderation_load_more)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(person: NoteUser, trailing: @Composable () -> Unit) {
    ListItem(
        leadingContent = { Avatar(name = person.displayName, url = person.avatarUrl, size = 40.dp) },
        supportingContent = { Text(person.fullHandle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PersonName(person)
    }
}

@Composable
fun AddToListSheet(user: NoteUser, collections: CollectionsViewModel, onDismiss: () -> Unit) {
    val state by collections.uiState.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(collections) { collections.load() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetMaxWidth = SheetMaxWidth) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).testTag("collections:add-to-list")) {
            Text(
                text = stringResource(R.string.collections_add_title, user.displayName),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when {
                state.status == CollectionsStatus.Failed -> TextButton(onClick = collections::load, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.feed_retry))
                }
                state.status != CollectionsStatus.Ready -> FeedLoading(Modifier.fillMaxWidth().padding(24.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(state.lists, key = { it.id }, contentType = { "list" }) { list ->
                        MembershipRow(list, user, list.id !in state.membershipBusy, collections)
                    }
                }
            }
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.collections_new))
            }
        }
    }
    if (creating) {
        ListNameDialog(
            initial = "",
            renaming = false,
            onConfirm = { name ->
                creating = false
                collections.createList(name)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun MembershipRow(list: UserListInfo, user: NoteUser, enabled: Boolean, collections: CollectionsViewModel) {
    val checked = user.id in list.userIds
    ListItem(
        checked = checked,
        onCheckedChange = { collections.setMembership(list.id, user.id, it) },
        enabled = enabled,
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CollectionsEventsEffect(collections: CollectionsViewModel, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(collections) {
        collections.events.collect { event ->
            val message = when (event) {
                is CollectionsEvent.Created -> resources.getString(R.string.collections_created, event.name)
                is CollectionsEvent.Deleted -> resources.getString(R.string.collections_deleted, event.name)
                is CollectionsEvent.Failed -> resources.getString(
                    when (event.failure) {
                        CollectionFailure.Offline -> R.string.collections_failed_offline
                        CollectionFailure.RateLimited -> R.string.collections_failed_rate
                        CollectionFailure.TooManyLists -> R.string.collections_failed_too_many_lists
                        CollectionFailure.TooManyUsers -> R.string.collections_failed_too_many_users
                        CollectionFailure.Blocked -> R.string.collections_failed_blocked
                    },
                )
            }
            scope.launch { snackbar.showSnackbar(message) }
        }
    }
}

@Composable
fun CollectionsEmptyPane(state: CollectionsUiState, collections: CollectionsViewModel) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CollectionsBar(collections)
        if (state.status == CollectionsStatus.Loading || state.status == CollectionsStatus.Idle) {
            FeedLoading(Modifier.fillMaxWidth().padding(top = 48.dp))
        } else {
            CollectionsMessage(state, collections)
        }
    }
}

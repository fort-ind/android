@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fortind.zenith.ui.composer

import android.content.ClipData
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.composer.MAX_POLL_CHOICES
import com.fortind.zenith.composer.MIN_POLL_CHOICES
import com.fortind.zenith.composer.PollProblem
import com.fortind.zenith.composer.customPollEnd
import com.fortind.zenith.drive.DriveFolder
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.ReactionAcceptance
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.contextMenuTrigger
import com.fortind.zenith.ui.discovery.PersonName
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.media.NetworkImage
import com.fortind.zenith.ui.rememberReduceMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal enum class PollEndChoice(val afterMillis: Long?, @param:StringRes val labelRes: Int) {
    Never(null, R.string.poll_end_never),
    FiveMinutes(5 * 60 * 1_000L, R.string.poll_end_five_minutes),
    OneHour(60 * 60 * 1_000L, R.string.poll_end_one_hour),
    OneDay(24 * 60 * 60 * 1_000L, R.string.poll_end_one_day),
    ThreeDays(3 * 24 * 60 * 60 * 1_000L, R.string.poll_end_three_days),
    OneWeek(7 * 24 * 60 * 60 * 1_000L, R.string.poll_end_one_week),
}

@Composable
internal fun PollEditor(
    viewModel: ComposerViewModel,
    state: ComposerUiState,
    problem: PollProblem?,
    resetsVotes: Boolean,
    onOpenEnd: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("composer:poll"),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.poll_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TooltipIconButton(
                    label = stringResource(R.string.poll_remove),
                    icon = Icons.Default.Close,
                    onClick = { viewModel.setPollEnabled(false) },
                )
            }
            viewModel.pollChoices.forEachIndexed { index, field ->
                OutlinedTextField(
                    state = field.text,
                    label = { Text(stringResource(R.string.poll_choice, index + 1)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    trailingIcon = if (viewModel.pollChoices.size > MIN_POLL_CHOICES) {
                        {
                            TooltipIconButton(
                                label = stringResource(R.string.poll_choice_remove, index + 1),
                                icon = Icons.Default.Close,
                                onClick = { viewModel.removePollChoice(field.id) },
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (viewModel.pollChoices.size < MAX_POLL_CHOICES) {
                TextButton(onClick = viewModel::addPollChoice) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.poll_add_choice), modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .toggleable(value = state.pollMultiple, role = Role.Checkbox, onValueChange = viewModel::setPollMultiple),
            ) {
                Checkbox(checked = state.pollMultiple, onCheckedChange = null)
                Text(stringResource(R.string.poll_multiple))
            }
            TextButton(onClick = onOpenEnd, modifier = Modifier.testTag("composer:poll:end")) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                val end = state.pollEndsAt?.let {
                    stringResource(
                        R.string.poll_ends_at,
                        DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL),
                    )
                } ?: stringResource(PollEndChoice.entries.first { it.afterMillis == state.pollEndsAfter }.labelRes)
                Text(end, modifier = Modifier.padding(start = 8.dp))
            }
            val message = when (problem) {
                PollProblem.DuplicateChoices -> R.string.poll_problem_duplicate
                PollProblem.ChoiceTooLong -> R.string.poll_problem_too_long
                PollProblem.EndInPast -> R.string.poll_problem_past
                PollProblem.TooFewChoices, null -> null
            }
            if (message != null) {
                Text(stringResource(message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else if (resetsVotes) {
                Text(stringResource(R.string.poll_resets_votes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
internal fun PollEndSheet(
    visible: Boolean,
    onChoose: (Long?) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.poll_ends)
    BottomSheetOverlay(visible = visible, title = title, onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val count = PollEndChoice.entries.size + 1
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            PollEndChoice.entries.forEachIndexed { index, choice ->
                SegmentedListItem(
                    onClick = { onChoose(choice.afterMillis) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Text(stringResource(choice.labelRes))
                }
            }
            SegmentedListItem(
                onClick = onCustom,
                shapes = ListItemDefaults.segmentedShapes(index = count - 1, count = count),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Text(stringResource(R.string.poll_end_custom))
            }
        }
    }
}

private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1_000L

private class FutureDates(nowMillis: Long) : SelectableDates {
    private val today = nowMillis - nowMillis % ONE_DAY_MILLIS - ONE_DAY_MILLIS

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= today
}

@Composable
internal fun PollDateSheet(visible: Boolean, nowMillis: Long, onChoose: (Long) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.poll_end_custom)
    BottomSheetOverlay(visible = visible, title = title, onDismiss = onDismiss) {
        val selectable = remember(nowMillis) { FutureDates(nowMillis) }
        val date = rememberDatePickerState(initialSelectedDateMillis = nowMillis + ONE_DAY_MILLIS, selectableDates = selectable)
        val time = rememberTimePickerState(initialHour = 12, initialMinute = 0)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            DatePicker(state = date, title = null, headline = null, showModeToggle = false)
            TimeInput(state = time)
            val chosen = date.selectedDateMillis?.let { customPollEnd(it, time.hour, time.minute) }
            val future = chosen != null && chosen > nowMillis
            if (chosen != null && !future) {
                Text(stringResource(R.string.poll_problem_past), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_dialog_cancel)) }
                Button(onClick = { chosen?.let(onChoose) }, enabled = future) { Text(stringResource(R.string.poll_end_set)) }
            }
        }
    }
}

@Composable
internal fun RecipientEditor(viewModel: ComposerViewModel, state: ComposerUiState, results: List<NoteUser>) {
    Column(modifier = Modifier.fillMaxWidth().testTag("composer:recipients"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.recipients.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.recipients.forEach { person ->
                    val remove = stringResource(R.string.recipient_remove, person.displayName)
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeRecipient(person.id) },
                        label = { PersonName(person) },
                        avatar = { Avatar(name = person.displayName, url = person.avatarUrl, size = 24.dp) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.semantics { contentDescription = remove },
                    )
                }
            }
        }
        OutlinedTextField(
            state = viewModel.recipientQuery,
            label = { Text(stringResource(R.string.recipient_add)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            lineLimits = TextFieldLineLimits.SingleLine,
            supportingText = if (state.recipients.isEmpty()) {
                { Text(stringResource(R.string.recipient_none)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        results.forEach { person ->
            ListItem(
                onClick = { viewModel.addRecipient(person) },
                leadingContent = { Avatar(name = person.displayName, url = person.avatarUrl, size = 40.dp) },
                supportingContent = { Text(person.fullHandle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PersonName(person)
            }
        }
    }
}

@StringRes
internal fun ReactionAcceptance.labelRes(): Int = when (this) {
    ReactionAcceptance.All -> R.string.reaction_limit_all
    ReactionAcceptance.NonSensitiveOnly -> R.string.reaction_limit_no_sensitive
    ReactionAcceptance.LikeOnly -> R.string.reaction_limit_like
    ReactionAcceptance.LikeOnlyForRemote -> R.string.reaction_limit_like_remote
    ReactionAcceptance.NonSensitiveOnlyForLocalLikeOnlyForRemote -> R.string.reaction_limit_mixed
}

private val PickerMaxWidth = 640.dp

@Composable
internal fun DrivePickerOverlay(
    picker: DrivePickerState,
    attachedIds: Set<String>,
    slots: Int,
    viewModel: ComposerViewModel,
    onClose: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val pickerArea = remember { LayoutHolder() }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    val menuOpen = picker.menuFileId != null
    val behindMenu = if (menuOpen) Modifier.clearAndSetSemantics {}.focusProperties { canFocus = false } else Modifier
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .safeDrawingPadding()
            .onPlaced { pickerArea.coordinates = it },
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = PickerMaxWidth).fillMaxSize().padding(8.dp).testTag("composer:drive"),
        ) {
            Column(modifier = behindMenu) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    TooltipIconButton(
                        label = stringResource(if (picker.path.isEmpty()) R.string.composer_close else R.string.drive_up),
                        icon = if (picker.path.isEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = { if (picker.path.isEmpty()) onClose() else viewModel.driveUp() },
                    )
                    Text(
                        text = picker.path.lastOrNull()?.name ?: stringResource(R.string.drive_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Button(onClick = viewModel::attachDriveSelection, enabled = picker.selected.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.drive_attach, picker.selected.size, picker.selected.size))
                    }
                }
                when (picker.status) {
                    DriveStatus.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (reduceMotion) Text(stringResource(R.string.search_loading)) else LoadingIndicator()
                    }
                    DriveStatus.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = viewModel::reloadDrive) { Text(stringResource(R.string.drive_retry)) }
                    }
                    DriveStatus.Ready -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    ) {
                        items(picker.folders, key = { "folder:${it.id}" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "folder" }) { folder ->
                            DriveFolderRow(folder) { viewModel.openDriveFolder(folder) }
                        }
                        if (picker.folders.isEmpty() && picker.files.isEmpty()) {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                                Text(
                                    stringResource(R.string.drive_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                        items(picker.files, key = { "file:${it.id}" }, contentType = { "file" }) { file ->
                            val attached = file.id in attachedIds
                            val selected = picker.selected.any { it.id == file.id }
                            DriveTile(
                                file = file,
                                selected = selected || attached,
                                enabled = !attached && (selected || picker.selected.size < slots),
                                animate = !reduceMotion,
                                onToggle = { viewModel.toggleDriveFile(file, slots) },
                                onOpenMenu = { tile, point ->
                                    menuAnchor = pickerArea.coordinates?.localPositionOf(tile, point) ?: Offset.Zero
                                    viewModel.openDriveMenu(file.id)
                                },
                            )
                        }
                        if (!picker.endReached && picker.files.isNotEmpty()) {
                            item(key = "more", span = { GridItemSpan(maxLineSpan) }, contentType = "more") {
                                TextButton(onClick = viewModel::loadMoreDrive, enabled = !picker.loadingMore) {
                                    Text(stringResource(R.string.moderation_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
        val menuFile = picker.menuFileId?.let { id -> picker.files.firstOrNull { it.id == id } }
        if (menuFile != null) {
            val attached = menuFile.id in attachedIds
            val selected = picker.selected.any { it.id == menuFile.id }
            val clipboard = LocalClipboard.current
            val scope = rememberCoroutineScope()
            DriveTileMenu(
                file = menuFile,
                anchor = menuAnchor,
                attached = attached,
                selected = selected,
                canSelect = selected || picker.selected.size < slots,
                onToggle = {
                    viewModel.toggleDriveFile(menuFile, slots)
                    viewModel.closeDriveMenu()
                },
                onCopyLink = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(menuFile.name, menuFile.url))) }
                    viewModel.closeDriveMenu()
                },
                onDismiss = viewModel::closeDriveMenu,
            )
        }
    }
}

private class LayoutHolder {
    var coordinates: LayoutCoordinates? = null
}

private val DriveMenuMinWidth = 200.dp
private val DriveMenuMaxWidth = 280.dp

@Composable
internal fun DriveTileMenu(
    file: NoteFile,
    anchor: Offset,
    attached: Boolean,
    selected: Boolean,
    canSelect: Boolean,
    onToggle: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = file.name ?: file.id
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Layout(
        content = {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .widthIn(min = DriveMenuMinWidth, max = DriveMenuMaxWidth)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = name }
                    .testTag("composer:drive:menu"),
            ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup(),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    when {
                                        attached -> R.string.drive_menu_attached
                                        selected -> R.string.drive_menu_deselect
                                        else -> R.string.drive_menu_select
                                    },
                                ),
                            )
                        },
                        onClick = onToggle,
                        enabled = !attached && canSelect,
                        modifier = if (!attached && canSelect) Modifier.focusRequester(focus) else Modifier,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.note_menu_copy_link)) },
                        onClick = onCopyLink,
                        modifier = if (!attached && canSelect) Modifier else Modifier.focusRequester(focus),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) { measurables, constraints ->
        val menu = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            menu.place(
                x = anchor.x.roundToInt().coerceAtMost(constraints.maxWidth - menu.width).coerceAtLeast(0),
                y = anchor.y.roundToInt().coerceAtMost(constraints.maxHeight - menu.height).coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun DriveFolderRow(folder: DriveFolder, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun DriveTile(
    file: NoteFile,
    selected: Boolean,
    enabled: Boolean,
    animate: Boolean,
    onToggle: () -> Unit,
    onOpenMenu: (LayoutCoordinates, Offset) -> Unit,
) {
    val name = file.name ?: file.id
    val tile = remember { LayoutHolder() }
    val density = LocalDensity.current
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .onPlaced { tile.coordinates = it }
            .contextMenuTrigger { offset ->
                tile.coordinates?.let { coordinates ->
                    val point = with(density) { Offset(offset.x.toPx(), offset.y.toPx() + coordinates.size.height) }
                    onOpenMenu(coordinates, point)
                }
            }
            .semantics {
                contentDescription = name
                this.selected = selected
            },
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)) {
            val thumbnail = file.thumbnailUrl
            when {
                thumbnail != null -> NetworkImage(
                    url = thumbnail,
                    contentDescription = null,
                    animate = animate,
                    blurhash = file.blurhash,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                ) {
                    Icon(if (file.kind == MediaKind.Audio) Icons.Default.AudioFile else Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
                    Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
            if (!enabled && !selected) Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)))
        }
    }
}

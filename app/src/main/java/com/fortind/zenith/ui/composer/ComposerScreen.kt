@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.composer

import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.fortind.zenith.composer.pollEditResetsVotes
import com.fortind.zenith.composer.problem
import com.fortind.zenith.timeline.ReactionAcceptance
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import com.fortind.zenith.R
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.composer.MAX_ATTACHMENTS
import com.fortind.zenith.mfm.MfmText
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.feed.NoteHeader
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.theme.isThemeDark
import kotlinx.coroutines.launch

private val ComposerMaxWidth = 640.dp
private val ComposerMaxHeight = 640.dp

@Composable
fun ComposerHost(
    viewModel: ComposerViewModel,
    environment: NoteEnvironment?,
    onGrantPermission: () -> Unit,
    windowed: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state ?: return
    ComposerDialog(current, viewModel, environment, onGrantPermission, windowed)
}

@Composable
private fun ComposerFrame(windowed: Boolean, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    if (windowed) {
        NavigationBackHandler(
            state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
            onBackCompleted = onDismissRequest,
        )
        content()
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            content()
        }
    }
}

@Composable
private fun ComposerDialog(
    state: ComposerUiState,
    viewModel: ComposerViewModel,
    environment: NoteEnvironment?,
    onGrantPermission: () -> Unit,
    windowed: Boolean,
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val floating = !windowed && windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    var overlay by rememberSaveable { mutableStateOf("") }
    val overlayOpen = confirmClose || overlay.isNotEmpty() || state.prompt != null || state.drive != null
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val compressUploads by viewModel.compressUploads.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val requestClose: () -> Unit = {
        when {
            confirmClose -> confirmClose = false
            state.prompt != null -> viewModel.dismissPrompt()
            state.drive?.menuFileId != null -> viewModel.closeDriveMenu()
            state.drive != null -> viewModel.closeDrive()
            overlay.isNotEmpty() -> overlay = ""
            viewModel.hasUnsavedChanges() -> confirmClose = true
            else -> viewModel.close(keepDraft = false)
        }
    }
    val pickMedia = rememberMediaPicker(remaining = MAX_ATTACHMENTS - state.attachments.size) { uris ->
        viewModel.addPicked(uris.map(Uri::toString))
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            overlay = OVERLAY_CAMERA
        } else {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.composer_camera_permission)) }
        }
    }
    val openCamera = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            overlay = OVERLAY_CAMERA
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.failure) {
        val failure = state.failure ?: return@LaunchedEffect
        val action = if (failure == ComposerFailure.MissingPermission) resources.getString(R.string.composer_grant) else null
        val result = snackbar.showSnackbar(
            message = resources.getString(failure.messageRes()),
            actionLabel = action,
            duration = SnackbarDuration.Long,
        )
        viewModel.failureShown()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.close(keepDraft = true)
            onGrantPermission()
        }
    }

    ComposerFrame(windowed = windowed, onDismissRequest = requestClose) {
        val overlayFocus = remember { OverlayFocus() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(overlayOpen) { if (overlayOpen) focusManager.clearFocus() }
        val dialogView = LocalView.current
        val darkTheme = isThemeDark()
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, dialogView).apply {
                    isAppearanceLightStatusBars = !darkTheme && !floating
                    isAppearanceLightNavigationBars = !darkTheme && !floating
                }
            }
        }
        val draftable = state.target.draftKey != null
        val keyboard = LocalSoftwareKeyboardController.current
        val openSheet = { sheet: String ->
            keyboard?.hide()
            overlay = sheet
        }
        val shortcuts = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                overlayOpen && overlayFocus.enterFrom(event) -> true
                event.key == Key.Escape -> {
                    if (!overlayOpen && suggestions != null) viewModel.dismissSuggestions() else requestClose()
                    true
                }
                event.key == Key.Enter && (event.isCtrlPressed || event.isMetaPressed) && !overlayOpen -> {
                    viewModel.submit()
                    true
                }
                else -> false
            }
        }
        val behindPrompt = if (overlayOpen) Modifier.clearAndSetSemantics {}.focusProperties { canFocus = false } else Modifier
        Box(modifier = Modifier.fillMaxSize().then(shortcuts)) {
            if (floating) {
                Box(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .widthIn(max = ComposerMaxWidth)
                            .fillMaxWidth()
                            .heightIn(max = ComposerMaxHeight)
                            .then(behindPrompt),
                    ) {
                        ComposerContent(
                            state = state,
                            viewModel = viewModel,
                            environment = environment,
                            floating = true,
                            promptOpen = overlayOpen,
                            snackbar = snackbar,
                            onRequestClose = requestClose,
                            onOpenVisibility = { openSheet(OVERLAY_VISIBILITY) },
                            onOpenAttach = { openSheet(OVERLAY_ATTACH) },
                            onOpenAttachment = { overlay = OVERLAY_PREVIEW + it },
                            onEditAttachment = { overlay = OVERLAY_DETAILS + it },
                            onOpenPollEnd = { openSheet(OVERLAY_POLL_END) },
                        )
                    }
                }
            } else {
                Surface(modifier = Modifier.fillMaxSize().then(behindPrompt), color = MaterialTheme.colorScheme.surface) {
                    ComposerContent(
                        state = state,
                        viewModel = viewModel,
                        environment = environment,
                        floating = false,
                        promptOpen = overlayOpen,
                        snackbar = snackbar,
                        onRequestClose = requestClose,
                        onOpenVisibility = { openSheet(OVERLAY_VISIBILITY) },
                        onOpenAttach = { openSheet(OVERLAY_ATTACH) },
                        onOpenAttachment = { overlay = OVERLAY_PREVIEW + it },
                        onEditAttachment = { overlay = OVERLAY_DETAILS + it },
                        onOpenPollEnd = { openSheet(OVERLAY_POLL_END) },
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().overlayFocusHost(overlayFocus, overlayOpen)) {
                DiscardPrompt(
                    visible = confirmClose,
                    draftable = draftable,
                    onConfirm = {
                        confirmClose = false
                        if (draftable) viewModel.close(keepDraft = true)
                    },
                    onDiscard = {
                        confirmClose = false
                        viewModel.close(keepDraft = false)
                    },
                    onDismiss = { confirmClose = false },
                )
                VisibilitySheet(
                    visible = overlay == OVERLAY_VISIBILITY,
                    selected = state.visibility,
                    options = state.visibilityOptions,
                    onSelect = viewModel::setVisibility,
                    acceptance = state.reactionAcceptance,
                    onAcceptance = viewModel::setReactionAcceptance,
                    onDismiss = { overlay = "" },
                )
                PollEndSheet(
                    visible = overlay == OVERLAY_POLL_END,
                    onChoose = {
                        overlay = ""
                        viewModel.setPollEndsAfter(it)
                    },
                    onCustom = { overlay = OVERLAY_POLL_DATE },
                    onDismiss = { overlay = "" },
                )
                PollDateSheet(
                    visible = overlay == OVERLAY_POLL_DATE,
                    nowMillis = viewModel.now(),
                    onChoose = {
                        overlay = ""
                        viewModel.setPollEndsAt(it)
                    },
                    onDismiss = { overlay = "" },
                )
                state.drive?.let { picker ->
                    DrivePickerOverlay(
                        picker = picker,
                        attachedIds = state.attachments.mapNotNullTo(HashSet()) { it.remote?.id },
                        slots = MAX_ATTACHMENTS - state.attachments.size,
                        viewModel = viewModel,
                        onClose = viewModel::closeDrive,
                    )
                }
                ComposerPromptOverlay(
                    prompt = state.prompt,
                    onConfirm = viewModel::confirmPrompt,
                    onDecline = {
                        val missing = state.attachments.firstOrNull { it.needsAltText }
                        val wasAltText = state.prompt == ComposerPrompt.MissingAltText
                        viewModel.dismissPrompt()
                        if (wasAltText && missing != null) overlay = OVERLAY_DETAILS + missing.localId
                    },
                )
                AttachSheet(
                    visible = overlay == OVERLAY_ATTACH,
                    onLibrary = {
                        overlay = ""
                        pickMedia()
                    },
                    onCamera = {
                        overlay = ""
                        openCamera()
                    },
                    onDrive = {
                        overlay = ""
                        viewModel.openDrive()
                    },
                    onDismiss = { overlay = "" },
                )
                val detailsId = overlay.removePrefixOrNull(OVERLAY_DETAILS)
                AttachmentDetailsPrompt(
                    attachment = detailsId?.let { id -> state.attachments.firstOrNull { it.localId == id } },
                    maxAltLength = state.maxAltLength,
                    onSave = { altText, isSensitive ->
                        detailsId?.let { viewModel.setAttachmentDetails(it, altText, isSensitive) }
                        overlay = ""
                    },
                    onDismiss = { overlay = "" },
                )
                val previewId = overlay.removePrefixOrNull(OVERLAY_PREVIEW)
                MediaPreviewOverlay(
                    attachment = previewId?.let { id -> state.attachments.firstOrNull { it.localId == id } },
                    onClose = { overlay = "" },
                )
                if (overlay == OVERLAY_CAMERA) {
                    CameraCaptureOverlay(
                        videoSizeLimitBytes = if (compressUploads) COMPRESSED_VIDEO_LIMIT_BYTES else state.maxFileSizeBytes,
                        onCaptured = { media ->
                            viewModel.addMedia(listOf(media))
                            overlay = ""
                        },
                        onClose = { overlay = "" },
                    )
                }
            }
        }
    }
}

private const val OVERLAY_VISIBILITY = "visibility"
private const val OVERLAY_ATTACH = "attach"
private const val OVERLAY_POLL_END = "pollEnd"
private const val OVERLAY_POLL_DATE = "pollDate"
private const val OVERLAY_CAMERA = "camera"
private const val OVERLAY_PREVIEW = "preview:"
private const val OVERLAY_DETAILS = "details:"
private const val COMPRESSED_VIDEO_LIMIT_BYTES = 512L * 1024 * 1024

private fun String.removePrefixOrNull(prefix: String): String? = if (startsWith(prefix)) removePrefix(prefix) else null

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComposerContent(
    state: ComposerUiState,
    viewModel: ComposerViewModel,
    environment: NoteEnvironment?,
    floating: Boolean,
    promptOpen: Boolean,
    snackbar: SnackbarHostState,
    onRequestClose: () -> Unit,
    onOpenVisibility: () -> Unit,
    onOpenAttach: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onEditAttachment: (String) -> Unit,
    onOpenPollEnd: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val recipientResults by viewModel.recipientResults.collectAsStateWithLifecycle()
    val pollProblem by remember(viewModel, state) { derivedStateOf { viewModel.pollDraft(state)?.problem(viewModel.now()) } }
    val pollResets by remember(viewModel, state) {
        derivedStateOf {
            state.target is ComposerTarget.Edit && !state.dropsEndedPoll && pollEditResetsVotes(state.contextNote?.poll, viewModel.pollDraft(state))
        }
    }
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var dragOver by remember { mutableStateOf(false) }
    val dropHint = stringResource(R.string.composer_drop_hint)
    val receiver = rememberMediaReceiver(
        reader = (LocalContext.current.applicationContext as ZenithApplication).container.mediaReader,
        onMedia = viewModel::addPicked,
        onDragOver = { dragOver = it },
    )
    val focusRequester = remember { FocusRequester() }
    val hasText by remember(viewModel) { derivedStateOf { viewModel.text.text.isNotBlank() } }
    val overLimit by remember(viewModel, state.textLimit, state.cwLimit, state.cwEnabled) {
        derivedStateOf {
            viewModel.text.text.length > state.textLimit ||
                (state.cwEnabled && viewModel.cw.text.length > state.cwLimit)
        }
    }
    LaunchedEffect(promptOpen) { if (!promptOpen) focusRequester.requestFocus() }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = if (floating) WindowInsets(0) else WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.titleRes()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TooltipIconButton(
                        label = stringResource(R.string.composer_close),
                        icon = Icons.Default.Close,
                        onClick = onRequestClose,
                    )
                },
                actions = {
                    PostButton(
                        label = stringResource(if (state.target is ComposerTarget.Edit) R.string.composer_save_edit else R.string.composer_post),
                        posting = state.posting,
                        enabled = (hasText || state.attachments.isNotEmpty() || state.pollEnabled || state.isQuote) &&
                            pollProblem == null && state.attachmentsReady && !overLimit && !state.posting,
                        onClick = viewModel::submit,
                    )
                    Spacer(Modifier.width(8.dp))
                },
                windowInsets = if (floating) WindowInsets(0) else TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { inner ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .contentReceiver(receiver)
                .dropHighlight(dragOver, dropHint),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = ComposerMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                val context = state.contextNote
                if (context != null && environment != null && state.target !is ComposerTarget.Edit) ContextCard(context, environment)
                if (state.showsRecipients) RecipientEditor(viewModel, state, recipientResults, environment)
                if (state.dropsEndedPoll && !state.pollEnabled) EndedPollWarning()
                AnimatedVisibility(
                    visible = state.cwEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    ContentWarningField(viewModel, onRemove = { viewModel.setCwEnabled(false) })
                }
                BodyField(viewModel, state, focusRequester)
                if (state.pollEnabled) {
                    PollEditor(viewModel, state, pollProblem, pollResets, onOpenPollEnd)
                }
                AnimatedVisibility(
                    visible = state.attachments.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    AttachmentStrip(
                        attachments = state.attachments,
                        animate = !reduceMotion,
                        onOpen = onOpenAttachment,
                        onEditDetails = onEditAttachment,
                        onRemove = viewModel::removeAttachment,
                        onRetry = viewModel::retryAttachment,
                    )
                }
            }
            AnimatedVisibility(visible = suggestions != null) {
                suggestions?.let { open ->
                    SuggestionStrip(
                        state = open,
                        animate = !reduceMotion,
                        onPick = viewModel::acceptSuggestion,
                        modifier = Modifier.widthIn(max = ComposerMaxWidth).fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
            ComposerToolbar(
                state = state,
                length = { viewModel.text.text.length },
                onCwChange = viewModel::setCwEnabled,
                onOpenVisibility = onOpenVisibility,
                onLocalOnlyChange = viewModel::setLocalOnly,
                onOpenAttach = onOpenAttach,
                onPollChange = viewModel::setPollEnabled,
                modifier = Modifier.widthIn(max = ComposerMaxWidth).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun ComposerViewModel.handleSuggestionKey(event: KeyEvent): Boolean {
    if (suggestions.value == null || event.type != KeyEventType.KeyDown) return false
    val plain = !event.isCtrlPressed && !event.isMetaPressed
    return when {
        event.key == Key.DirectionDown -> {
            moveSuggestion(1)
            true
        }
        event.key == Key.DirectionUp -> {
            moveSuggestion(-1)
            true
        }
        plain && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Tab) -> {
            acceptSuggestion()
            true
        }
        else -> false
    }
}

@Composable
private fun Modifier.dropHighlight(active: Boolean, label: String): Modifier {
    if (!active) return this
    val color = MaterialTheme.colorScheme.primary
    return this
        .border(3.dp, color, MaterialTheme.shapes.large)
        .background(color.copy(alpha = 0.08f))
        .semantics { stateDescription = label }
}

@Composable
private fun BodyField(viewModel: ComposerViewModel, state: ComposerUiState, focusRequester: FocusRequester) {
    val hint = when (state.target) {
        is ComposerTarget.Reply -> R.string.composer_hint_reply
        is ComposerTarget.Quote -> R.string.composer_hint_quote
        else -> R.string.composer_hint_new
    }
    TextField(
        state = viewModel.text,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent(viewModel::handleSuggestionKey),
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 6),
        placeholder = { Text(stringResource(hint)) },
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = transparentFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, showKeyboardOnFocus = true),
    )
}

@Composable
private fun ContentWarningField(viewModel: ComposerViewModel, onRemove: () -> Unit) {
    val removeLabel = stringResource(R.string.composer_cw_remove)
    TextField(
        state = viewModel.cw,
        modifier = Modifier.fillMaxWidth(),
        lineLimits = TextFieldLineLimits.SingleLine,
        label = { Text(stringResource(R.string.composer_cw_label)) },
        trailingIcon = { TooltipIconButton(label = removeLabel, icon = Icons.Default.Close, onClick = onRemove) },
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun EndedPollWarning() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null)
            Text(stringResource(R.string.composer_poll_ended_warning), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ContextCard(note: Note, environment: NoteEnvironment) {
    val textEnvironment = remember(note.id, environment.mfm) { environment.mfm.withEmoji(environment.noteEmoji(note)) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
            Avatar(name = note.user.displayName, url = note.user.avatarUrl, size = 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                NoteHeader(note, environment)
                val summary = note.cwMfm ?: note.textMfm
                if (summary.isNotEmpty()) {
                    MfmContent(summary, textEnvironment, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                } else {
                    MfmContent(listOf(MfmText(note.user.handle)), textEnvironment, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PostButton(label: String, posting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shapes = ButtonDefaults.shapes()) {
        if (posting) {
            LoadingIndicator(modifier = Modifier.size(24.dp), color = LocalContentColor.current)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.composer_posting))
        } else {
            Text(label)
        }
    }
}

@Composable
private fun ComposerToolbar(
    state: ComposerUiState,
    length: () -> Int,
    onCwChange: (Boolean) -> Unit,
    onOpenVisibility: () -> Unit,
    onLocalOnlyChange: (Boolean) -> Unit,
    onOpenAttach: () -> Unit,
    onPollChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        ToggleTooltipButton(
            label = stringResource(if (state.cwEnabled) R.string.composer_cw_remove else R.string.composer_cw_toggle),
            icon = Icons.Default.WarningAmber,
            checked = state.cwEnabled,
            onCheckedChange = onCwChange,
        )
        VisibilityChip(state.visibility, limited = state.reactionAcceptance != ReactionAcceptance.All, onClick = onOpenVisibility)
        ToggleTooltipButton(
            label = stringResource(if (state.settingsLocked) R.string.composer_local_only_locked else R.string.composer_local_only),
            icon = Icons.Default.PublicOff,
            checked = state.localOnly,
            onCheckedChange = onLocalOnlyChange,
            enabled = !state.settingsLocked && state.visibility != Visibility.Specified,
        )
        TooltipIconButton(
            label = stringResource(R.string.composer_attach),
            icon = Icons.Default.AddPhotoAlternate,
            onClick = onOpenAttach,
            enabled = state.canAttachMore,
        )
        ToggleTooltipButton(
            label = stringResource(if (state.pollEnabled) R.string.poll_remove else R.string.poll_add),
            icon = Icons.Default.Poll,
            checked = state.pollEnabled,
            onCheckedChange = onPollChange,
        )
        Spacer(Modifier.weight(1f))
        Counter(length, state.textLimit)
    }
}

@Composable
fun AttachSheet(visible: Boolean, onLibrary: () -> Unit, onCamera: () -> Unit, onDrive: () -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.composer_attach)
    BottomSheetOverlay(visible = visible, title = title, onDismiss = onDismiss) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstFocus.requestFocus() }
        SheetTitle(title)
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            SegmentedListItem(
                onClick = onLibrary,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                colors = sheetItemColors(),
                leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                modifier = Modifier.focusRequester(firstFocus),
            ) {
                Text(stringResource(R.string.composer_attach_library))
            }
            SegmentedListItem(
                onClick = onCamera,
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = sheetItemColors(),
                leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
            ) {
                Text(stringResource(R.string.composer_attach_camera))
            }
            SegmentedListItem(
                onClick = onDrive,
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                colors = sheetItemColors(),
                leadingContent = { Icon(Icons.Default.Cloud, contentDescription = null) },
            ) {
                Text(stringResource(R.string.composer_attach_drive))
            }
        }
    }
}

@Composable
private fun sheetItemColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

@Composable
private fun SheetTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Counter(length: () -> Int, limit: Int) {
    val value = length()
    val over = value > limit
    Text(
        text = if (over) stringResource(R.string.composer_counter_over, value - limit) else stringResource(R.string.composer_counter, value, limit),
        style = MaterialTheme.typography.labelMedium,
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun ToggleTooltipButton(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = IconButtonDefaults.iconToggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun VisibilityChip(selected: Visibility, limited: Boolean, onClick: () -> Unit) {
    val label = stringResource(selected.labelRes())
    val description = stringResource(R.string.composer_visibility)
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                if (limited) Icons.Default.HeartBroken else selected.icon(),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        modifier = Modifier.semantics { contentDescription = "$description: $label" },
    )
}

@Composable
fun VisibilitySheet(
    visible: Boolean,
    selected: Visibility,
    options: List<Visibility>,
    onSelect: (Visibility) -> Unit,
    acceptance: ReactionAcceptance,
    onAcceptance: (ReactionAcceptance) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.composer_visibility)
    BottomSheetOverlay(visible = visible, title = title, onDismiss = onDismiss) {
        val initialFocus = remember { FocusRequester() }
        val focusTarget = if (selected in options) selected else options.firstOrNull()
        LaunchedEffect(Unit) { if (focusTarget != null) initialFocus.requestFocus() }
        SheetTitle(title)
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier
                .selectableGroup()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            options.forEachIndexed { index, option ->
                SegmentedListItem(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
                    colors = sheetItemColors(),
                    leadingContent = { Icon(option.icon(), contentDescription = null) },
                    supportingContent = { Text(stringResource(option.bodyRes())) },
                    trailingContent = if (option == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = if (option == focusTarget) Modifier.focusRequester(initialFocus) else Modifier,
                ) {
                    Text(stringResource(option.labelRes()))
                }
            }
        }
        SheetTitle(stringResource(R.string.reaction_limit_title))
        val limits = listOf(
            ReactionAcceptance.All,
            ReactionAcceptance.NonSensitiveOnly,
            ReactionAcceptance.LikeOnly,
            ReactionAcceptance.LikeOnlyForRemote,
            ReactionAcceptance.NonSensitiveOnlyForLocalLikeOnlyForRemote,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier
                .selectableGroup()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            limits.forEachIndexed { index, limit ->
                SegmentedListItem(
                    selected = limit == acceptance,
                    onClick = { onAcceptance(limit) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = limits.size),
                    colors = sheetItemColors(),
                    trailingContent = if (limit == acceptance) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                ) {
                    Text(stringResource(limit.labelRes()))
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 12.dp), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_sheet_done)) }
        }
    }
}

@Composable
private fun ComposerPromptOverlay(prompt: ComposerPrompt?, onConfirm: () -> Unit, onDecline: () -> Unit) {
    val titleRes = if (prompt == ComposerPrompt.PollResetsVotes) R.string.composer_prompt_votes_title else R.string.composer_prompt_alt_title
    ModalOverlay(visible = prompt != null, title = stringResource(titleRes), onDismiss = onDecline) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(if (prompt == ComposerPrompt.PollResetsVotes) R.string.composer_prompt_votes_body else R.string.composer_prompt_alt_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            TextButton(onClick = onDecline) {
                Text(stringResource(if (prompt == ComposerPrompt.PollResetsVotes) R.string.note_dialog_cancel else R.string.composer_prompt_alt_add))
            }
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (prompt == ComposerPrompt.PollResetsVotes) R.string.composer_prompt_votes_confirm else R.string.composer_prompt_alt_post))
            }
        }
    }
}

private fun Visibility.icon(): ImageVector = when (this) {
    Visibility.Public -> Icons.Default.Public
    Visibility.Home -> Icons.Default.Home
    Visibility.Followers -> Icons.Default.Lock
    Visibility.Specified -> Icons.Default.Mail
}

private fun Visibility.labelRes(): Int = when (this) {
    Visibility.Public -> R.string.composer_visibility_public
    Visibility.Home -> R.string.composer_visibility_home
    Visibility.Followers -> R.string.composer_visibility_followers
    Visibility.Specified -> R.string.composer_visibility_specified
}

private fun Visibility.bodyRes(): Int = when (this) {
    Visibility.Public -> R.string.composer_visibility_public_body
    Visibility.Home -> R.string.composer_visibility_home_body
    Visibility.Followers -> R.string.composer_visibility_followers_body
    Visibility.Specified -> R.string.composer_visibility_specified_body
}

private fun ComposerUiState.titleRes(): Int = when (target) {
    is ComposerTarget.NewPost, is ComposerTarget.Share -> R.string.composer_title_new
    is ComposerTarget.Reply -> R.string.composer_title_reply
    is ComposerTarget.Quote -> R.string.composer_title_quote
    is ComposerTarget.Edit -> R.string.composer_title_edit
}

private fun ComposerFailure.messageRes(): Int = when (this) {
    ComposerFailure.Generic -> R.string.composer_failed
    ComposerFailure.RateLimited -> R.string.feed_rate_limited
    ComposerFailure.Blocked -> R.string.composer_failed_blocked
    ComposerFailure.TooManyMentions -> R.string.composer_failed_mentions
    ComposerFailure.ProhibitedWords -> R.string.composer_failed_prohibited
    ComposerFailure.TargetGone -> R.string.composer_failed_gone
    ComposerFailure.TooLong -> R.string.composer_failed_too_long
    ComposerFailure.MissingPermission -> R.string.composer_failed_permission
    ComposerFailure.SignInRequired -> R.string.composer_failed_sign_in
    ComposerFailure.TooManyAttachments -> R.string.composer_failed_attachments
    ComposerFailure.PollAlreadyEnded -> R.string.poll_problem_past
    ComposerFailure.UnreadableFile -> R.string.composer_failed_unreadable
    ComposerFailure.UnsupportedShare -> R.string.composer_failed_share_type
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.graphics.shapes.RoundedPolygon
import com.fortind.zenith.R
import com.fortind.zenith.account.ModerationKind
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.ui.PrimaryButton
import com.fortind.zenith.ui.StatusMessage
import com.fortind.zenith.ui.account.AccountEvent
import com.fortind.zenith.ui.account.AccountViewModel
import com.fortind.zenith.ui.account.ModerationEvent
import com.fortind.zenith.ui.account.ModerationViewModel
import com.fortind.zenith.ui.launchCustomTab
import com.fortind.zenith.ui.profile.ProfileUiState
import com.fortind.zenith.ui.theme.isThemeDark
import kotlinx.coroutines.launch

private enum class AccountSection(@param:StringRes val titleRes: Int, @param:StringRes val supportingRes: Int) {
    OVERVIEW(R.string.settings_category_account, R.string.settings_account_supporting),
    EDIT_PROFILE(R.string.account_edit_profile_title, R.string.account_edit_profile_supporting),
    MODERATION(R.string.account_moderation_title, R.string.account_moderation_supporting),
}

@Composable
internal fun AccountPage(
    editProfileRequested: Boolean,
    onEditProfileHandled: () -> Unit,
    onBack: () -> Unit,
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    moderationViewModel: ModerationViewModel = viewModel(factory = ModerationViewModel.Factory),
) {
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    val saveState by accountViewModel.saveState.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(AccountSection.OVERVIEW) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val user = (account as? ProfileUiState.Loaded)?.user
    val form = if (section == AccountSection.EDIT_PROFILE && user != null) accountViewModel.formFor(user) else null
    val dirty by remember(form, user) {
        derivedStateOf {
            form != null && user != null && (
                !form.toForm().diff(user).isEmpty || form.avatarUri != null || form.bannerUri != null
                )
        }
    }
    val valid by remember(form) { derivedStateOf { form?.toForm()?.isValid == true } }

    val leaveSection = {
        if (section == AccountSection.EDIT_PROFILE && dirty && !saveState.saving) {
            confirmingDiscard = true
        } else {
            if (section == AccountSection.EDIT_PROFILE) accountViewModel.discardForm()
            section = AccountSection.OVERVIEW
        }
    }
    val goBack = { if (section == AccountSection.OVERVIEW) onBack() else leaveSection() }

    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = section != AccountSection.OVERVIEW,
        onBackCompleted = leaveSection,
    )

    LaunchedEffect(editProfileRequested, user != null) {
        if (editProfileRequested && user != null) {
            section = AccountSection.EDIT_PROFILE
            onEditProfileHandled()
        }
    }

    LaunchedEffect(accountViewModel) {
        accountViewModel.events.collect { event ->
            when (event) {
                AccountEvent.ProfileSaved -> {
                    section = AccountSection.OVERVIEW
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.account_save_done)) }
                }
            }
        }
    }
    LaunchedEffect(moderationViewModel) {
        moderationViewModel.events.collect { event ->
            when (event) {
                is ModerationEvent.Removed -> launch {
                    val message = when (event.entry.kind) {
                        ModerationKind.Mute -> R.string.moderation_unmuted
                        ModerationKind.RenoteMute -> R.string.moderation_renotes_shown
                        ModerationKind.Block -> R.string.moderation_unblocked
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(message, event.entry.user.displayName),
                        actionLabel = resources.getString(R.string.moderation_undo),
                    )
                    if (result == SnackbarResult.ActionPerformed) moderationViewModel.undo(event.entry)
                }
                is ModerationEvent.Restored -> launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.moderation_restored, event.entry.user.displayName))
                }
                ModerationEvent.RateLimited -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.moderation_rate_limited)) }
                ModerationEvent.Failed -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.moderation_failed)) }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .onPreviewKeyEvent { event ->
                val keyDown = event.type == KeyEventType.KeyDown
                when {
                    keyDown && event.key == Key.Escape && section != AccountSection.OVERVIEW -> {
                        leaveSection()
                        true
                    }
                    keyDown && event.key == Key.S && (event.isCtrlPressed || event.isMetaPressed) &&
                        form != null && user != null && dirty && valid && !saveState.saving -> {
                        accountViewModel.save(user, form)
                        true
                    }
                    else -> false
                }
            },
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(section.titleRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                subtitle = { Text(text = stringResource(section.supportingRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    if (form != null && user != null) {
                        Button(
                            onClick = { accountViewModel.save(user, form) },
                            enabled = dirty && valid && !saveState.saving,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(stringResource(R.string.account_save))
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val current = account) {
                    ProfileUiState.Guest -> AccountMessage(
                        shape = MaterialShapes.Cookie9Sided,
                        titleRes = R.string.account_guest_title,
                        bodyRes = R.string.account_guest_body,
                        actionRes = R.string.account_guest_log_in,
                        onAction = accountViewModel::logIn,
                    )
                    ProfileUiState.Unavailable -> AccountMessage(
                        shape = MaterialShapes.Sunny,
                        titleRes = R.string.account_unavailable_title,
                        bodyRes = R.string.account_unavailable_body,
                        actionRes = R.string.account_retry,
                        onAction = accountViewModel::retry,
                    )
                    is ProfileUiState.Loaded -> when (section) {
                        AccountSection.OVERVIEW -> AccountOverview(
                            user = current.user,
                            onEditProfile = { section = AccountSection.EDIT_PROFILE },
                            onModeration = { section = AccountSection.MODERATION },
                            snackbarHostState = snackbarHostState,
                        )
                        AccountSection.EDIT_PROFILE -> if (form != null) {
                            EditProfileContent(
                                user = current.user,
                                form = form,
                                saveState = saveState,
                            )
                        }
                        AccountSection.MODERATION -> ModerationContent(viewModel = moderationViewModel)
                    }
                }
            }
        }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.account_discard_title)) },
            text = { Text(stringResource(R.string.account_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDiscard = false
                        accountViewModel.discardForm()
                        section = AccountSection.OVERVIEW
                    },
                ) {
                    Text(stringResource(R.string.account_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) { Text(stringResource(R.string.account_discard_keep)) }
            },
        )
    }
}

@Composable
private fun AccountOverview(
    user: CurrentUser,
    onEditProfile: () -> Unit,
    onModeration: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val darkTheme = isThemeDark()
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val scope = rememberCoroutineScope()
    val noBrowser = stringResource(R.string.login_error_no_browser)

    AccountSummary(user = user)
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SettingsRow(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
            action = RowAction.Click(onEditProfile),
            leading = { Icon(Icons.Default.Edit, contentDescription = null) },
            headline = { Text(stringResource(R.string.account_edit_profile_title)) },
            supporting = { Text(stringResource(R.string.account_edit_profile_supporting)) },
        )
        SettingsRow(
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
            action = RowAction.Click(onModeration),
            leading = { Icon(Icons.Default.Block, contentDescription = null) },
            headline = { Text(stringResource(R.string.account_moderation_title)) },
            supporting = { Text(stringResource(R.string.account_moderation_supporting)) },
        )
        SettingsRow(
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
            action = RowAction.Click {
                if (!launchCustomTab(context, FortInstance.SETTINGS_URL, darkTheme, toolbarColor)) {
                    scope.launch { snackbarHostState.showSnackbar(noBrowser) }
                }
            },
            leading = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
            headline = { Text(stringResource(R.string.account_web_title)) },
            supporting = { Text(stringResource(R.string.account_web_supporting)) },
        )
    }
}

@Composable
private fun AccountMessage(
    shape: RoundedPolygon,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    @StringRes actionRes: Int,
    onAction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        StatusMessage(
            shape = shape,
            title = stringResource(titleRes),
            body = stringResource(bodyRes),
        ) {
            PrimaryButton(label = stringResource(actionRes), onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

package com.fortind.zenith.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortind.zenith.R
import com.fortind.zenith.people.FollowListKind
import com.fortind.zenith.ui.PrimaryButton
import com.fortind.zenith.ui.StatusMessage
import com.fortind.zenith.ui.people.OwnProfileActions
import com.fortind.zenith.ui.people.PersonPage
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.social.SocialUi

@Composable
fun ProfileScreen(
    ui: SocialUi,
    onEditProfile: () -> Unit,
    onOpenList: (String, FollowListKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var confirmingLogout by rememberSaveable { mutableStateOf(false) }
    val own = remember(onEditProfile) { OwnProfileActions(onEditProfile = onEditProfile, onLogOut = { confirmingLogout = true }) }

    when (val current = state) {
        is ProfileUiState.Loaded -> {
            LaunchedEffect(ui.people, current.user) { ui.people.refresh(current.user.id) }
            PersonPage(
                userId = current.user.id,
                ui = ui,
                onBack = null,
                onOpenList = { kind -> onOpenList(current.user.id, kind) },
                own = own,
            )
        }
        ProfileUiState.Unavailable, ProfileUiState.Guest -> ProfileMessage(
            state = current,
            refreshing = refreshing,
            onLogOut = { confirmingLogout = true },
            onRetry = viewModel::refresh,
            onLogIn = viewModel::logIn,
            modifier = modifier,
        )
    }

    if (confirmingLogout) {
        LogoutConfirmationSheet(
            onConfirm = {
                confirmingLogout = false
                viewModel.logOut()
            },
            onDismiss = { confirmingLogout = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileMessage(
    state: ProfileUiState,
    refreshing: Boolean,
    onLogOut: () -> Unit,
    onRetry: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            if (state == ProfileUiState.Guest) {
                StatusMessage(
                    shape = MaterialShapes.Cookie9Sided,
                    title = stringResource(R.string.profile_guest_title),
                    body = stringResource(R.string.profile_guest_body),
                ) {
                    PrimaryButton(
                        label = stringResource(R.string.profile_guest_log_in),
                        onClick = onLogIn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                StatusMessage(
                    shape = MaterialShapes.Sunny,
                    title = stringResource(R.string.profile_unavailable_title),
                    body = stringResource(R.string.profile_unavailable_body),
                ) {
                    RetryButton(refreshing = refreshing, onRetry = onRetry)
                    LogOutButton(onClick = onLogOut, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogOutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        contentPadding = ButtonDefaults.contentPaddingFor(size),
        modifier = modifier.heightIn(min = size),
    ) {
        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)))
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
        Text(stringResource(R.string.profile_log_out), style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RetryButton(refreshing: Boolean, onRetry: () -> Unit) {
    val label = stringResource(R.string.profile_retry)
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onRetry,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(size),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = size),
    ) {
        if (refreshing && !rememberReduceMotion()) {
            LoadingIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogoutConfirmationSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_logout_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.profile_logout_confirm_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LogOutButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_logout_stay))
            }
        }
    }
}

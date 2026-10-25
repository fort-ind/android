package com.fortind.zenith.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import com.fortind.zenith.R
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.ui.launchCustomTab
import com.fortind.zenith.ui.rememberMorphingShape
import com.fortind.zenith.ui.rememberReduceMotion
import com.fortind.zenith.ui.theme.isThemeDark
import kotlinx.coroutines.launch

private val AvatarSize = 112.dp
private val AvatarRing = 8.dp

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var confirmingLogout by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val darkTheme = isThemeDark()
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noBrowserMessage = stringResource(R.string.login_error_no_browser)

    Box(modifier = modifier.fillMaxSize()) {
        ProfileContent(
            state = state,
            refreshing = refreshing,
            onManageOnWeb = {
                if (!launchCustomTab(context, FortInstance.SETTINGS_URL, darkTheme, toolbarColor)) {
                    scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                }
            },
            onLogOut = { confirmingLogout = true },
            onRetry = viewModel::refresh,
            onLogIn = viewModel::logIn,
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
fun ProfileContent(
    state: ProfileUiState,
    refreshing: Boolean,
    onManageOnWeb: () -> Unit,
    onLogOut: () -> Unit,
    onRetry: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    // Same single column everywhere, capped and centered; the wider the window, the more air around it.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (state) {
                is ProfileUiState.Loaded -> {
                    ProfileHeader(state.user, wide = wide)
                    ProfileActions(onManageOnWeb = onManageOnWeb, onLogOut = onLogOut)
                }
                ProfileUiState.Unavailable -> StatusMessage(
                    shape = MaterialShapes.Sunny,
                    title = stringResource(R.string.profile_unavailable_title),
                    body = stringResource(R.string.profile_unavailable_body),
                ) {
                    RetryButton(refreshing = refreshing, onRetry = onRetry)
                    LogOutButton(onClick = onLogOut, modifier = Modifier.fillMaxWidth())
                }
                ProfileUiState.Guest -> StatusMessage(
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileHeader(user: CurrentUser, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The bottom padding reserves the half of the avatar that hangs below the banner.
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = (AvatarSize + AvatarRing * 2) / 2)) {
            Banner(
                url = user.bannerUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (wide) 3f else 16f / 9f)
                    .clip(MaterialTheme.shapes.extraExtraLarge),
            )
            Avatar(
                user = user,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 24.dp, y = (AvatarSize + AvatarRing * 2) / 2),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = user.handle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The banner image, over a tonal shape collage that also stands in while loading or when there is none. */
@Composable
private fun Banner(url: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        BannerArt(Modifier.matchParentSize())
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null, // decorative
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BannerArt(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
    ) {
        ArtShape(MaterialShapes.Sunny, colors.secondaryContainer, 180.dp, Modifier.align(Alignment.TopStart).offset(x = (-40).dp, y = (-50).dp))
        ArtShape(MaterialShapes.Cookie9Sided, colors.primary, 120.dp, Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = 20.dp), alpha = 0.25f)
        ArtShape(MaterialShapes.SoftBurst, colors.tertiary, 96.dp, Modifier.align(Alignment.BottomCenter).offset(x = 60.dp, y = 36.dp), alpha = 0.3f)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtShape(
    polygon: RoundedPolygon,
    color: androidx.compose.ui.graphics.Color,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Box(modifier = modifier.size(size).alpha(alpha).clip(polygon.toShape()).background(color))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Avatar(user: CurrentUser, modifier: Modifier = Modifier) {
    // Circle on first frame, then it blooms into a 12-sided cookie.
    val shape = rememberMorphingShape(
        target = MaterialShapes.Cookie12Sided,
        reduceMotion = rememberReduceMotion(),
        start = MaterialShapes.Circle,
    )
    val description = stringResource(R.string.profile_avatar_description)
    // The ring is the page color so the avatar looks cut out of the banner.
    Box(
        modifier = modifier
            .size(AvatarSize + AvatarRing * 2)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = user.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileActions(onManageOnWeb: () -> Unit, onLogOut: () -> Unit) {
    // Two across when they fit, stacked at large font scales.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManageOnWebButton(onClick = onManageOnWeb, modifier = Modifier.weight(1f))
        LogOutButton(onClick = onLogOut, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManageOnWebButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(size),
        modifier = modifier.heightIn(min = size),
    ) {
        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)))
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
        Text(stringResource(R.string.profile_manage_on_web), style = MaterialTheme.typography.titleMedium)
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
private fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val size = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(size),
        modifier = modifier.heightIn(min = size),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
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

/** Centered shape + title + body + actions, used when there is no profile to show. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusMessage(
    shape: RoundedPolygon,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(shape.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        actions()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogoutConfirmationSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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

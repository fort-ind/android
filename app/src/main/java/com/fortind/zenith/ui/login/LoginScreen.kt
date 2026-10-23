package com.fortind.zenith.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.fortind.zenith.R
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.ui.launchCustomTab
import com.fortind.zenith.ui.rememberMorphingShape
import com.fortind.zenith.ui.rememberReduceMotion

@Composable
fun LoginScreen(
    onBrowseWithoutSigningIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.OpenAuthPage -> {
                    if (launchCustomTab(context, event.url, darkTheme, toolbarColor)) {
                        viewModel.onAuthPageLaunched()
                    } else {
                        viewModel.onAuthPageLaunchFailed()
                    }
                }
            }
        }
    }

    // Coming back to the app after launching the tab means the tab was closed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onReturnedToApp() }

    LoginContent(
        state = state,
        onLogIn = viewModel::onSignInClick,
        onCancel = viewModel::onCancel,
        onBrowseWithoutSigningIn = onBrowseWithoutSigningIn,
        modifier = modifier,
    )
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onLogIn: () -> Unit,
    onCancel: () -> Unit,
    onBrowseWithoutSigningIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val expanded = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    val hero: @Composable (Modifier) -> Unit = { m ->
        Hero(state = state, reduceMotion = reduceMotion, modifier = m)
    }
    val actions: @Composable (Modifier) -> Unit = { m ->
        Actions(
            state = state,
            reduceMotion = reduceMotion,
            onLogIn = onLogIn,
            onCancel = onCancel,
            onBrowseWithoutSigningIn = onBrowseWithoutSigningIn,
            modifier = m,
        )
    }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        // fillMaxSize before verticalScroll keeps the content centered, and it still scrolls at large font scales.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (expanded) {
                Row(
                    modifier = Modifier.widthIn(max = 960.dp),
                    horizontalArrangement = Arrangement.spacedBy(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    hero(Modifier.weight(1f))
                    actions(Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier.widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    hero(Modifier)
                    actions(Modifier)
                }
            }
        }
    }
}

@Composable
private fun Hero(state: LoginUiState, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val target = when {
            state.error != null -> LogoShape.Error
            state.signingIn -> LogoShape.SigningIn
            else -> LogoShape.Idle
        }
        MorphingLogo(target = target, reduceMotion = reduceMotion)
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.login_subtitle, FortInstance.HOST),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Actions(
    state: LoginUiState,
    reduceMotion: Boolean,
    onLogIn: () -> Unit,
    onCancel: () -> Unit,
    onBrowseWithoutSigningIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.error?.let { ErrorMessage(it) }

        val signingInLabel = stringResource(R.string.login_signing_in)
        Button(
            onClick = { if (!state.signingIn) onLogIn() },
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.MediumContainerHeight)
                .then(
                    if (state.signingIn) {
                        // Live region: TalkBack announces "Signing in…" when the label swaps for the indicator.
                        Modifier.semantics {
                            contentDescription = signingInLabel
                            liveRegion = LiveRegionMode.Polite
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            when {
                // The indicator is motion, so reduced-motion users get the plain label instead.
                state.signingIn && !reduceMotion -> LoadingIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                )
                state.signingIn -> Text(signingInLabel, style = MaterialTheme.typography.titleMedium)
                state.error != null -> Text(
                    stringResource(R.string.login_button_retry),
                    style = MaterialTheme.typography.titleMedium,
                )
                else -> Text(stringResource(R.string.login_button), style = MaterialTheme.typography.titleMedium)
            }
        }

        if (state.signingIn) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.login_cancel)) }
        }
        TextButton(onClick = onBrowseWithoutSigningIn) {
            Text(stringResource(R.string.login_browse))
        }
    }
}

@Composable
private fun ErrorMessage(error: LoginError, modifier: Modifier = Modifier) {
    val message = when (error) {
        LoginError.Cancelled -> stringResource(R.string.login_error_cancelled)
        LoginError.TimedOut -> stringResource(R.string.login_error_timed_out)
        LoginError.Network -> stringResource(R.string.login_error_network, FortInstance.HOST)
        LoginError.Rejected -> stringResource(R.string.login_error_rejected, FortInstance.HOST)
        LoginError.NoBrowser -> stringResource(R.string.login_error_no_browser)
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private enum class LogoShape { Idle, SigningIn, Error }

/** The M3 Expressive shape each logo state morphs into. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LogoShape.polygon(): RoundedPolygon = when (this) {
    LogoShape.Idle -> MaterialShapes.Cookie9Sided
    LogoShape.SigningIn -> MaterialShapes.Sunny
    LogoShape.Error -> MaterialShapes.Slanted
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingLogo(target: LogoShape, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    // Enters as a pill and morphs into the idle cookie on load.
    val shape = rememberMorphingShape(target.polygon(), reduceMotion, start = MaterialShapes.Pill)
    Box(
        modifier = modifier
            .size(168.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            // Decorative: the headline already names the screen.
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "f",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

package com.fortind.zenith

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isCaptionBarVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.settings.WindowSizeDp
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.ui.composer.ComposerEvent
import com.fortind.zenith.ui.composer.ComposerHost
import com.fortind.zenith.ui.composer.ComposerTarget
import com.fortind.zenith.ui.composer.ComposerViewModel
import com.fortind.zenith.ui.theme.SettingsTheme
import com.fortind.zenith.ui.theme.applySystemBarStyle
import com.fortind.zenith.window.AppWindows
import com.fortind.zenith.window.ComposerWindowTarget
import com.fortind.zenith.window.WindowEvent
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

private const val SIZE_SAVE_DEBOUNCE_MS = 500L
private val MinRememberedWidth = 320.dp
private val MinRememberedHeight = 320.dp

class ComposerWindowActivity : ComponentActivity() {
    private val composer: ComposerViewModel by viewModels { ComposerViewModel.Factory }

    private val container get() = (application as ZenithApplication).container

    private val windows by lazy { container.windowEvents.link(viewModelStore) }

    private var openerWindowId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = AppWindows.composerTarget(intent.data) ?: run {
            finish()
            return
        }
        openerWindowId = savedInstanceState?.getString(AppWindows.EXTRA_OPENER_WINDOW_ID)
            ?: intent.getStringExtra(AppWindows.EXTRA_OPENER_WINDOW_ID)
        AppWindows.setWindowLabel(
            this,
            getString(if (target is ComposerWindowTarget.Edit) R.string.composer_title_edit else R.string.composer_title_new),
        )
        container.emojiRepository.ensureFresh()
        setContent {
            SettingsTheme(onDarkTheme = { applySystemBarStyle(it) }) {
                ComposerWindowRoot(target)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(AppWindows.EXTRA_OPENER_WINDOW_ID)?.let { openerWindowId = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(AppWindows.EXTRA_OPENER_WINDOW_ID, openerWindowId)
    }

    override fun onStart() {
        super.onStart()
        container.foregroundTracker.windowStarted()
    }

    override fun onStop() {
        super.onStop()
        container.foregroundTracker.windowStopped()
    }

    override fun onDestroy() {
        if (isFinishing && composer.uiState.value != null) composer.close(keepDraft = true)
        super.onDestroy()
    }

    @Composable
    private fun ComposerWindowRoot(target: ComposerWindowTarget) {
        val session by container.sessionService.state.collectAsStateWithLifecycle()
        RememberWindowSize()
        when (session) {
            SessionState.Restoring -> Unit
            is SessionState.SignedIn -> SignedInComposer(target)
            SessionState.Guest, SessionState.SignedOut -> LaunchedEffect(Unit) {
                if (openerWindowId == null) openMainWindow() else finish()
            }
        }
    }

    @Composable
    private fun SignedInComposer(target: ComposerWindowTarget) {
        var attempt by remember { mutableIntStateOf(0) }
        var failed by remember { mutableStateOf(false) }
        LaunchedEffect(composer) {
            composer.events.collect { event ->
                if (event is ComposerEvent.Posted) {
                    windows.publish(WindowEvent.NotePosted(event.note, edited = event.target is ComposerTarget.Edit, openerWindowId = openerWindowId))
                }
            }
        }
        LaunchedEffect(attempt) {
            if (composer.uiState.value == null) {
                failed = false
                when (target) {
                    ComposerWindowTarget.NewPost -> composer.open(ComposerTarget.NewPost, null)
                    is ComposerWindowTarget.Edit -> {
                        val note = loadNote(target.noteId)
                        if (note == null) {
                            failed = true
                            return@LaunchedEffect
                        }
                        composer.open(ComposerTarget.Edit(note.id), note)
                    }
                }
            }
            composer.uiState.filterNotNull().first()
            composer.uiState.first { it == null }
            finish()
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            when {
                failed -> LoadFailed(onRetry = { attempt++ }, onClose = ::finish)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            ComposerHost(viewModel = composer, environment = null, onGrantPermission = ::openMainWindow, windowed = true)
        }
    }

    @Composable
    private fun LoadFailed(onRetry: () -> Unit, onClose: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.composer_window_load_failed), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.composer_close)) }
                TextButton(onClick = onRetry) { Text(stringResource(R.string.feed_retry)) }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalLayoutApi::class)
    @Composable
    private fun RememberWindowSize() {
        val windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current
        val desktopWindow = WindowInsets.isCaptionBarVisible
        LaunchedEffect(windowInfo, density, desktopWindow) {
            if (!desktopWindow) return@LaunchedEffect
            snapshotFlow { with(density) { windowInfo.containerSize.width.toDp() to windowInfo.containerSize.height.toDp() } }
                .filter { (width, height) -> width >= MinRememberedWidth && height >= MinRememberedHeight }
                .distinctUntilChanged()
                .debounce(SIZE_SAVE_DEBOUNCE_MS)
                .collect { (width, height) ->
                    container.settingsStore.setComposerWindowSize(WindowSizeDp(width.value.roundToInt(), height.value.roundToInt()))
                }
        }
    }

    private suspend fun loadNote(noteId: String): Note? = try {
        container.notesApi.show(container.sessionService.token(), noteId)
    } catch (_: IOException) {
        null
    }

    private fun openMainWindow() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

package com.fortind.zenith

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.composer.SharedContent
import com.fortind.zenith.composer.SharedUri
import com.fortind.zenith.composer.sharedContent
import com.fortind.zenith.notifications.SCOPE_WRITE_NOTES
import com.fortind.zenith.ui.composer.ComposerHost
import com.fortind.zenith.ui.composer.ComposerViewModel
import com.fortind.zenith.ui.theme.SettingsTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

private const val SCOPE_WRITE_DRIVE = "write:drive"

class ShareActivity : ComponentActivity() {
    private val composer: ComposerViewModel by viewModels { ComposerViewModel.Factory }
    private val share: ShareSession by viewModels { viewModelFactory { initializer { ShareSession() } } }

    private val container get() = (application as ZenithApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            share.pending = intent.readShare() ?: run {
                finish()
                return
            }
            share.started = true
        } else if (!share.started) {
            finish()
            return
        }
        container.emojiRepository.ensureFresh()
        setContent {
            SettingsTheme {
                ShareRoot()
            }
        }
    }

    @Composable
    private fun ShareRoot() {
        val session by container.sessionService.state.collectAsStateWithLifecycle()
        when (session) {
            SessionState.Restoring -> Unit
            is SessionState.SignedIn -> SignedInShare()
            SessionState.Guest, SessionState.SignedOut -> ShareBlocked(
                title = R.string.share_sign_in_title,
                body = R.string.share_sign_in_body,
            )
        }
    }

    @Composable
    private fun SignedInShare() {
        var allowed by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) {
            val sessions = container.sessionService
            allowed = sessions.hasScope(SCOPE_WRITE_NOTES) && sessions.hasScope(SCOPE_WRITE_DRIVE)
        }
        when (allowed) {
            null -> Unit
            false -> ShareBlocked(title = R.string.share_access_title, body = R.string.share_access_body)
            true -> {
                LaunchedEffect(composer) {
                    share.pending?.let { pending ->
                        share.pending = null
                        composer.openShare(pending)
                    }
                    composer.uiState.filterNotNull().first()
                    composer.uiState.first { it == null }
                    finish()
                }
                ComposerHost(viewModel = composer, environment = null, onGrantPermission = ::openApp)
            }
        }
    }

    @Composable
    private fun ShareBlocked(title: Int, body: Int) {
        AlertDialog(
            onDismissRequest = ::finish,
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(body)) },
            confirmButton = { TextButton(onClick = ::openApp) { Text(stringResource(R.string.share_open_app)) } },
            dismissButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.share_close)) } },
        )
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun Intent.readShare(): SharedContent? {
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE && action != Intent.ACTION_VIEW) return null
        return try {
            sharedContent(
                text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                subject = getStringExtra(Intent.EXTRA_SUBJECT) ?: getStringExtra(Intent.EXTRA_TITLE),
                uris = sharedUris().map { SharedUri(it.toString(), it.scheme, it.authority) },
                ownPackage = packageName,
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun Intent.sharedUris(): List<Uri> = when (action) {
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty().filterNotNull()
        Intent.ACTION_VIEW -> listOfNotNull(data)
        else -> listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java))
    }
}

internal class ShareSession : ViewModel() {
    var pending: SharedContent? = null
    var started = false
}

package com.fortind.zenith.ui.games

import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.fortind.zenith.R
import com.fortind.zenith.games.FavoriteNotice
import com.fortind.zenith.games.Game
import com.fortind.zenith.ui.launchCustomTab
import com.fortind.zenith.ui.theme.isThemeDark
import kotlinx.coroutines.launch

class GameActions(
    val open: (Game) -> Unit,
    val copyLink: (Game) -> Unit,
    val share: (Game) -> Unit,
    val toggleFavorite: (Game) -> Unit,
)

@Composable
fun gameActions(snackbarHostState: SnackbarHostState, onToggleFavorite: (Game) -> Unit): GameActions {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val darkTheme = isThemeDark()
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val scope = rememberCoroutineScope()
    val noBrowserMessage = stringResource(R.string.login_error_no_browser)
    val linkCopiedMessage = stringResource(R.string.games_link_copied)

    return GameActions(
        open = { game ->
            if (!launchCustomTab(context, game.url, darkTheme, toolbarColor)) {
                scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
            }
        },
        copyLink = { game ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(game.title, game.url)))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    snackbarHostState.showSnackbar(linkCopiedMessage)
                }
            }
        },
        share = { game ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, game.title)
                .putExtra(Intent.EXTRA_TEXT, game.url)
            context.startActivity(Intent.createChooser(send, game.title))
        },
        toggleFavorite = onToggleFavorite,
    )
}

@Composable
fun FavoriteNoticeEffect(
    notice: FavoriteNotice?,
    snackbarHostState: SnackbarHostState,
    onUndo: (FavoriteNotice) -> Unit,
    onShown: (FavoriteNotice) -> Unit,
) {
    val resources = LocalResources.current
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        val message = resources.getString(
            if (notice.edit.added) R.string.favorites_added else R.string.favorites_removed,
            notice.title,
        )
        val result = try {
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = resources.getString(R.string.favorites_undo),
                duration = SnackbarDuration.Short,
            )
        } finally {
            currentOnShown(notice)
        }
        if (result == SnackbarResult.ActionPerformed) currentOnUndo(notice)
    }
}

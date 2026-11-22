package com.fortind.zenith.auth

import com.fortind.zenith.media.OwnedFolders
import com.fortind.zenith.media.RemoteMediaProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val SignOutCacheFolders: List<String> = OwnedFolders + RemoteMediaProvider.CACHE_DIR

fun signedOutFromAccount(previous: SessionState?, current: SessionState): Boolean =
    current == SessionState.SignedOut && previous is SessionState.SignedIn

fun deleteSignOutCacheFolders(cacheDir: File, folders: List<String> = SignOutCacheFolders) {
    folders.forEach { File(cacheDir, it).deleteRecursively() }
}

class SignOutWipe(
    private val cacheDir: () -> File,
    private val clearImageCaches: () -> Unit,
) {
    fun start(scope: CoroutineScope, states: Flow<SessionState>) {
        scope.launch {
            var previous: SessionState? = null
            states.collect { current ->
                if (signedOutFromAccount(previous, current)) wipe()
                previous = current
            }
        }
    }

    private suspend fun wipe() = withContext(NonCancellable + Dispatchers.IO) {
        clearImageCaches()
        deleteSignOutCacheFolders(cacheDir())
    }
}

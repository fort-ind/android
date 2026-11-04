package com.fortind.zenith.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.account.AccountApi
import com.fortind.zenith.account.ProfileEdit
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.drive.DriveApi
import com.fortind.zenith.media.MediaCompressor
import com.fortind.zenith.media.MediaReader
import com.fortind.zenith.media.MediaSanitizer
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.net.isRateLimited
import com.fortind.zenith.settings.SettingsStore
import com.fortind.zenith.ui.profile.ProfileUiState
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProfileSaveFailure { Offline, RateLimited, ProhibitedName, NotAnImage, FileTooBig, NoFreeSpace, Unreadable }

data class ProfileSaveState(
    val saving: Boolean = false,
    val progress: Float? = null,
    val failure: ProfileSaveFailure? = null,
    val needsPermission: Boolean = false,
)

sealed interface AccountEvent {
    data object ProfileSaved : AccountEvent
}

class AccountViewModel(
    private val session: UserSessionService,
    private val api: AccountApi,
    private val drive: DriveApi,
    private val reader: MediaReader,
    private val compressor: MediaCompressor,
    private val sanitizer: MediaSanitizer,
    private val settings: SettingsStore,
) : ViewModel() {

    val account: StateFlow<ProfileUiState> = session.state
        .mapNotNull { state ->
            when (state) {
                is SessionState.SignedIn -> state.user?.let(ProfileUiState::Loaded) ?: ProfileUiState.Unavailable
                SessionState.Guest -> ProfileUiState.Guest
                SessionState.Restoring, SessionState.SignedOut -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileUiState.Unavailable)

    private val _saveState = MutableStateFlow(ProfileSaveState())
    val saveState: StateFlow<ProfileSaveState> = _saveState.asStateFlow()

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events: Flow<AccountEvent> = _events.receiveAsFlow()

    private var form: ProfileFormState? = null

    init {
        viewModelScope.launch {
            session.credentialsVersion.drop(1).collect { _saveState.value = ProfileSaveState() }
        }
    }

    fun formFor(user: CurrentUser): ProfileFormState = form ?: ProfileFormState(user).also { form = it }

    fun discardForm() {
        form = null
        _saveState.value = ProfileSaveState()
    }

    fun logIn() = session.leaveGuest()

    fun retry() {
        viewModelScope.launch { session.refreshUser() }
    }

    fun save(user: CurrentUser, formState: ProfileFormState) {
        if (_saveState.value.saving) return
        val form = formState.toForm()
        if (!form.isValid) return
        val avatarUri = formState.avatarUri
        val bannerUri = formState.bannerUri
        val edit = form.diff(user)
        if (edit.isEmpty && avatarUri == null && bannerUri == null) return
        viewModelScope.launch {
            _saveState.value = ProfileSaveState(saving = true)
            val uploaded = mutableListOf<String>()
            var token: String? = null
            var saved = false
            var updateSent = false
            var refused = false
            try {
                val sessionToken = session.token() ?: throw TokenRejectedException(0)
                token = sessionToken
                val uploads = listOfNotNull(avatarUri?.let { true to it }, bannerUri?.let { false to it })
                var avatarId: String? = null
                var bannerId: String? = null
                uploads.forEachIndexed { index, (isAvatar, uri) ->
                    val id = upload(sessionToken, uri) { fraction ->
                        _saveState.update { it.copy(progress = (index + fraction) / (uploads.size + 1)) }
                    }
                    uploaded += id
                    if (isAvatar) avatarId = id else bannerId = id
                }
                _saveState.update { it.copy(progress = null) }
                updateSent = true
                val updated = api.updateProfile(sessionToken, edit.copy(avatarId = avatarId, bannerId = bannerId))
                saved = true
                session.applyUpdatedUser(updated)
                this@AccountViewModel.form = null
                _saveState.value = ProfileSaveState()
                _events.trySend(AccountEvent.ProfileSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: TokenRejectedException) {
                refused = true
                _saveState.value = ProfileSaveState()
                session.signOut()
            } catch (_: MissingPermissionException) {
                refused = true
                _saveState.value = ProfileSaveState(needsPermission = true)
            } catch (e: IOException) {
                refused = e is HttpStatusException && e.code in CLIENT_ERROR_RANGE
                _saveState.value = ProfileSaveState(failure = e.toFailure())
            } catch (_: SecurityException) {
                _saveState.value = ProfileSaveState(failure = ProfileSaveFailure.Unreadable)
            } finally {
                val sessionToken = token
                if (!saved && (!updateSent || refused) && sessionToken != null && uploaded.isNotEmpty()) discardUploads(sessionToken, uploaded)
            }
        }
    }

    private suspend fun discardUploads(token: String, ids: List<String>) {
        withContext(NonCancellable) {
            ids.forEach { id ->
                try {
                    drive.delete(token, id)
                } catch (_: IOException) {
                    return@forEach
                }
            }
        }
    }

    private suspend fun upload(token: String, uri: String, onProgress: (Float) -> Unit): String {
        val media = withContext(Dispatchers.IO) { reader.describe(uri) } ?: throw FileNotFoundException(uri)
        if (!media.mimeType.startsWith("image/")) throw NotAnImageException()
        var prepared = media
        if (settings.settings.first().compressUploads) prepared = compressor.compress(prepared) { onProgress(it / 2f) }
        prepared = sanitizer.stripLocation(prepared)
        return drive.upload(token, prepared) { onProgress(0.5f + it / 2f) }.id
    }

    private class NotAnImageException : IOException("Not an image")

    private fun IOException.toFailure(): ProfileSaveFailure = when {
        this is NotAnImageException -> ProfileSaveFailure.NotAnImage
        this is FileNotFoundException -> ProfileSaveFailure.Unreadable
        isRateLimited -> ProfileSaveFailure.RateLimited
        this is HttpStatusException && apiErrorCode == ApiErrorCodes.YOUR_NAME_CONTAINS_PROHIBITED_WORDS -> ProfileSaveFailure.ProhibitedName
        this is HttpStatusException && (apiErrorCode == ApiErrorCodes.AVATAR_NOT_AN_IMAGE || apiErrorCode == ApiErrorCodes.BANNER_NOT_AN_IMAGE) ->
            ProfileSaveFailure.NotAnImage
        this is HttpStatusException && (code == HTTP_PAYLOAD_TOO_LARGE || apiErrorCode == ApiErrorCodes.FILE_TOO_BIG) -> ProfileSaveFailure.FileTooBig
        this is HttpStatusException && apiErrorCode == ApiErrorCodes.NO_FREE_SPACE -> ProfileSaveFailure.NoFreeSpace
        else -> ProfileSaveFailure.Offline
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val HTTP_PAYLOAD_TOO_LARGE = 413
        private val CLIENT_ERROR_RANGE = 400..499

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                AccountViewModel(
                    session = app.container.sessionService,
                    api = app.container.accountApi,
                    drive = app.container.driveApi,
                    reader = app.container.mediaReader,
                    compressor = app.container.mediaCompressor,
                    sanitizer = app.container.mediaSanitizer,
                    settings = app.container.settingsStore,
                )
            }
        }
    }
}

package com.fortind.zenith.ui.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.account.AccountApi
import com.fortind.zenith.account.AccountChoice
import com.fortind.zenith.account.AccountFlag
import com.fortind.zenith.account.AccountSettings
import com.fortind.zenith.account.NotificationFilterType
import com.fortind.zenith.account.NotificationRule
import com.fortind.zenith.account.OlderNotesCutoff
import com.fortind.zenith.account.SettingsEditor
import com.fortind.zenith.account.SettingsEditorCheck
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.emoji.DEFAULT_MAX_CW_LENGTH
import com.fortind.zenith.emoji.InstanceInfo
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.net.isRateLimited
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val NOTIFICATION_CONFIG_KEY = "notificationRecieveConfig"
private const val LANGUAGE_KEY = "lang"

enum class SettingSaveFailure { Offline, RateLimited, InvalidPattern, TooManyWords, CwTooLong }

sealed interface AccountSettingsEvent {
    data class Failed(val failure: SettingSaveFailure) : AccountSettingsEvent

    data object EditorSaved : AccountSettingsEvent
}

data class AccountSettingsUiState(
    val settings: AccountSettings? = null,
    val pending: Map<String, Any> = emptyMap(),
    val needsPermission: Boolean = false,
) {
    fun flag(flag: AccountFlag): Boolean = pending[flag.key] as? Boolean ?: settings?.let(flag.read) ?: false

    fun choice(choice: AccountChoice): String? = pending[choice.key] as? String ?: settings?.let(choice.read)

    fun cutoff(cutoff: OlderNotesCutoff): Long? = when (val value = pending[cutoff.key]) {
        null -> settings?.let(cutoff.read)
        is Long -> value
        else -> null
    }

    val language: String?
        get() = when (val value = pending[LANGUAGE_KEY]) {
            null -> settings?.lang
            is String -> value
            else -> null
        }

    fun notificationRule(type: NotificationFilterType): String? {
        val current = settings ?: return null
        val config = pending[NOTIFICATION_CONFIG_KEY] as? String ?: current.notificationConfig
        return current.copy(notificationConfig = config).notificationRule(type)
    }

    val notificationRulesBusy: Boolean get() = NOTIFICATION_CONFIG_KEY in pending

    val languageBusy: Boolean get() = LANGUAGE_KEY in pending

    fun busy(key: String): Boolean = key in pending
}

class SettingsEditorState(val editor: SettingsEditor, stored: String) {
    val text = TextFieldState(stored)
    var baseline by mutableStateOf(editor.canonical(stored))
        private set
    var saving by mutableStateOf(false)
    var problem by mutableStateOf<SettingsEditorCheck?>(null)
    var problemText by mutableStateOf("")

    val dirty: Boolean get() = editor.canonical(text.text.toString()) != baseline

    val shownProblem: SettingsEditorCheck?
        get() = problem?.takeIf { problemText == text.text.toString() }

    fun storedChanged(stored: String) {
        val keepTyping = dirty
        baseline = editor.canonical(stored)
        if (!keepTyping) text.setTextAndPlaceCursorAtEnd(stored)
    }

    fun saved(canonical: String) {
        baseline = editor.canonical(canonical)
        text.setTextAndPlaceCursorAtEnd(canonical)
    }

    fun revert() {
        text.setTextAndPlaceCursorAtEnd(baseline)
        problem = null
    }
}

class AccountSettingsViewModel(
    private val session: UserSessionService,
    private val api: AccountApi,
    private val instance: InstanceInfo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSettingsUiState(settings = settingsOf(session.state.value)))
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<AccountSettingsEvent>(Channel.BUFFERED)
    val events: Flow<AccountSettingsEvent> = _events.receiveAsFlow()

    private val editors = HashMap<SettingsEditor, SettingsEditorState>()

    init {
        viewModelScope.launch {
            session.state.collect { state ->
                val settings = settingsOf(state)
                _uiState.update { it.copy(settings = settings) }
                if (settings != null) editors.values.forEach { it.storedChanged(it.editor.storedText(settings)) }
            }
        }
        viewModelScope.launch {
            session.credentialsVersion.drop(1).collect {
                editors.clear()
                _uiState.update { it.copy(pending = emptyMap(), needsPermission = false) }
            }
        }
    }

    fun editor(editor: SettingsEditor): SettingsEditorState = editors.getOrPut(editor) {
        SettingsEditorState(editor, editor.storedText(_uiState.value.settings ?: AccountSettings()))
    }

    fun setFlag(flag: AccountFlag, value: Boolean) = write(flag.key, flag.key, value, value)

    fun setChoice(choice: AccountChoice, value: String) = write(choice.key, choice.key, value, value)

    fun setCutoff(cutoff: OlderNotesCutoff, seconds: Long?) =
        write(cutoff.key, cutoff.key, seconds ?: JSONObject.NULL, seconds ?: JSONObject.NULL)

    fun setLanguage(code: String?) = write(LANGUAGE_KEY, LANGUAGE_KEY, code ?: JSONObject.NULL, code ?: JSONObject.NULL)

    fun setNotificationRule(type: NotificationFilterType, rule: NotificationRule) {
        val settings = _uiState.value.settings ?: return
        if (_uiState.value.notificationRulesBusy) return
        val config = settings.notificationConfigWith(type, rule)
        write(NOTIFICATION_CONFIG_KEY, NOTIFICATION_CONFIG_KEY, config, config.toString())
    }

    fun save(editor: SettingsEditor) {
        val state = editor(editor)
        val settings = _uiState.value.settings ?: return
        if (state.saving || !state.dirty) return
        val text = state.text.text.toString()
        val check = editor.check(text, settings, instance.meta.value?.maxCwLength ?: DEFAULT_MAX_CW_LENGTH)
        if (check !is SettingsEditorCheck.Valid) {
            state.problem = check
            state.problemText = text
            return
        }
        state.problem = null
        state.saving = true
        viewModelScope.launch {
            val saved = send(editor.key, check.payload)
            state.saving = false
            if (saved) {
                state.saved(check.canonical)
                _events.trySend(AccountSettingsEvent.EditorSaved)
            }
        }
    }

    private fun write(pendingKey: String, apiKey: String, apiValue: Any, optimistic: Any) {
        if (_uiState.value.busy(pendingKey)) return
        _uiState.update { it.copy(pending = it.pending + (pendingKey to optimistic)) }
        viewModelScope.launch {
            send(apiKey, apiValue)
            _uiState.update { it.copy(pending = it.pending - pendingKey) }
        }
    }

    private suspend fun send(key: String, value: Any): Boolean {
        try {
            val token = session.token() ?: throw TokenRejectedException(0)
            session.applyUpdatedUser(api.updateSetting(token, key, value))
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (_: TokenRejectedException) {
            session.signOut()
        } catch (_: MissingPermissionException) {
            _uiState.update { it.copy(needsPermission = true) }
        } catch (e: IOException) {
            _events.trySend(AccountSettingsEvent.Failed(e.toFailure()))
        }
        return false
    }

    private fun IOException.toFailure(): SettingSaveFailure = when {
        isRateLimited -> SettingSaveFailure.RateLimited
        this is HttpStatusException && apiErrorCode == ApiErrorCodes.INVALID_REGEXP -> SettingSaveFailure.InvalidPattern
        this is HttpStatusException && apiErrorCode == ApiErrorCodes.TOO_MANY_MUTED_WORDS -> SettingSaveFailure.TooManyWords
        this is HttpStatusException && apiErrorCode == ApiErrorCodes.MAX_CW_LENGTH -> SettingSaveFailure.CwTooLong
        else -> SettingSaveFailure.Offline
    }

    private fun settingsOf(state: SessionState): AccountSettings? = (state as? SessionState.SignedIn)?.user?.settings

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                AccountSettingsViewModel(
                    session = app.container.sessionService,
                    api = app.container.accountApi,
                    instance = app.container.emojiRepository,
                )
            }
        }
    }
}

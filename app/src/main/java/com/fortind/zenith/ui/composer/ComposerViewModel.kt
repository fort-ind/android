package com.fortind.zenith.ui.composer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.text.TextRange
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.SessionState
import com.fortind.zenith.auth.TokenRejectedException
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.autocomplete.SuggestionEngine
import com.fortind.zenith.autocomplete.SuggestionsState
import com.fortind.zenith.autocomplete.Trigger
import com.fortind.zenith.autocomplete.TriggerKind
import com.fortind.zenith.autocomplete.findTrigger
import com.fortind.zenith.composer.Attachment
import com.fortind.zenith.composer.AttachmentFailure
import com.fortind.zenith.composer.AttachmentPhase
import com.fortind.zenith.composer.ComposerDraft
import com.fortind.zenith.composer.DraftAttachment
import com.fortind.zenith.composer.DraftStore
import com.fortind.zenith.composer.MAX_ATTACHMENTS
import com.fortind.zenith.composer.toAttachment
import com.fortind.zenith.composer.toDraft
import com.fortind.zenith.composer.toExistingAttachment
import com.fortind.zenith.drive.DriveApi
import com.fortind.zenith.emoji.DEFAULT_MAX_ALT_TEXT_LENGTH
import com.fortind.zenith.emoji.DEFAULT_MAX_CW_LENGTH
import com.fortind.zenith.emoji.DEFAULT_MAX_FILE_SIZE_BYTES
import com.fortind.zenith.emoji.DEFAULT_MAX_NOTE_TEXT_LENGTH
import com.fortind.zenith.emoji.InstanceInfo
import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.media.MediaCompressor
import com.fortind.zenith.media.MediaReader
import com.fortind.zenith.media.MediaSanitizer
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.net.isRateLimited
import com.fortind.zenith.settings.SettingsStore
import com.fortind.zenith.timeline.NewNote
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.timeline.NotesApi
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.timeline.canRenote
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ComposerTarget {
    val draftKey: String?

    data object NewPost : ComposerTarget {
        override val draftKey: String = "new"
    }

    data class Reply(val noteId: String) : ComposerTarget {
        override val draftKey: String = "reply:$noteId"
    }

    data class Quote(val noteId: String) : ComposerTarget {
        override val draftKey: String = "quote:$noteId"
    }

    data class Edit(val noteId: String) : ComposerTarget {
        override val draftKey: String? = null
    }
}

enum class ComposerFailure {
    Generic,
    RateLimited,
    Blocked,
    TooManyMentions,
    ProhibitedWords,
    TargetGone,
    MissingPermission,
    SignInRequired,
    TooManyAttachments,
    UnreadableFile,
}

data class ComposerUiState(
    val target: ComposerTarget,
    val contextNote: Note?,
    val visibility: Visibility,
    val localOnly: Boolean,
    val cwEnabled: Boolean,
    val visibleUserIds: List<String>,
    val textLimit: Int,
    val cwLimit: Int,
    val attachments: List<Attachment> = emptyList(),
    val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
    val maxAltLength: Int = DEFAULT_MAX_ALT_TEXT_LENGTH,
    val posting: Boolean = false,
    val failure: ComposerFailure? = null,
) {
    val visibilityLocked: Boolean
        get() = contextNote?.visibility == Visibility.Specified && target !is ComposerTarget.Quote

    val visibilityOptions: List<Visibility>
        get() = if (visibilityLocked) listOf(Visibility.Specified) else listOf(Visibility.Public, Visibility.Home, Visibility.Followers)

    val needsContext: Boolean get() = target !is ComposerTarget.NewPost

    val attachmentsReady: Boolean get() = attachments.all { it.isReady }

    val canAttachMore: Boolean get() = attachments.size < MAX_ATTACHMENTS
}

sealed interface ComposerEvent {
    data class Posted(val note: Note, val target: ComposerTarget) : ComposerEvent
}

fun codePointLength(text: CharSequence): Int = Character.codePointCount(text, 0, text.length)

class ComposerViewModel(
    private val session: UserSessionService,
    private val api: NotesApi,
    private val drafts: DraftStore,
    private val instance: InstanceInfo,
    private val drive: DriveApi,
    private val reader: MediaReader,
    private val settings: SettingsStore,
    private val compressor: MediaCompressor,
    private val sanitizer: MediaSanitizer,
    private val suggestionEngine: SuggestionEngine,
    private val draftDebounceMs: Long = DRAFT_DEBOUNCE_MS,
) : ViewModel() {

    val text = TextFieldState()
    val cw = TextFieldState()

    private val _uiState = MutableStateFlow<ComposerUiState?>(null)
    val uiState: StateFlow<ComposerUiState?> = _uiState.asStateFlow()

    private val _events = Channel<ComposerEvent>(Channel.BUFFERED)
    val events: Flow<ComposerEvent> = _events.receiveAsFlow()

    val compressUploads: StateFlow<Boolean> = settings.settings
        .map { it.compressUploads }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _suggestions = MutableStateFlow<SuggestionsState?>(null)
    val suggestions: StateFlow<SuggestionsState?> = _suggestions.asStateFlow()

    private var dismissedSuggestion: Pair<TriggerKind, Int>? = null
    private var baseline = ComposerDraft()
    private var baselineFiles: List<String> = emptyList()
    private val uploads = HashMap<String, Job>()

    private val myUserId: String?
        get() = (session.state.value as? SessionState.SignedIn)?.user?.id

    init {
        autosave()
        watchSuggestions()
    }

    @OptIn(ExperimentalFoundationApi::class)
    fun open(target: ComposerTarget, note: Note?, seed: Note? = null) {
        if (seed == null && _uiState.value?.target == target) return
        viewModelScope.launch {
            val stored = if (seed == null) target.draftKey?.let { drafts.load(it) } else null
            baseline = initialDraft(target, note)
            val draft = when {
                seed != null -> seedDraft(seed)
                else -> stored ?: baseline
            }
            val existingFiles = when {
                seed != null -> seed.files
                target is ComposerTarget.Edit -> note?.files.orEmpty()
                else -> null
            }
            val attachments = existingFiles?.map(NoteFile::toExistingAttachment) ?: draft.attachments.map(DraftAttachment::toAttachment)
            baselineFiles = if (target is ComposerTarget.Edit) attachments.map { it.localId } else emptyList()
            cancelUploads()
            val limits = instance.meta.value
            text.setTextAndPlaceCursorAtEnd(draft.text)
            text.undoState.clearHistory()
            cw.setTextAndPlaceCursorAtEnd(draft.cw.orEmpty())
            _uiState.value = ComposerUiState(
                target = target,
                contextNote = note,
                visibility = draft.visibility,
                localOnly = draft.localOnly,
                cwEnabled = draft.cw != null,
                visibleUserIds = recipients(target, note),
                textLimit = limits?.maxNoteTextLength ?: DEFAULT_MAX_NOTE_TEXT_LENGTH,
                cwLimit = limits?.maxCwLength ?: DEFAULT_MAX_CW_LENGTH,
                attachments = attachments,
                maxFileSizeBytes = limits?.maxFileSizeBytes ?: DEFAULT_MAX_FILE_SIZE_BYTES,
                maxAltLength = limits?.maxAltTextLength ?: DEFAULT_MAX_ALT_TEXT_LENGTH,
            )
        }
    }

    fun redraft(deleted: Note) {
        val parent = deleted.reply
        val quoted = deleted.renote?.takeIf { !deleted.isPureRenote }
        when {
            parent != null -> open(ComposerTarget.Reply(parent.id), parent, seed = deleted)
            quoted != null -> open(ComposerTarget.Quote(quoted.id), quoted, seed = deleted)
            else -> open(ComposerTarget.NewPost, null, seed = deleted)
        }
    }

    fun setVisibility(visibility: Visibility) {
        _uiState.update { state -> state?.takeIf { visibility in it.visibilityOptions }?.copy(visibility = visibility) ?: state }
    }

    fun setLocalOnly(localOnly: Boolean) {
        _uiState.update { it?.copy(localOnly = localOnly) }
    }

    fun setCwEnabled(enabled: Boolean) {
        if (!enabled) cw.clearText()
        _uiState.update { it?.copy(cwEnabled = enabled) }
    }

    fun moveSuggestion(delta: Int) {
        _suggestions.update { state ->
            state?.copy(selected = (state.selected + delta).mod(state.items.size))
        }
    }

    fun acceptSuggestion(index: Int? = null) {
        val state = _suggestions.value ?: return
        val item = state.items.getOrNull(index ?: state.selected) ?: return
        val trigger = state.trigger
        _suggestions.value = null
        val current = text.text
        if (trigger.end > current.length || current.substring(trigger.start, trigger.end) != trigger.kind.marker() + trigger.query) return
        text.edit {
            replace(trigger.start, trigger.end, item.insertion)
            selection = TextRange(trigger.start + item.insertion.length)
        }
    }

    fun dismissSuggestions() {
        val state = _suggestions.value ?: return
        dismissedSuggestion = state.trigger.kind to state.trigger.start
        _suggestions.value = null
    }

    fun failureShown() {
        _uiState.update { it?.copy(failure = null) }
    }

    fun addPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val described = withContext(Dispatchers.IO) { uris.map(reader::describe) }
            if (described.any { it == null }) _uiState.update { it?.copy(failure = ComposerFailure.UnreadableFile) }
            addMedia(described.filterNotNull())
        }
    }

    fun addMedia(items: List<LocalMedia>) {
        val state = _uiState.value ?: return
        val room = MAX_ATTACHMENTS - state.attachments.size
        if (items.size > room) _uiState.update { it?.copy(failure = ComposerFailure.TooManyAttachments) }
        items.take(room.coerceAtLeast(0)).forEach { media ->
            val attachment = Attachment(UUID.randomUUID().toString(), media, AttachmentPhase.Uploading)
            _uiState.update { it?.copy(attachments = it.attachments + attachment) }
            startUpload(attachment.localId, forceCompress = false)
        }
    }

    fun retryAttachment(localId: String, compress: Boolean = false) {
        if (_uiState.value?.attachments?.any { it.localId == localId && it.phase == AttachmentPhase.Failed } != true) return
        updateAttachment(localId) { it.copy(phase = AttachmentPhase.Uploading, progress = 0f, failure = null) }
        startUpload(localId, forceCompress = compress)
    }

    fun removeAttachment(localId: String) {
        val attachment = _uiState.value?.attachments?.firstOrNull { it.localId == localId } ?: return
        uploads.remove(localId)?.cancel()
        _uiState.update { state -> state?.copy(attachments = state.attachments.filterNot { it.localId == localId }) }
        deleteFromDrive(listOf(attachment))
    }

    fun setAttachmentDetails(localId: String, altText: String, isSensitive: Boolean) {
        val limit = _uiState.value?.maxAltLength ?: DEFAULT_MAX_ALT_TEXT_LENGTH
        updateAttachment(localId) { it.copy(altText = altText.take(limit), isSensitive = isSensitive) }
    }

    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value ?: return false
        return currentDraft(state) != baseline ||
            attachmentSignature(state) != baselineFiles ||
            state.attachments.any { !it.isReady }
    }

    fun close(keepDraft: Boolean) {
        val state = _uiState.value ?: return
        _uiState.value = null
        _suggestions.value = null
        cancelUploads()
        val key = state.target.draftKey
        val draft = currentDraft(state).takeUnless { it == baseline } ?: ComposerDraft()
        viewModelScope.launch {
            if (key != null) {
                if (keepDraft) drafts.save(key, draft) else drafts.clear(key)
            }
            if (!keepDraft) deleteFromDrive(state.attachments)
        }
    }

    fun submit() {
        val state = _uiState.value ?: return
        if (state.posting) return
        val note = buildNote(state) ?: return
        _uiState.update { it?.copy(posting = true, failure = null) }
        viewModelScope.launch {
            val token = session.token()
            if (token == null) {
                fail(ComposerFailure.SignInRequired)
                return@launch
            }
            try {
                state.attachments.filter { it.needsSync }.forEach { attachment ->
                    attachment.remote?.let { drive.update(token, it.id, attachment.altText, attachment.isSensitive) }
                }
                val result = when (val target = state.target) {
                    is ComposerTarget.Edit -> api.edit(token, target.noteId, note)
                    else -> api.create(token, note)
                }
                state.target.draftKey?.let { drafts.clear(it) }
                _events.trySend(ComposerEvent.Posted(result, state.target))
                _uiState.value = null
            } catch (_: TokenRejectedException) {
                fail(ComposerFailure.SignInRequired)
                session.signOut()
            } catch (e: IOException) {
                fail(e.toFailure())
            }
        }
    }

    private fun watchSuggestions() {
        viewModelScope.launch {
            snapshotFlow { text.text.toString() to text.selection }
                .map { (value, selection) -> if (selection.collapsed) findTrigger(value, selection.start) else null }
                .distinctUntilChanged()
                .collectLatest { trigger -> showSuggestions(trigger) }
        }
    }

    private suspend fun showSuggestions(trigger: Trigger?) {
        if (trigger == null) dismissedSuggestion = null
        val blocked = trigger != null && dismissedSuggestion == (trigger.kind to trigger.start)
        if (trigger == null || blocked || _uiState.value == null) {
            _suggestions.value = null
            return
        }
        if (trigger.kind != TriggerKind.Emoji) delay(SUGGESTION_DEBOUNCE_MS)
        val items = suggestionEngine.suggest(trigger, session.token(), myUserId)
        _suggestions.value = items.takeIf { it.isNotEmpty() }?.let { SuggestionsState(trigger, it) }
    }

    private fun TriggerKind.marker(): String = when (this) {
        TriggerKind.Mention -> "@"
        TriggerKind.Hashtag -> "#"
        TriggerKind.Emoji -> ":"
    }

    private fun startUpload(localId: String, forceCompress: Boolean) {
        uploads.remove(localId)?.cancel()
        uploads[localId] = viewModelScope.launch {
            val attachment = _uiState.value?.attachments?.firstOrNull { it.localId == localId } ?: return@launch
            val maxBytes = _uiState.value?.maxFileSizeBytes ?: DEFAULT_MAX_FILE_SIZE_BYTES
            try {
                val token = session.token()
                if (token == null) {
                    failAttachment(localId, AttachmentFailure.PermissionMissing)
                    fail(ComposerFailure.SignInRequired)
                    return@launch
                }
                val compress = forceCompress || settings.settings.first().compressUploads
                if (!compress && attachment.media.sizeBytes > maxBytes) {
                    failAttachment(localId, AttachmentFailure.TooLarge)
                    return@launch
                }
                var prepared = attachment.media
                if (compress) {
                    updateAttachment(localId) { it.copy(phase = AttachmentPhase.Compressing, progress = 0f, failure = null) }
                    prepared = compressor.compress(prepared) { setProgress(localId, it) }
                }
                prepared = sanitizer.stripLocation(prepared)
                if (prepared.sizeBytes > maxBytes) {
                    failAttachment(localId, AttachmentFailure.TooLarge)
                    return@launch
                }
                updateAttachment(localId) { it.copy(phase = AttachmentPhase.Uploading, progress = 0f, failure = null) }
                val remote = drive.upload(token, prepared) { setProgress(localId, it) }
                updateAttachment(localId) { it.copy(phase = AttachmentPhase.Ready, progress = 1f, failure = null, remote = remote) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: TokenRejectedException) {
                failAttachment(localId, AttachmentFailure.PermissionMissing)
                session.signOut()
            } catch (e: IOException) {
                failAttachment(localId, e.toAttachmentFailure())
                if (e is MissingPermissionException) _uiState.update { it?.copy(failure = ComposerFailure.MissingPermission) }
            } catch (_: SecurityException) {
                failAttachment(localId, AttachmentFailure.Unreadable)
            }
        }
    }

    private fun setProgress(localId: String, value: Float) {
        val current = _uiState.value?.attachments?.firstOrNull { it.localId == localId }?.progress ?: return
        if (value >= 1f || value - current >= PROGRESS_STEP) updateAttachment(localId) { it.copy(progress = value) }
    }

    private fun failAttachment(localId: String, failure: AttachmentFailure) {
        updateAttachment(localId) { it.copy(phase = AttachmentPhase.Failed, failure = failure) }
    }

    private fun updateAttachment(localId: String, transform: (Attachment) -> Attachment) {
        _uiState.update { state ->
            state?.copy(attachments = state.attachments.map { if (it.localId == localId) transform(it) else it })
        }
    }

    private fun cancelUploads() {
        uploads.values.forEach { it.cancel() }
        uploads.clear()
    }

    private fun deleteFromDrive(attachments: List<Attachment>) {
        val files = attachments.filter { it.owned }.mapNotNull { it.remote?.id }
        if (files.isEmpty()) return
        viewModelScope.launch {
            val token = session.token() ?: return@launch
            files.forEach { id -> deleteQuietly(token, id) }
        }
    }

    private suspend fun deleteQuietly(token: String, fileId: String) {
        try {
            drive.delete(token, fileId)
        } catch (_: IOException) {
            return
        }
    }

    private fun fail(failure: ComposerFailure) {
        _uiState.update { it?.copy(posting = false, failure = failure) }
    }

    private fun buildNote(state: ComposerUiState): NewNote? {
        if (!state.attachmentsReady) return null
        val body = text.text.toString().trimEnd()
        val warning = if (state.cwEnabled) cw.text.toString() else null
        val fileIds = state.attachments.mapNotNull { it.remote?.id }
        if (body.isBlank() && fileIds.isEmpty()) return null
        if (codePointLength(body) > state.textLimit) return null
        if (warning != null && codePointLength(warning) > state.cwLimit) return null
        return NewNote(
            text = body.takeIf { it.isNotBlank() },
            cw = warning,
            visibility = state.visibility,
            localOnly = state.localOnly,
            replyId = (state.target as? ComposerTarget.Reply)?.noteId,
            renoteId = (state.target as? ComposerTarget.Quote)?.noteId,
            fileIds = fileIds,
            visibleUserIds = state.visibleUserIds,
        )
    }

    private fun initialDraft(target: ComposerTarget, note: Note?): ComposerDraft = when {
        note == null -> ComposerDraft()
        target is ComposerTarget.Edit -> ComposerDraft(
            text = note.text.orEmpty(),
            cw = note.cw,
            visibility = note.visibility,
            localOnly = note.localOnly,
        )
        target is ComposerTarget.Reply -> ComposerDraft(
            text = if (note.user.id == myUserId) "" else "${note.user.handle} ",
            cw = note.cw,
            visibility = note.visibility,
            localOnly = note.localOnly,
        )
        else -> ComposerDraft(
            visibility = if (note.canRenote(myUserId)) note.visibility else Visibility.Public,
            localOnly = note.localOnly,
        )
    }

    private fun seedDraft(seed: Note): ComposerDraft = ComposerDraft(
        text = seed.text.orEmpty(),
        cw = seed.cw,
        visibility = seed.visibility,
        localOnly = seed.localOnly,
    )

    private fun recipients(target: ComposerTarget, note: Note?): List<String> {
        if (note == null || note.visibility != Visibility.Specified || target is ComposerTarget.Quote) return emptyList()
        val me = myUserId
        return (note.visibleUserIds + note.user.id).filter { it != me }.distinct()
    }

    private fun currentDraft(state: ComposerUiState): ComposerDraft = ComposerDraft(
        text = text.text.toString(),
        cw = if (state.cwEnabled) cw.text.toString() else null,
        visibility = state.visibility,
        localOnly = state.localOnly,
        attachments = state.attachments.mapNotNull(Attachment::toDraft),
    )

    private fun attachmentSignature(state: ComposerUiState): List<String> = state.attachments.map { it.remote?.id ?: it.localId }

    @OptIn(FlowPreview::class)
    private fun autosave() {
        viewModelScope.launch {
            combine(snapshotFlow { text.text.toString() }, snapshotFlow { cw.text.toString() }, _uiState) { _, _, state -> state }
                .debounce(draftDebounceMs)
                .collect {
                    val state = _uiState.value ?: return@collect
                    val key = state.target.draftKey ?: return@collect
                    if (!state.posting) drafts.save(key, currentDraft(state).takeUnless { it == baseline } ?: ComposerDraft())
                }
        }
    }

    private fun IOException.toFailure(): ComposerFailure = when {
        this is MissingPermissionException -> ComposerFailure.MissingPermission
        isRateLimited -> ComposerFailure.RateLimited
        this is HttpStatusException -> when (apiErrorCode) {
            ApiErrorCodes.YOU_HAVE_BEEN_BLOCKED -> ComposerFailure.Blocked
            ApiErrorCodes.CONTAINS_TOO_MANY_MENTIONS -> ComposerFailure.TooManyMentions
            ApiErrorCodes.CONTAINS_PROHIBITED_WORDS -> ComposerFailure.ProhibitedWords
            ApiErrorCodes.NO_SUCH_REPLY_TARGET, ApiErrorCodes.NO_SUCH_RENOTE_TARGET, ApiErrorCodes.NO_SUCH_NOTE -> ComposerFailure.TargetGone
            else -> ComposerFailure.Generic
        }
        else -> ComposerFailure.Generic
    }

    private fun IOException.toAttachmentFailure(): AttachmentFailure = when {
        this is MissingPermissionException -> AttachmentFailure.PermissionMissing
        this is FileNotFoundException -> AttachmentFailure.Unreadable
        this is HttpStatusException && (code == HTTP_PAYLOAD_TOO_LARGE || apiErrorCode == ApiErrorCodes.FILE_TOO_BIG) -> AttachmentFailure.TooLarge
        this is HttpStatusException && apiErrorCode == ApiErrorCodes.NO_FREE_SPACE -> AttachmentFailure.NoSpace
        else -> AttachmentFailure.Network
    }

    companion object {
        const val DRAFT_DEBOUNCE_MS = 400L
        const val SUGGESTION_DEBOUNCE_MS = 200L
        private const val PROGRESS_STEP = 0.02f
        private const val HTTP_PAYLOAD_TOO_LARGE = 413

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                ComposerViewModel(
                    session = app.container.sessionService,
                    api = app.container.notesApi,
                    drafts = app.container.draftStore,
                    instance = app.container.emojiRepository,
                    drive = app.container.driveApi,
                    reader = app.container.mediaReader,
                    settings = app.container.settingsStore,
                    compressor = app.container.mediaCompressor,
                    sanitizer = app.container.mediaSanitizer,
                    suggestionEngine = SuggestionEngine(
                        api = app.container.suggestionsApi,
                        customEmoji = { app.container.emojiRepository.emojis.value },
                        unicodeEmoji = app.container.unicodeEmoji::groups,
                    ),
                )
            }
        }
    }
}

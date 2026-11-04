package com.fortind.zenith.ui.composer

import androidx.compose.runtime.mutableStateListOf
import com.fortind.zenith.autocomplete.SuggestionsApi
import com.fortind.zenith.composer.MAX_POLL_CHOICES
import com.fortind.zenith.composer.MIN_POLL_CHOICES
import com.fortind.zenith.composer.PollDraft
import com.fortind.zenith.composer.SharedContent
import com.fortind.zenith.composer.failedReplySlot
import com.fortind.zenith.composer.isPostableMime
import com.fortind.zenith.composer.mentionsIn
import com.fortind.zenith.composer.pollEditResetsVotes
import com.fortind.zenith.composer.problem
import com.fortind.zenith.composer.replyPrefix
import com.fortind.zenith.composer.replyText
import com.fortind.zenith.drive.DRIVE_PAGE_SIZE
import com.fortind.zenith.drive.DriveFolder
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.timeline.NoteUser
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
import com.fortind.zenith.account.CW_PRIORITY_PARENT
import com.fortind.zenith.account.composerContentWarning
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
import com.fortind.zenith.composer.draftStorageKey
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
import com.fortind.zenith.timeline.ReactionAcceptance
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.timeline.canRenote
import com.fortind.zenith.timeline.toBody
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

    data object Share : ComposerTarget {
        override val draftKey: String? = null
    }
}

class PollChoiceField(val id: Long, initial: String) {
    val text = TextFieldState(initial)
}

enum class ComposerPrompt { MissingAltText, PollResetsVotes }

enum class DriveStatus { Loading, Ready, Failed }

data class DrivePickerState(
    val path: List<DriveFolder> = emptyList(),
    val status: DriveStatus = DriveStatus.Loading,
    val folders: List<DriveFolder> = emptyList(),
    val files: List<NoteFile> = emptyList(),
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val selected: List<NoteFile> = emptyList(),
    val menuFileId: String? = null,
)

enum class ComposerFailure {
    Generic,
    RateLimited,
    Blocked,
    TooManyMentions,
    ProhibitedWords,
    TargetGone,
    TooLong,
    MissingPermission,
    SignInRequired,
    TooManyAttachments,
    UnreadableFile,
    UnsupportedShare,
    PollAlreadyEnded,
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
    val dropsEndedPoll: Boolean = false,
    val pollEnabled: Boolean = false,
    val pollMultiple: Boolean = false,
    val pollEndsAfter: Long? = null,
    val pollEndsAt: Long? = null,
    val reactionAcceptance: ReactionAcceptance = ReactionAcceptance.All,
    val recipients: List<NoteUser> = emptyList(),
    val prompt: ComposerPrompt? = null,
    val confirmedPrompts: Set<ComposerPrompt> = emptySet(),
    val drive: DrivePickerState? = null,
) {
    val settingsLocked: Boolean get() = target is ComposerTarget.Edit

    val visibilityLocked: Boolean
        get() = settingsLocked || (contextNote?.visibility == Visibility.Specified && target !is ComposerTarget.Quote)

    val visibilityOptions: List<Visibility>
        get() = if (visibilityLocked) {
            listOf(contextNote?.visibility ?: visibility)
        } else {
            listOf(Visibility.Public, Visibility.Home, Visibility.Followers, Visibility.Specified)
        }

    val showsRecipients: Boolean get() = visibility == Visibility.Specified && !visibilityLocked

    val isQuote: Boolean get() = target is ComposerTarget.Quote

    fun pollHasEnded(nowMillis: Long): Boolean =
        settingsLocked && contextNote?.poll?.expiresAtMillis?.let { it <= nowMillis } == true

    val needsContext: Boolean get() = target !is ComposerTarget.NewPost && target !is ComposerTarget.Share

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
    private val clock: () -> Long = System::currentTimeMillis,
    private val people: SuggestionsApi = NoPeople,
    private val resolveHandle: suspend (String?, String, String?) -> NoteUser? = { _, _, _ -> null },
    private val remindAltText: Boolean = true,
    private val mentionDebounceMs: Long = MENTION_DEBOUNCE_MS,
    private val cleanupScope: CoroutineScope? = null,
) : ViewModel() {

    val text = TextFieldState()
    val cw = TextFieldState()
    val recipientQuery = TextFieldState()
    val pollChoices = mutableStateListOf<PollChoiceField>()

    private val _recipientResults = MutableStateFlow<List<NoteUser>>(emptyList())
    val recipientResults: StateFlow<List<NoteUser>> = _recipientResults.asStateFlow()

    private var nextPollChoiceId = 0L
    private val mentionLookups = HashMap<String, NoteUser>()
    private val removedRecipients = HashSet<String>()
    private var driveJob: Job? = null
    private var driveLastPath: List<DriveFolder> = emptyList()

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

    private val myUsername: String?
        get() = (session.state.value as? SessionState.SignedIn)?.user?.username

    init {
        autosave()
        watchSuggestions()
        watchRecipientSearch()
        watchMentions()
    }

    private fun seedPollChoices(choices: List<String>) {
        pollChoices.clear()
        choices.forEach { pollChoices += PollChoiceField(nextPollChoiceId++, it) }
    }

    fun setPollEnabled(enabled: Boolean) {
        if (enabled) {
            if (pollChoices.isEmpty()) seedPollChoices(listOf("", ""))
        } else {
            pollChoices.clear()
        }
        _uiState.update { state -> state?.copy(pollEnabled = enabled, pollMultiple = false, pollEndsAfter = null, pollEndsAt = null) }
    }

    fun addPollChoice() {
        if (pollChoices.size < MAX_POLL_CHOICES) pollChoices += PollChoiceField(nextPollChoiceId++, "")
    }

    fun removePollChoice(id: Long) {
        if (pollChoices.size > MIN_POLL_CHOICES) pollChoices.removeAll { it.id == id }
    }

    fun setPollMultiple(multiple: Boolean) {
        _uiState.update { it?.copy(pollMultiple = multiple) }
    }

    fun setPollEndsAfter(millis: Long?) {
        _uiState.update { it?.copy(pollEndsAfter = millis, pollEndsAt = null) }
    }

    fun setPollEndsAt(millis: Long) {
        _uiState.update { it?.copy(pollEndsAfter = null, pollEndsAt = millis) }
    }

    fun pollDraft(state: ComposerUiState): PollDraft? = if (state.pollEnabled) {
        PollDraft(pollChoices.map { it.text.text.toString() }, state.pollMultiple, state.pollEndsAfter, state.pollEndsAt)
    } else {
        null
    }

    fun now(): Long = clock()

    fun setReactionAcceptance(acceptance: ReactionAcceptance) {
        _uiState.update { it?.copy(reactionAcceptance = acceptance) }
    }

    fun addRecipient(user: NoteUser) {
        if (user.id == myUserId) return
        removedRecipients -= user.id
        _uiState.update { state ->
            if (state == null || state.recipients.any { it.id == user.id }) state else state.copy(recipients = state.recipients + user)
        }
        recipientQuery.clearText()
        _recipientResults.value = emptyList()
    }

    fun removeRecipient(userId: String) {
        removedRecipients += userId
        _uiState.update { state -> state?.copy(recipients = state.recipients.filterNot { it.id == userId }) }
    }

    @OptIn(FlowPreview::class)
    private fun watchRecipientSearch() {
        viewModelScope.launch {
            snapshotFlow { recipientQuery.text.toString().trim().removePrefix("@") }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length < MIN_RECIPIENT_QUERY) {
                        _recipientResults.value = emptyList()
                        return@collectLatest
                    }
                    delay(SUGGESTION_DEBOUNCE_MS)
                    val found = try {
                        people.users(session.token(), query, RECIPIENT_RESULTS)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: IOException) {
                        emptyList()
                    }
                    val chosen = _uiState.value?.recipients.orEmpty().mapTo(HashSet()) { it.id }
                    _recipientResults.value = found.filter { it.id != myUserId && it.id !in chosen }
                }
        }
    }

    @OptIn(FlowPreview::class)
    private fun watchMentions() {
        viewModelScope.launch {
            snapshotFlow { text.text.toString() }
                .debounce(mentionDebounceMs)
                .collectLatest { addMentionedRecipients() }
        }
    }

    private suspend fun addMentionedRecipients() {
        val state = _uiState.value ?: return
        if (state.visibility != Visibility.Specified || state.target is ComposerTarget.Edit) return
        val mentions = withContext(Dispatchers.Default) { mentionsIn(MfmParser.parse(text.text.toString())) }
        if (mentions.isEmpty()) return
        val token = session.token()
        mentions.forEach { mention ->
            val key = mention.acct.lowercase()
            val user = mentionLookups[key] ?: try {
                resolveHandle(token, mention.username, mention.host)?.also { mentionLookups[key] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                null
            }
            if (user != null && user.id != myUserId && user.id !in removedRecipients) {
                _uiState.update { current ->
                    if (current == null || current.recipients.any { it.id == user.id }) current else current.copy(recipients = current.recipients + user)
                }
            }
        }
    }

    fun openDrive() {
        _uiState.update { it?.copy(drive = DrivePickerState(path = driveLastPath)) }
        loadDriveFolder(driveLastPath)
    }

    fun closeDrive() {
        driveJob?.cancel()
        _uiState.update { it?.copy(drive = null) }
    }

    fun openDriveFolder(folder: DriveFolder) {
        val current = _uiState.value?.drive ?: return
        navigateDrive(current.path + folder)
    }

    fun driveUp() {
        val current = _uiState.value?.drive ?: return
        if (current.path.isNotEmpty()) navigateDrive(current.path.dropLast(1))
    }

    fun reloadDrive() {
        val current = _uiState.value?.drive ?: return
        navigateDrive(current.path)
    }

    private fun navigateDrive(path: List<DriveFolder>) {
        driveLastPath = path
        updateDrive { it.copy(path = path, status = DriveStatus.Loading, folders = emptyList(), files = emptyList(), endReached = false, loadingMore = false) }
        loadDriveFolder(path)
    }

    private fun loadDriveFolder(path: List<DriveFolder>) {
        driveJob?.cancel()
        driveJob = viewModelScope.launch {
            try {
                val token = session.token() ?: return@launch fail(ComposerFailure.SignInRequired)
                val folderId = path.lastOrNull()?.id
                val folders = drive.folders(token, folderId)
                val files = drive.files(token, folderId, null, DRIVE_PAGE_SIZE)
                updateDrive { it.copy(status = DriveStatus.Ready, folders = folders, files = files, endReached = files.isEmpty()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                updateDrive { it.copy(status = DriveStatus.Failed) }
                if (e is MissingPermissionException) fail(ComposerFailure.MissingPermission)
            }
        }
    }

    fun loadMoreDrive() {
        val current = _uiState.value?.drive ?: return
        val cursor = current.files.lastOrNull()?.id ?: return
        if (current.loadingMore || current.endReached) return
        updateDrive { it.copy(loadingMore = true) }
        driveJob = viewModelScope.launch {
            try {
                val token = session.token() ?: return@launch fail(ComposerFailure.SignInRequired)
                val page = drive.files(token, current.path.lastOrNull()?.id, cursor, DRIVE_PAGE_SIZE)
                updateDrive { state ->
                    val known = state.files.mapTo(HashSet()) { it.id }
                    state.copy(files = state.files + page.filter { known.add(it.id) }, loadingMore = false, endReached = page.isEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                updateDrive { it.copy(loadingMore = false) }
            }
        }
    }

    fun toggleDriveFile(file: NoteFile, slots: Int) {
        updateDrive { state ->
            when {
                state.selected.any { it.id == file.id } -> state.copy(selected = state.selected.filterNot { it.id == file.id })
                state.selected.size < slots -> state.copy(selected = state.selected + file)
                else -> state
            }
        }
    }

    fun openDriveMenu(fileId: String) {
        updateDrive { it.copy(menuFileId = fileId) }
    }

    fun closeDriveMenu() {
        updateDrive { it.copy(menuFileId = null) }
    }

    fun attachDriveSelection() {
        val current = _uiState.value ?: return
        val picked = current.drive?.selected.orEmpty()
        val attached = current.attachments.mapNotNullTo(HashSet()) { it.remote?.id }
        val room = (MAX_ATTACHMENTS - current.attachments.size).coerceAtLeast(0)
        val added = picked.filter { it.id !in attached }.take(room).map(NoteFile::toExistingAttachment)
        driveJob?.cancel()
        _uiState.update { state -> state?.copy(attachments = state.attachments + added, drive = null) }
    }

    private fun updateDrive(transform: (DrivePickerState) -> DrivePickerState) {
        _uiState.update { state -> state?.copy(drive = state.drive?.let(transform)) }
    }

    fun confirmPrompt() {
        val prompt = _uiState.value?.prompt ?: return
        _uiState.update { it?.copy(prompt = null, confirmedPrompts = it.confirmedPrompts + prompt) }
        submit()
    }

    fun dismissPrompt() {
        _uiState.update { it?.copy(prompt = null) }
    }

    @OptIn(ExperimentalFoundationApi::class)
    fun open(target: ComposerTarget, note: Note?, seed: Note? = null, shared: SharedContent? = null) {
        if (seed == null && _uiState.value?.target == target) return
        viewModelScope.launch {
            myUserId?.let { drafts.adopt(it) }
            val stored = if (seed == null) target.draftKey?.let { drafts.load(storageKey(it)) } else null
            baseline = initialDraft(target, note).let { if (shared != null) it.copy(text = shared.text) else it }
            val failedReply = if (seed == null && target is ComposerTarget.Reply && note != null) takeFailedReply(target.noteId) else null
            val draft = when {
                seed != null -> seedDraft(seed)
                failedReply != null && note != null -> (stored ?: baseline).copy(text = replyText(note, failedReply, myUsername))
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
            mentionLookups.clear()
            removedRecipients.clear()
            recipientQuery.clearText()
            _recipientResults.value = emptyList()
            seedPollChoices(draft.poll?.choices.orEmpty())
            text.setTextAndPlaceCursorAtEnd(draft.text)
            if (shared?.caretAtStart == true) text.edit { selection = TextRange.Zero }
            text.undoState.clearHistory()
            cw.setTextAndPlaceCursorAtEnd(draft.cw.orEmpty())
            val opened = ComposerUiState(
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
                pollEnabled = draft.poll != null,
                pollMultiple = draft.poll?.multiple == true,
                pollEndsAfter = draft.poll?.endsAfterMillis,
                pollEndsAt = draft.poll?.endsAtMillis,
                reactionAcceptance = draft.reactionAcceptance,
                recipients = draft.recipients,
            )
            _uiState.value = opened.copy(dropsEndedPoll = opened.pollHasEnded(clock()))
            if (shared?.refusedFiles == true) _uiState.update { it?.copy(failure = ComposerFailure.UnsupportedShare) }
            shared?.uris?.takeIf { it.isNotEmpty() }?.let { addShared(it) }
        }
    }

    fun openShare(shared: SharedContent) = open(ComposerTarget.Share, null, shared = shared)

    private suspend fun takeFailedReply(noteId: String): String? {
        val key = storageKey(failedReplySlot(noteId))
        val text = drafts.load(key)?.text?.takeIf { it.isNotBlank() } ?: return null
        drafts.clear(key)
        return text
    }

    private suspend fun addShared(uris: List<String>) {
        val media = withContext(Dispatchers.IO) { uris.mapNotNull(::describeShared) }.filter { isPostableMime(it.mimeType) }
        if (media.size < uris.size) _uiState.update { it?.copy(failure = ComposerFailure.UnsupportedShare) }
        addMedia(media)
    }

    private fun describeShared(uri: String): LocalMedia? = try {
        reader.describe(uri)
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
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
        _uiState.update { state ->
            state?.takeIf { visibility in it.visibilityOptions }?.copy(
                visibility = visibility,
                localOnly = state.localOnly && visibility != Visibility.Specified,
            ) ?: state
        }
    }

    fun setLocalOnly(localOnly: Boolean) {
        _uiState.update { state ->
            if (state?.settingsLocked == true || state?.visibility == Visibility.Specified) state else state?.copy(localOnly = localOnly)
        }
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
        val key = state.target.draftKey?.let(::storageKey)
        val draft = currentDraft(state).takeUnless { it == baseline } ?: ComposerDraft()
        (cleanupScope ?: viewModelScope).launch {
            if (key != null) {
                if (keepDraft) drafts.save(key, draft) else drafts.clear(key)
            }
            if (!keepDraft) deleteFromDrive(state.attachments)
        }
    }

    fun submit() {
        val state = _uiState.value ?: return
        if (state.posting) return
        if (!state.dropsEndedPoll && state.pollHasEnded(clock())) {
            pollChoices.clear()
            _uiState.update { it?.copy(dropsEndedPoll = true, pollEnabled = false) }
            return
        }
        if (buildNote(state) == null) return
        val edited = state.contextNote?.takeIf { state.target is ComposerTarget.Edit }
        val prompt = when {
            remindAltText && state.target !is ComposerTarget.Edit && state.attachments.any { it.needsAltText } -> ComposerPrompt.MissingAltText
            !state.dropsEndedPoll && pollEditResetsVotes(edited?.poll, pollDraft(state)) -> ComposerPrompt.PollResetsVotes
            else -> null
        }
        if (prompt != null && prompt !in state.confirmedPrompts) {
            _uiState.update { it?.copy(prompt = prompt) }
            return
        }
        _uiState.update { it?.copy(posting = true, failure = null) }
        viewModelScope.launch {
            val token = session.token()
            if (token == null) {
                fail(ComposerFailure.SignInRequired)
                return@launch
            }
            try {
                addMentionedRecipients()
                val note = _uiState.value?.let(::buildNote) ?: return@launch fail(ComposerFailure.Generic)
                state.attachments.filter { it.needsSync }.forEach { attachment ->
                    attachment.remote?.let { drive.update(token, it.id, attachment.altText, attachment.isSensitive) }
                }
                val result = when (val target = state.target) {
                    is ComposerTarget.Edit -> api.edit(token, target.noteId, note)
                    else -> api.create(token, note)
                }
                state.target.draftKey?.let { drafts.clear(storageKey(it)) }
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
        (cleanupScope ?: viewModelScope).launch {
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
        val poll = pollDraft(state)
        if (poll?.problem(clock()) != null) return null
        if (body.isBlank() && fileIds.isEmpty() && poll == null && !state.isQuote) return null
        if (body.length > state.textLimit) return null
        if (warning != null && warning.length > state.cwLimit) return null
        val edited = state.contextNote?.takeIf { state.target is ComposerTarget.Edit }
        return NewNote(
            text = body.takeIf { it.isNotBlank() },
            cw = warning,
            visibility = edited?.visibility ?: state.visibility,
            localOnly = edited?.localOnly ?: state.localOnly,
            replyId = (state.target as? ComposerTarget.Reply)?.noteId,
            renoteId = (state.target as? ComposerTarget.Quote)?.noteId ?: edited?.renote?.id,
            fileIds = fileIds,
            visibleUserIds = (state.visibleUserIds + state.recipients.map { it.id }).distinct(),
            poll = poll?.toBody(),
            reactionAcceptance = state.reactionAcceptance,
        )
    }

    private fun storageKey(slot: String): String = draftStorageKey(myUserId, slot)

    private fun defaultWarning(parentCw: String?): String? {
        val account = (session.state.value as? SessionState.SignedIn)?.user?.settings
        return composerContentWarning(parentCw, account?.defaultCw, account?.defaultCwPriority ?: CW_PRIORITY_PARENT)
    }

    private fun initialDraft(target: ComposerTarget, note: Note?): ComposerDraft = when {
        note == null -> ComposerDraft(cw = defaultWarning(null))
        target is ComposerTarget.Edit -> ComposerDraft(
            text = note.text.orEmpty(),
            cw = note.cw,
            visibility = note.visibility,
            localOnly = note.localOnly,
            poll = note.poll?.takeUnless { poll -> poll.expiresAtMillis?.let { it <= clock() } == true }?.toDraft(clock()),
            reactionAcceptance = note.reactionAcceptance,
        )
        target is ComposerTarget.Reply -> ComposerDraft(
            text = replyPrefix(note, myUsername),
            cw = defaultWarning(note.cw),
            visibility = note.visibility,
            localOnly = note.localOnly,
        )
        else -> ComposerDraft(
            cw = defaultWarning(null),
            visibility = if (note.canRenote(myUserId)) note.visibility else Visibility.Public,
            localOnly = note.localOnly,
        )
    }

    private fun seedDraft(seed: Note): ComposerDraft = ComposerDraft(
        text = seed.text.orEmpty(),
        cw = seed.cw,
        visibility = seed.visibility,
        localOnly = seed.localOnly,
        poll = seed.poll?.toDraft(clock()),
        reactionAcceptance = seed.reactionAcceptance,
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
        poll = pollDraft(state),
        reactionAcceptance = state.reactionAcceptance,
        recipients = state.recipients,
    )

    private fun attachmentSignature(state: ComposerUiState): List<String> = state.attachments.map { it.remote?.id ?: it.localId }

    @OptIn(FlowPreview::class)
    private fun autosave() {
        viewModelScope.launch {
            combine(
                snapshotFlow { text.text.toString() },
                snapshotFlow { cw.text.toString() },
                snapshotFlow { pollChoices.map { it.text.text.toString() } },
                _uiState,
            ) { _, _, _, state -> state }
                .debounce(draftDebounceMs)
                .collect {
                    val state = _uiState.value ?: return@collect
                    val key = state.target.draftKey ?: return@collect
                    if (!state.posting) drafts.save(storageKey(key), currentDraft(state).takeUnless { it == baseline } ?: ComposerDraft())
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
            ApiErrorCodes.MAX_LENGTH, ApiErrorCodes.MAX_CW_LENGTH -> ComposerFailure.TooLong
            ApiErrorCodes.CANNOT_CREATE_ALREADY_EXPIRED_POLL -> ComposerFailure.PollAlreadyEnded
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
        const val MENTION_DEBOUNCE_MS = 1_000L
        const val MIN_RECIPIENT_QUERY = 2
        const val RECIPIENT_RESULTS = 6

        private val NoPeople = object : SuggestionsApi {
            override suspend fun users(token: String?, query: String, limit: Int) = emptyList<NoteUser>()

            override suspend fun frequentUsers(token: String, userId: String, limit: Int) = emptyList<NoteUser>()

            override suspend fun hashtags(token: String?, query: String, limit: Int) = emptyList<String>()
        }
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
                    people = app.container.suggestionsApi,
                    resolveHandle = { token, username, host -> app.container.usersApi.showByHandle(token, username, host).user },
                    cleanupScope = app.container.scope,
                )
            }
        }
    }
}

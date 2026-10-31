package com.fortind.zenith.ui.composer

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.autocomplete.SuggestionEngine
import com.fortind.zenith.autocomplete.SuggestionsApi
import com.fortind.zenith.autocomplete.Trigger
import com.fortind.zenith.autocomplete.TriggerKind
import com.fortind.zenith.composer.AttachmentFailure
import com.fortind.zenith.composer.AttachmentPhase
import com.fortind.zenith.composer.ComposerDraft
import com.fortind.zenith.composer.DraftStore
import com.fortind.zenith.composer.MAX_ATTACHMENTS
import com.fortind.zenith.drive.DriveApi
import com.fortind.zenith.emoji.EmojiIndex
import com.fortind.zenith.emoji.InstanceInfo
import com.fortind.zenith.emoji.InstanceMeta
import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.media.MediaCompressor
import com.fortind.zenith.media.MediaReader
import com.fortind.zenith.media.MediaSanitizer
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.FakeSettingsStore
import com.fortind.zenith.timeline.FakeNotesApi
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteFile
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.timeline.testNote
import com.fortind.zenith.timeline.testUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<ComposerViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        mainDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private class FakeInstance(maxText: Int) : InstanceInfo {
        override val emojis = MutableStateFlow<EmojiIndex?>(EmojiIndex(emptyList()))
        override val meta: StateFlow<InstanceMeta?> =
            MutableStateFlow(InstanceMeta("❤️", ltlAvailable = true, gtlAvailable = true, maxNoteTextLength = maxText))

        override fun ensureFresh() = Unit

        override fun emojiUrl(name: String) = "https://x/$name"
    }

    private class FakeDraftStore : DraftStore {
        val drafts = mutableMapOf<String, ComposerDraft>()

        override suspend fun load(key: String): ComposerDraft? = drafts[key]

        override suspend fun save(key: String, draft: ComposerDraft) {
            if (draft.isBlank) drafts.remove(key) else drafts[key] = draft
        }

        override suspend fun clear(key: String) {
            drafts.remove(key)
        }
    }

    private class FakeDriveApi : DriveApi {
        val uploads = CopyOnWriteArrayList<LocalMedia>()
        val updates = CopyOnWriteArrayList<Triple<String, String, Boolean>>()
        val deleted = CopyOnWriteArrayList<String>()
        val failures = CopyOnWriteArrayList<IOException>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun upload(token: String, media: LocalMedia, onProgress: (Float) -> Unit): NoteFile {
            uploads += media
            gate?.await()
            if (failures.isNotEmpty()) throw failures.removeAt(0)
            onProgress(0.5f)
            return NoteFile(
                id = "file${uploads.size}",
                name = media.name,
                mimeType = media.mimeType,
                url = "https://files/${media.name}",
                thumbnailUrl = null,
                blurhash = null,
                isSensitive = false,
                altText = null,
                width = null,
                height = null,
            )
        }

        override suspend fun update(token: String, fileId: String, altText: String, isSensitive: Boolean) {
            updates += Triple(fileId, altText, isSensitive)
        }

        override suspend fun delete(token: String, fileId: String) {
            deleted += fileId
        }
    }

    private class FakeReader : MediaReader {
        override fun describe(uri: String): LocalMedia? =
            if (uri.contains("unreadable")) null else LocalMedia(uri, uri.substringAfterLast('/'), "image/jpeg", 1024L)

        override fun open(uri: String): InputStream = throw UnsupportedOperationException()
    }

    private class FakeCompressor : MediaCompressor {
        val inputs = CopyOnWriteArrayList<LocalMedia>()

        override suspend fun compress(media: LocalMedia, onProgress: (Float) -> Unit): LocalMedia {
            inputs += media
            return media.copy(uri = "file:///compressed/${media.name}", sizeBytes = media.sizeBytes / 10)
        }
    }

    private class FakeSanitizer : MediaSanitizer {
        val inputs = CopyOnWriteArrayList<LocalMedia>()

        override suspend fun stripLocation(media: LocalMedia): LocalMedia {
            inputs += media
            return media.copy(uri = media.uri + "#clean")
        }
    }

    private class FakeSuggestionsApi : SuggestionsApi {
        var users = emptyList<NoteUser>()

        override suspend fun users(token: String?, query: String, limit: Int): List<NoteUser> = users

        override suspend fun frequentUsers(token: String, userId: String, limit: Int): List<NoteUser> = emptyList()

        override suspend fun hashtags(token: String?, query: String, limit: Int): List<String> = emptyList()
    }

    private class Fixture(maxText: Int, compress: Boolean) {
        val suggestionsApi = FakeSuggestionsApi()
        val engine = SuggestionEngine(suggestionsApi, { null }, { emptyList() })
        val session = UserSessionService(FakeTokenStore("token"), FakeUserApi())
        val api = FakeNotesApi()
        val drafts = FakeDraftStore()
        val drive = FakeDriveApi()
        val compressor = FakeCompressor()
        val sanitizer = FakeSanitizer()
        val settings = FakeSettingsStore(AppSettings(compressUploads = compress))
        val viewModel = ComposerViewModel(
            session, api, drafts, FakeInstance(maxText), drive, FakeReader(), settings, compressor, sanitizer, engine, draftDebounceMs = DEBOUNCE_MS,
        )
    }

    private suspend fun fixture(maxText: Int = 3000, compress: Boolean = false): Fixture {
        val f = Fixture(maxText, compress).also { viewModels += it.viewModel }
        f.session.restore()
        return f
    }

    private fun ComposerViewModel.type(value: String) {
        text.setTextAndPlaceCursorAtEnd(value)
        Snapshot.sendApplyNotifications()
    }

    private suspend fun ComposerViewModel.awaitSettled() {
        uiState.first { it == null || it.failure != null }
    }

    private fun photo(name: String = "photo.jpg", size: Long = 2L * 1024 * 1024) =
        LocalMedia(uri = "content://media/$name", name = name, mimeType = "image/jpeg", sizeBytes = size)

    private suspend fun ComposerViewModel.awaitUploads() {
        uiState.first { state ->
            state != null && state.attachments.none { it.phase == AttachmentPhase.Uploading || it.phase == AttachmentPhase.Compressing }
        }
    }

    private suspend fun FakeDriveApi.awaitDeletion() {
        while (deleted.isEmpty()) yield()
    }

    private fun parent(
        id: String = "p1",
        authorId: String = "u1",
        visibility: Visibility = Visibility.Public,
        cw: String? = null,
        localOnly: Boolean = false,
        visibleUserIds: List<String> = emptyList(),
    ): Note = testNote(id, user = testUser(authorId), visibility = visibility).copy(
        cw = cw,
        cwMfm = cw?.let(MfmParser::parse),
        localOnly = localOnly,
        visibleUserIds = visibleUserIds,
    )

    @Test
    fun replyingPrefillsAMentionAndInheritsTheParentsSettings() = runTest {
        val f = fixture()

        f.viewModel.open(ComposerTarget.Reply("p1"), parent(visibility = Visibility.Home, cw = "spoilers", localOnly = true))

        val state = f.viewModel.uiState.value!!
        assertEquals("@u1 ", f.viewModel.text.text.toString())
        assertEquals("spoilers", f.viewModel.cw.text.toString())
        assertTrue(state.cwEnabled)
        assertEquals(Visibility.Home, state.visibility)
        assertTrue(state.localOnly)
    }

    @Test
    fun replyingToYourselfAddsNoMention() = runTest {
        val f = fixture()

        f.viewModel.open(ComposerTarget.Reply("p1"), parent(authorId = "me"))

        assertEquals("", f.viewModel.text.text.toString())
    }

    @Test
    fun replyingToADirectNoteStaysDirectAndKeepsEveryoneInTheLoop() = runTest {
        val f = fixture()

        f.viewModel.open(ComposerTarget.Reply("p1"), parent(visibility = Visibility.Specified, visibleUserIds = listOf("me", "u2")))

        val state = f.viewModel.uiState.value!!
        assertEquals(listOf(Visibility.Specified), state.visibilityOptions)
        assertEquals(listOf("u2", "u1"), state.visibleUserIds)
        f.viewModel.setVisibility(Visibility.Public)
        assertEquals(Visibility.Specified, f.viewModel.uiState.value!!.visibility)
    }

    @Test
    fun postingAReplySendsItsTargetAndEmitsTheCreatedNote() = runTest {
        val f = fixture()
        val event = async { f.viewModel.events.first() }
        f.viewModel.open(ComposerTarget.Reply("p1"), parent())
        f.viewModel.type("@u1 thanks!")

        f.viewModel.submit()
        val posted = event.await() as ComposerEvent.Posted
        f.viewModel.awaitSettled()

        val sent = f.api.createCalls.single()
        assertEquals("p1", sent.replyId)
        assertEquals("@u1 thanks!", sent.text)
        assertNull(sent.cw)
        assertNull(f.viewModel.uiState.value)
        assertEquals(ComposerTarget.Reply("p1"), posted.target)
    }

    @Test
    fun quotingSendsTheRenoteTarget() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.Quote("p1"), parent())
        f.viewModel.type("look at this")

        f.viewModel.submit()
        f.viewModel.awaitSettled()

        assertEquals("p1", f.api.createCalls.single().renoteId)
    }

    @Test
    fun aDisabledContentWarningIsNotSent() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.cw.setTextAndPlaceCursorAtEnd("leftover")
        f.viewModel.setCwEnabled(false)
        f.viewModel.type("hello")

        f.viewModel.submit()
        f.viewModel.awaitSettled()

        assertNull(f.api.createCalls.single().cw)
    }

    @Test
    fun editingGoesThroughTheEditEndpoint() = runTest {
        val f = fixture()
        f.api.notes["n1"] = parent(id = "n1", authorId = "me")
        f.viewModel.open(ComposerTarget.Edit("n1"), f.api.notes.getValue("n1"))
        f.viewModel.type("fixed typo")

        f.viewModel.submit()
        f.viewModel.awaitSettled()

        assertEquals("n1", f.api.editCalls.single().first)
        assertTrue(f.api.createCalls.isEmpty())
    }

    @Test
    fun blankTextIsNeverSubmitted() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("   \n ")

        f.viewModel.submit()

        assertTrue(f.api.createCalls.isEmpty())
        assertFalse(f.viewModel.uiState.value!!.posting)
    }

    @Test
    fun textOverTheServerLimitIsNeverSubmitted() = runTest {
        val f = fixture(maxText = 5)
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("123456")

        f.viewModel.submit()

        assertTrue(f.api.createCalls.isEmpty())
    }

    @Test
    fun emojiCountAsOneCharacterTowardsTheLimit() {
        assertEquals(3, codePointLength("a😀b"))
    }

    @Test
    fun serverRejectionsKeepTheComposerOpenAndExplainWhy() = runTest {
        val cases = listOf(
            HttpStatusException(400, "YOU_HAVE_BEEN_BLOCKED") to ComposerFailure.Blocked,
            HttpStatusException(400, "CONTAINS_TOO_MANY_MENTIONS") to ComposerFailure.TooManyMentions,
            HttpStatusException(400, "CONTAINS_PROHIBITED_WORDS") to ComposerFailure.ProhibitedWords,
            HttpStatusException(400, "NO_SUCH_REPLY_TARGET") to ComposerFailure.TargetGone,
            HttpStatusException(429, null) to ComposerFailure.RateLimited,
            HttpStatusException(500, null) to ComposerFailure.Generic,
        )
        cases.forEach { (error, expected) ->
            val f = fixture()
            f.api.createFailure = error
            f.viewModel.open(ComposerTarget.NewPost, null)
            f.viewModel.type("hello")

            f.viewModel.submit()
            f.viewModel.awaitSettled()

            val state = f.viewModel.uiState.value!!
            assertEquals(expected, state.failure)
            assertFalse(state.posting)
            assertEquals("hello", f.viewModel.text.text.toString())
        }
    }

    @Test
    fun aTokenWithoutWritePermissionAsksForReauthorization() = runTest {
        val f = fixture()
        f.api.createFailure = MissingPermissionException()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("hello")

        f.viewModel.submit()
        f.viewModel.awaitSettled()

        assertEquals(ComposerFailure.MissingPermission, f.viewModel.uiState.value!!.failure)
    }

    @Test
    fun typingIsAutosavedAsADraftAfterTheDebounce() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.type("work in progress")
        advanceTimeBy(DEBOUNCE_MS - 1)
        assertNull(f.drafts.drafts["new"])
        advanceTimeBy(2)

        assertEquals("work in progress", f.drafts.drafts.getValue("new").text)
    }

    @Test
    fun aKeptDraftComesBackTheNextTimeTheComposerOpens() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("half a thought")
        f.viewModel.close(keepDraft = true)
        advanceTimeBy(DEBOUNCE_MS * 2)

        f.viewModel.type("")
        f.viewModel.open(ComposerTarget.NewPost, null)

        assertEquals("half a thought", f.viewModel.text.text.toString())
    }

    @Test
    fun discardingRemovesTheDraft() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("never mind")
        advanceTimeBy(DEBOUNCE_MS * 2)
        assertEquals("never mind", f.drafts.drafts.getValue("new").text)

        f.viewModel.close(keepDraft = false)
        advanceTimeBy(DEBOUNCE_MS * 2)

        assertNull(f.drafts.drafts["new"])
        assertNull(f.viewModel.uiState.value)
    }

    @Test
    fun anUntouchedReplyPrefillIsNotWorthKeeping() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.Reply("p1"), parent())

        assertFalse(f.viewModel.hasUnsavedChanges())
        f.viewModel.close(keepDraft = true)
        advanceTimeBy(DEBOUNCE_MS * 2)

        assertTrue(f.drafts.drafts.isEmpty())
    }

    @Test
    fun repliesToDifferentNotesKeepSeparateDrafts() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.Reply("p1"), parent(id = "p1", authorId = "me"))
        f.viewModel.type("for the first")
        f.viewModel.close(keepDraft = true)
        advanceTimeBy(DEBOUNCE_MS * 2)

        f.viewModel.open(ComposerTarget.Reply("p2"), parent(id = "p2", authorId = "me"))

        assertEquals("", f.viewModel.text.text.toString())
        assertEquals("for the first", f.drafts.drafts.getValue("reply:p1").text)
    }


    @Test
    fun anAttachedPhotoUploadsAndBecomesReady() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()

        val attachment = f.viewModel.uiState.value!!.attachments.single()
        assertEquals(AttachmentPhase.Ready, attachment.phase)
        assertEquals("file1", attachment.remote!!.id)
        assertEquals(1, f.drive.uploads.size)
    }

    @Test
    fun aPostWithOnlyAPhotoIsAllowedAndCarriesItsFileId() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()

        f.viewModel.submit()
        f.viewModel.awaitSettled()

        val sent = f.api.createCalls.single()
        assertEquals(listOf("file1"), sent.fileIds)
        assertNull(sent.text)
    }

    @Test
    fun postingWaitsUntilEveryUploadHasFinished() = runTest {
        val f = fixture()
        f.drive.gate = CompletableDeferred()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("with a photo")
        f.viewModel.addMedia(listOf(photo()))

        f.viewModel.submit()

        assertTrue(f.api.createCalls.isEmpty())
        assertFalse(f.viewModel.uiState.value!!.posting)
        f.drive.gate!!.complete(Unit)
    }

    @Test
    fun everyPhotoHasItsLocationStrippedBeforeUpload() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()

        assertEquals(1, f.sanitizer.inputs.size)
        assertEquals("content://media/photo.jpg#clean", f.drive.uploads.single().uri)
    }

    @Test
    fun anOversizedFileFailsWithoutBeingUploadedWhenCompressionIsOff() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.addMedia(listOf(photo(size = 40L * 1024 * 1024)))
        f.viewModel.awaitUploads()

        val attachment = f.viewModel.uiState.value!!.attachments.single()
        assertEquals(AttachmentPhase.Failed, attachment.phase)
        assertEquals(AttachmentFailure.TooLarge, attachment.failure)
        assertTrue(f.drive.uploads.isEmpty())
        assertTrue(f.compressor.inputs.isEmpty())
    }

    @Test
    fun theCompressionSettingShrinksFilesBeforeUpload() = runTest {
        val f = fixture(compress = true)
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.addMedia(listOf(photo(size = 20L * 1024 * 1024)))
        f.viewModel.awaitUploads()

        assertEquals(1, f.compressor.inputs.size)
        assertEquals(2L * 1024 * 1024, f.drive.uploads.single().sizeBytes)
        assertEquals(AttachmentPhase.Ready, f.viewModel.uiState.value!!.attachments.single().phase)
    }

    @Test
    fun compressingJustThisOneRescuesAnOversizedFileWithoutTheSetting() = runTest {
        val f = fixture(compress = false)
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.addMedia(listOf(photo(size = 40L * 1024 * 1024)))
        f.viewModel.awaitUploads()
        val id = f.viewModel.uiState.value!!.attachments.single().localId

        f.viewModel.retryAttachment(id, compress = true)
        f.viewModel.awaitUploads()

        assertEquals(1, f.compressor.inputs.size)
        assertEquals(AttachmentPhase.Ready, f.viewModel.uiState.value!!.attachments.single().phase)
        assertFalse(f.settings.current.compressUploads)
    }

    @Test
    fun aFailedUploadCanBeRetried() = runTest {
        val f = fixture()
        f.drive.failures += IOException("offline")
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()
        val failed = f.viewModel.uiState.value!!.attachments.single()
        assertEquals(AttachmentFailure.Network, failed.failure)

        f.viewModel.retryAttachment(failed.localId)
        f.viewModel.awaitUploads()

        assertEquals(AttachmentPhase.Ready, f.viewModel.uiState.value!!.attachments.single().phase)
    }

    @Test
    fun aMissingDrivePermissionAsksForReauthorization() = runTest {
        val f = fixture()
        f.drive.failures += MissingPermissionException()
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()

        val state = f.viewModel.uiState.value!!
        assertEquals(AttachmentFailure.PermissionMissing, state.attachments.single().failure)
        assertEquals(ComposerFailure.MissingPermission, state.failure)
    }

    @Test
    fun removingAnUploadedFileDeletesItFromDrive() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()

        f.viewModel.removeAttachment(f.viewModel.uiState.value!!.attachments.single().localId)
        f.drive.awaitDeletion()

        assertEquals(listOf("file1"), f.drive.deleted.toList())
        assertTrue(f.viewModel.uiState.value!!.attachments.isEmpty())
    }

    @Test
    fun discardingTheComposerCleansUpItsUploads() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()

        f.viewModel.close(keepDraft = false)
        f.drive.awaitDeletion()

        assertEquals(listOf("file1"), f.drive.deleted.toList())
    }

    @Test
    fun keepingTheDraftKeepsUploadsAndRestoresThemNextTime() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()
        f.viewModel.close(keepDraft = true)
        advanceTimeBy(DEBOUNCE_MS * 2)

        f.viewModel.open(ComposerTarget.NewPost, null)

        val restored = f.viewModel.uiState.value!!.attachments.single()
        assertEquals(AttachmentPhase.Ready, restored.phase)
        assertEquals("file1", restored.remote!!.id)
        assertTrue(f.drive.deleted.isEmpty())
    }

    @Test
    fun editingKeepsExistingFilesAndNeverDeletesThemFromDrive() = runTest {
        val f = fixture()
        val existing = NoteFile("old1", "a.png", "image/png", "https://files/a.png", null, null, false, "alt", null, null)
        val note = parent(id = "n1", authorId = "me").copy(files = listOf(existing))
        f.api.notes["n1"] = note
        f.viewModel.open(ComposerTarget.Edit("n1"), note)
        f.viewModel.type("edited")

        assertEquals("old1", f.viewModel.uiState.value!!.attachments.single().remote!!.id)
        f.viewModel.removeAttachment("old1")
        f.viewModel.submit()
        f.viewModel.awaitSettled()

        assertTrue(f.drive.deleted.isEmpty())
        assertTrue(f.api.editCalls.single().second.fileIds.isEmpty())
    }

    private fun deletedPost(): Note {
        val file = NoteFile("old1", "a.png", "image/png", "https://files/a.png", null, null, false, "alt", null, null)
        return testNote("d1", user = testUser("me"), text = "oops typo")
            .copy(cw = "spoilers", visibility = Visibility.Home, localOnly = true, files = listOf(file))
    }

    @Test
    fun redraftingSeedsANewPostFromTheDeletedNoteAndIgnoresStoredDrafts() = runTest {
        val f = fixture()
        f.drafts.drafts["new"] = ComposerDraft(text = "an older draft")

        f.viewModel.redraft(deletedPost())

        val state = f.viewModel.uiState.value!!
        assertEquals(ComposerTarget.NewPost, state.target)
        assertEquals("oops typo", f.viewModel.text.text.toString())
        assertEquals("spoilers", f.viewModel.cw.text.toString())
        assertTrue(state.cwEnabled)
        assertEquals(Visibility.Home, state.visibility)
        assertTrue(state.localOnly)
        assertEquals("old1", state.attachments.single().remote!!.id)
        assertTrue(f.viewModel.hasUnsavedChanges())
    }

    @Test
    fun redraftingAReplyKeepsItAReplyToTheSameParent() = runTest {
        val f = fixture()
        val parentNote = parent(id = "p1", authorId = "u2")

        f.viewModel.redraft(deletedPost().copy(reply = parentNote))

        val state = f.viewModel.uiState.value!!
        assertEquals(ComposerTarget.Reply("p1"), state.target)
        assertEquals(parentNote, state.contextNote)
        assertEquals("oops typo", f.viewModel.text.text.toString())
    }

    @Test
    fun redraftingAQuoteKeepsTheQuotedNote() = runTest {
        val f = fixture()
        val quoted = parent(id = "q1", authorId = "u2")

        f.viewModel.redraft(deletedPost().copy(renote = quoted))

        assertEquals(ComposerTarget.Quote("q1"), f.viewModel.uiState.value!!.target)
    }

    @Test
    fun redraftedFilesAreNeverDeletedFromDriveWhenRemoved() = runTest {
        val f = fixture()
        f.viewModel.redraft(deletedPost())

        f.viewModel.removeAttachment("old1")

        assertTrue(f.drive.deleted.isEmpty())
        assertTrue(f.viewModel.uiState.value!!.attachments.isEmpty())
    }

    @Test
    fun altTextAndSensitivityAreSyncedBeforeThePostGoesOut() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.addMedia(listOf(photo()))
        f.viewModel.awaitUploads()
        f.viewModel.setAttachmentDetails(f.viewModel.uiState.value!!.attachments.single().localId, "a cat on a sofa", true)

        f.viewModel.submit()
        f.viewModel.awaitSettled()

        assertEquals(listOf(Triple("file1", "a cat on a sofa", true)), f.drive.updates.toList())
        assertEquals(1, f.api.createCalls.size)
    }

    @Test
    fun noMoreThanTheMaximumNumberOfFilesCanBeAttached() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.addMedia((1..MAX_ATTACHMENTS + 1).map { photo("p$it.jpg") })
        f.viewModel.awaitUploads()

        val state = f.viewModel.uiState.value!!
        assertEquals(MAX_ATTACHMENTS, state.attachments.size)
        assertEquals(ComposerFailure.TooManyAttachments, state.failure)
    }

    @Test
    fun pickedUrisAreDescribedAndUploaded() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.addPicked(listOf("content://picker/one.jpg", "content://picker/two.jpg"))
        f.viewModel.uiState.first { it != null && it.attachments.size == 2 }
        f.viewModel.awaitUploads()

        assertEquals(listOf("one.jpg", "two.jpg"), f.drive.uploads.map { it.name }.sorted())
    }

    @Test
    fun anUnreadablePickedFileIsReportedWithoutBlockingTheOthers() = runTest {
        val f = fixture()
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.addPicked(listOf("content://picker/unreadable.jpg", "content://picker/good.jpg"))
        f.viewModel.uiState.first { it != null && it.attachments.size == 1 }
        f.viewModel.awaitUploads()

        val state = f.viewModel.uiState.value!!
        assertEquals(ComposerFailure.UnreadableFile, state.failure)
        assertEquals("good.jpg", state.attachments.single().media.name)
    }

    @Test
    fun typingAMentionOffersPeopleAndAcceptingReplacesTheToken() = runTest {
        val f = fixture()
        f.suggestionsApi.users = listOf(testUser("u1", "alice"))
        f.viewModel.open(ComposerTarget.NewPost, null)

        f.viewModel.type("hi @al")
        advanceTimeBy(SUGGESTION_WAIT_MS)
        val state = f.viewModel.suggestions.first { it != null }!!

        assertEquals(Trigger(TriggerKind.Mention, "al", 3, 6), state.trigger)
        f.viewModel.acceptSuggestion()
        assertEquals("hi @alice ", f.viewModel.text.text.toString())
        assertNull(f.viewModel.suggestions.value)
    }

    @Test
    fun aDismissedSuggestionStaysGoneUntilANewTokenStarts() = runTest {
        val f = fixture()
        f.suggestionsApi.users = listOf(testUser("u1", "alice"))
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("@al")
        advanceTimeBy(SUGGESTION_WAIT_MS)
        f.viewModel.suggestions.first { it != null }

        f.viewModel.dismissSuggestions()
        assertNull(f.viewModel.suggestions.value)
        f.viewModel.type("@ali")
        advanceTimeBy(SUGGESTION_WAIT_MS)
        assertNull(f.viewModel.suggestions.value)

        f.viewModel.type("@ali and @bo")
        advanceTimeBy(SUGGESTION_WAIT_MS)
        val reopened = f.viewModel.suggestions.first { it != null }!!
        assertEquals(9, reopened.trigger.start)
    }

    @Test
    fun theSelectionWrapsAroundTheSuggestions() = runTest {
        val f = fixture()
        f.suggestionsApi.users = listOf(testUser("u1", "a1"), testUser("u2", "a2"), testUser("u3", "a3"))
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("@a")
        advanceTimeBy(SUGGESTION_WAIT_MS)
        f.viewModel.suggestions.first { it != null }

        f.viewModel.moveSuggestion(-1)
        assertEquals(2, f.viewModel.suggestions.value!!.selected)
        f.viewModel.moveSuggestion(1)
        assertEquals(0, f.viewModel.suggestions.value!!.selected)
        f.viewModel.moveSuggestion(1)
        f.viewModel.acceptSuggestion()
        assertEquals("@a2 ", f.viewModel.text.text.toString())
    }

    @Test
    fun aSuggestionForTextThatChangedUnderneathItNeverCorruptsTheText() = runTest {
        val f = fixture()
        f.suggestionsApi.users = listOf(testUser("u1", "alice"))
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("@al")
        advanceTimeBy(SUGGESTION_WAIT_MS)
        f.viewModel.suggestions.first { it != null }

        f.viewModel.text.setTextAndPlaceCursorAtEnd("zzz")
        f.viewModel.acceptSuggestion()

        assertEquals("zzz", f.viewModel.text.text.toString())
    }

    @Test
    fun closingTheComposerClearsAnyOpenSuggestions() = runTest {
        val f = fixture()
        f.suggestionsApi.users = listOf(testUser("u1", "alice"))
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.type("@al")
        advanceTimeBy(SUGGESTION_WAIT_MS)
        f.viewModel.suggestions.first { it != null }

        f.viewModel.close(keepDraft = false)

        assertNull(f.viewModel.suggestions.value)
    }

    @Test
    fun anUnfinishedUploadCountsAsUnsavedWork() = runTest {
        val f = fixture()
        f.drive.gate = CompletableDeferred()
        f.viewModel.open(ComposerTarget.NewPost, null)
        f.viewModel.addMedia(listOf(photo()))

        assertTrue(f.viewModel.hasUnsavedChanges())
        f.drive.gate!!.complete(Unit)
    }

    private companion object {
        const val DEBOUNCE_MS = 100L
        const val SUGGESTION_WAIT_MS = 250L
    }
}

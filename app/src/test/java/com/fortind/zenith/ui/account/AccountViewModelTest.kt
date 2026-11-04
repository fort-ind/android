package com.fortind.zenith.ui.account

import androidx.lifecycle.viewModelScope
import com.fortind.zenith.account.AccountApi
import com.fortind.zenith.account.ProfileEdit
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.drive.DriveApi
import com.fortind.zenith.drive.DriveFolder
import com.fortind.zenith.media.LocalMedia
import com.fortind.zenith.media.MediaReader
import com.fortind.zenith.media.NoCompression
import com.fortind.zenith.media.NoSanitizing
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.FakeUserApi
import com.fortind.zenith.notifications.awaitTrue
import com.fortind.zenith.settings.FakeSettingsStore
import com.fortind.zenith.timeline.NoteFile
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SETTLE_MS = 150L

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val models = ArrayList<AccountViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        runBlocking { models.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private class FakeAccount : AccountApi {
        var failure: IOException? = null
        val edits = CopyOnWriteArrayList<ProfileEdit>()

        override suspend fun updateProfile(token: String, edit: ProfileEdit): CurrentUser {
            edits += edit
            failure?.let { throw it }
            return CurrentUser("me", "me", "New", null, null)
        }

        override suspend fun updateSetting(token: String, key: String, value: Any) = error("unused")
    }

    private class FakeDrive : DriveApi {
        val uploads = CopyOnWriteArrayList<String>()
        val deleted = CopyOnWriteArrayList<String>()
        var failOnUpload: Int? = null

        override suspend fun upload(token: String, media: LocalMedia, onProgress: (Float) -> Unit): NoteFile {
            uploads += media.name
            if (failOnUpload == uploads.size) throw IOException("offline")
            return NoteFile("file${uploads.size}", media.name, media.mimeType, "https://files/${media.name}", null, null, false, null, null, null)
        }

        override suspend fun update(token: String, fileId: String, altText: String, isSensitive: Boolean) = Unit

        override suspend fun delete(token: String, fileId: String) {
            deleted += fileId
        }

        override suspend fun files(token: String, folderId: String?, untilId: String?, limit: Int): List<NoteFile> = emptyList()

        override suspend fun folders(token: String, folderId: String?): List<DriveFolder> = emptyList()
    }

    private class FakeReader : MediaReader {
        override fun describe(uri: String) = LocalMedia(uri, uri.substringAfterLast('/'), "image/png", 100L)

        override fun open(uri: String): InputStream = throw UnsupportedOperationException()
    }

    private class Setup {
        val account = FakeAccount()
        val drive = FakeDrive()
        val user = CurrentUser("me", "me", "Old", null, null)
        val form = ProfileFormState(user)
    }

    private suspend fun model(setup: Setup): AccountViewModel {
        val session = UserSessionService(FakeTokenStore("token"), FakeUserApi())
        session.restore()
        return AccountViewModel(session, setup.account, setup.drive, FakeReader(), NoCompression, NoSanitizing, FakeSettingsStore())
            .also(models::add)
    }

    private suspend fun AccountViewModel.finishSaving() {
        awaitTrue { !saveState.value.saving }
        withContext(Dispatchers.Default) { delay(SETTLE_MS) }
    }

    @Test
    fun uploadsAreDeletedWhenTheServerRefusesTheProfileUpdate() = runTest {
        val setup = Setup().apply { account.failure = HttpStatusException(400, "YOUR_NAME_CONTAINS_PROHIBITED_WORDS") }
        val model = model(setup)
        setup.form.avatarUri = "content://pick/avatar.png"
        setup.form.bannerUri = "content://pick/banner.png"

        model.save(setup.user, setup.form)
        model.finishSaving()

        assertEquals(listOf("file1", "file2"), setup.drive.deleted)
    }

    @Test
    fun aFailedSecondUploadDeletesTheFirst() = runTest {
        val setup = Setup().apply { drive.failOnUpload = 2 }
        val model = model(setup)
        setup.form.avatarUri = "content://pick/avatar.png"
        setup.form.bannerUri = "content://pick/banner.png"

        model.save(setup.user, setup.form)
        model.finishSaving()

        assertEquals(listOf("file1"), setup.drive.deleted)
        assertTrue(setup.account.edits.isEmpty())
    }

    @Test
    fun uploadsAreKeptWhenTheOutcomeOfTheUpdateIsUnknown() = runTest {
        val setup = Setup().apply { account.failure = IOException("timed out") }
        val model = model(setup)
        setup.form.avatarUri = "content://pick/avatar.png"

        model.save(setup.user, setup.form)
        model.finishSaving()

        assertTrue(setup.drive.deleted.isEmpty())
    }

    @Test
    fun uploadsAreKeptWhenTheUpdateWorked() = runTest {
        val setup = Setup()
        val model = model(setup)
        setup.form.avatarUri = "content://pick/avatar.png"

        model.save(setup.user, setup.form)
        model.finishSaving()

        assertEquals("file1", setup.account.edits.single().avatarId)
        assertTrue(setup.drive.deleted.isEmpty())
    }
}

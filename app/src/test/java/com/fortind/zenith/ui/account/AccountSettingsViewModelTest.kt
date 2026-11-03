package com.fortind.zenith.ui.account

import com.fortind.zenith.account.AccountApi
import com.fortind.zenith.account.AccountChoice
import com.fortind.zenith.account.AccountFlag
import com.fortind.zenith.account.AccountSettings
import com.fortind.zenith.account.MuteWordSet
import com.fortind.zenith.account.MuteWordsParse
import com.fortind.zenith.account.NotificationFilterType
import com.fortind.zenith.account.NotificationRule
import com.fortind.zenith.account.OlderNotesCutoff
import com.fortind.zenith.account.ProfileEdit
import com.fortind.zenith.account.SettingsEditor
import com.fortind.zenith.account.SettingsEditorCheck
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.UserApi
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.emoji.EmojiIndex
import com.fortind.zenith.emoji.InstanceInfo
import com.fortind.zenith.emoji.InstanceMeta
import com.fortind.zenith.net.ApiErrorCodes
import com.fortind.zenith.net.HttpStatusException
import com.fortind.zenith.net.MissingPermissionException
import com.fortind.zenith.notifications.FakeTokenStore
import com.fortind.zenith.notifications.awaitTrue
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.viewModelScope
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {
    private val models = ArrayList<AccountSettingsViewModel>()

    private class FakeUser(var settings: AccountSettings) : UserApi {
        override suspend fun fetchCurrentUser(token: String) = CurrentUser("me", "me", null, null, null, settings = settings)
    }

    private class FakeInstance : InstanceInfo {
        override val emojis = MutableStateFlow<EmojiIndex?>(null)
        override val meta = MutableStateFlow<InstanceMeta?>(InstanceMeta("x", ltlAvailable = true, gtlAvailable = true, maxCwLength = 5))

        override fun ensureFresh() = Unit

        override fun emojiUrl(name: String) = ""
    }

    private class FakeAccount(private val user: FakeUser) : AccountApi {
        val sent = ArrayList<Pair<String, Any>>()
        var failure: IOException? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun updateProfile(token: String, edit: ProfileEdit) = error("unused")

        override suspend fun updateSetting(token: String, key: String, value: Any): CurrentUser {
            sent += key to value
            gate?.await()
            failure?.let { throw it }
            user.settings = when (key) {
                "isLocked" -> user.settings.copy(isLocked = value as Boolean)
                "chatScope" -> user.settings.copy(chatScope = value as String)
                "makeNotesHiddenBefore" -> user.settings.copy(notesHiddenBefore = value as? Long)
                "mutedWords" -> user.settings.copy(mutedWords = MuteWordSet.fromJson(value as JSONArray))
                "defaultCW" -> user.settings.copy(defaultCw = value as? String)
                "notificationRecieveConfig" -> user.settings.copy(notificationConfig = value.toString())
                else -> user.settings
            }
            return CurrentUser("me", "me", null, null, null, settings = user.settings)
        }
    }

    private class Fixture(val model: AccountSettingsViewModel, val api: FakeAccount, val session: UserSessionService)

    private fun fixture(settings: AccountSettings = AccountSettings()): Fixture {
        val user = FakeUser(settings)
        val api = FakeAccount(user)
        val session = UserSessionService(FakeTokenStore("token"), user)
        runBlocking { session.restore() }
        val model = AccountSettingsViewModel(session, api, FakeInstance())
        models += model
        return Fixture(model, api, session)
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        runBlocking { models.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private suspend fun AccountSettingsViewModel.awaitIdle() = awaitTrue { uiState.value.pending.isEmpty() }

    @Test
    fun aSwitchSendsOneKeyAndShowsTheServersAnswer() = runTest {
        val f = fixture()

        f.model.setFlag(AccountFlag.Locked, true)
        f.model.awaitIdle()

        assertEquals(listOf<Pair<String, Any>>("isLocked" to true), f.api.sent)
        assertTrue(f.model.uiState.value.flag(AccountFlag.Locked))
        assertTrue(f.model.uiState.value.settings!!.isLocked)
    }

    @Test
    fun aSwitchShowsItsNewValueWhileSavingAndIgnoresASecondTap() = runTest {
        val f = fixture()
        val gate = CompletableDeferred<Unit>()
        f.api.gate = gate

        f.model.setFlag(AccountFlag.Locked, true)
        awaitTrue { f.api.sent.size == 1 }
        f.model.setFlag(AccountFlag.Locked, false)

        assertTrue(f.model.uiState.value.flag(AccountFlag.Locked))
        assertTrue(f.model.uiState.value.busy("isLocked"))
        assertEquals(1, f.api.sent.size)
        gate.complete(Unit)
        f.model.awaitIdle()
    }

    @Test
    fun aFailedSwitchFlipsBackAndSaysSo() = runTest {
        val f = fixture()
        f.api.failure = IOException("offline")

        f.model.setFlag(AccountFlag.Locked, true)
        val event = f.model.events.first()
        f.model.awaitIdle()

        assertEquals(AccountSettingsEvent.Failed(SettingSaveFailure.Offline), event)
        assertFalse(f.model.uiState.value.flag(AccountFlag.Locked))
    }

    @Test
    fun aMissingScopeAsksToReauthorize() = runTest {
        val f = fixture()
        f.api.failure = MissingPermissionException()

        f.model.setChoice(AccountChoice.ChatScope, "none")
        f.model.awaitIdle()

        assertTrue(f.model.uiState.value.needsPermission)
    }

    @Test
    fun turningOffACutoffSendsNull() = runTest {
        val f = fixture(AccountSettings(notesHiddenBefore = -3600))

        f.model.setCutoff(OlderNotesCutoff.Hidden, null)
        f.model.awaitIdle()

        assertEquals(JSONObject.NULL, f.api.sent.single().second)
        assertEquals(null, f.model.uiState.value.cutoff(OlderNotesCutoff.Hidden))
    }

    @Test
    fun aNotificationRuleSendsTheWholeConfigAndAllowsOneChangeAtATime() = runTest {
        val f = fixture(AccountSettings(notificationConfig = """{"reply":{"type":"never"}}"""))
        val gate = CompletableDeferred<Unit>()
        f.api.gate = gate

        f.model.setNotificationRule(NotificationFilterType.Mention, NotificationRule.Following)
        awaitTrue { f.api.sent.size == 1 }
        assertEquals("following", f.model.uiState.value.notificationRule(NotificationFilterType.Mention))
        f.model.setNotificationRule(NotificationFilterType.Quote, NotificationRule.Never)

        assertEquals(1, f.api.sent.size)
        val config = f.api.sent.single().second as JSONObject
        assertEquals("never", config.getJSONObject("reply").getString("type"))
        gate.complete(Unit)
        f.model.awaitIdle()
    }

    @Test
    fun anEditorIsCleanUntilChangedAndSavesTheCanonicalText() = runTest {
        val stored = (MuteWordSet.parseEditor("cat") as MuteWordsParse.Valid).set
        val f = fixture(AccountSettings(mutedWords = stored))
        val editor = f.model.editor(SettingsEditor.MutedWords)

        assertEquals("cat", editor.text.text.toString())
        assertFalse(editor.dirty)

        editor.text.setTextAndPlaceCursorAtEnd(" cat \n\n")
        assertFalse(editor.dirty)

        editor.text.setTextAndPlaceCursorAtEnd("cat\nred   fox")
        assertTrue(editor.dirty)

        f.model.save(SettingsEditor.MutedWords)
        assertEquals(AccountSettingsEvent.EditorSaved, f.model.events.first())

        assertEquals("""[["cat"],["red","fox"]]""", f.api.sent.single().second.toString())
        assertEquals("cat\nred fox", editor.text.text.toString())
        assertFalse(editor.dirty)
    }

    @Test
    fun anInvalidLineStaysLocalUntilTheTextChanges() = runTest {
        val f = fixture()
        val editor = f.model.editor(SettingsEditor.MutedWords)

        editor.text.setTextAndPlaceCursorAtEnd("/(/")
        f.model.save(SettingsEditor.MutedWords)

        assertTrue(f.api.sent.isEmpty())
        assertEquals(SettingsEditorCheck.BadPattern("/(/"), editor.shownProblem)

        editor.text.setTextAndPlaceCursorAtEnd("/(/ ")
        assertEquals(null, editor.shownProblem)
    }

    @Test
    fun theDefaultWarningUsesTheInstancesLimit() = runTest {
        val f = fixture()
        val editor = f.model.editor(SettingsEditor.DefaultCw)

        editor.text.setTextAndPlaceCursorAtEnd("too long")
        f.model.save(SettingsEditor.DefaultCw)

        assertTrue(f.api.sent.isEmpty())
        assertEquals(SettingsEditorCheck.TooLong(5), editor.shownProblem)
    }

    @Test
    fun aServerRejectionKeepsTheTextAndNamesTheProblem() = runTest {
        val f = fixture()
        val editor = f.model.editor(SettingsEditor.MutedWords)
        f.api.failure = HttpStatusException(400, ApiErrorCodes.TOO_MANY_MUTED_WORDS)

        editor.text.setTextAndPlaceCursorAtEnd("cat")
        f.model.save(SettingsEditor.MutedWords)
        val event = f.model.events.first()

        assertEquals(AccountSettingsEvent.Failed(SettingSaveFailure.TooManyWords), event)
        assertEquals("cat", editor.text.text.toString())
        assertTrue(editor.dirty)
    }

    @Test
    fun aChangeFromElsewhereUpdatesACleanEditorButNotOneBeingTyped() = runTest {
        val f = fixture()
        val clean = f.model.editor(SettingsEditor.MutedWords)
        val typing = f.model.editor(SettingsEditor.DefaultCw)
        typing.text.setTextAndPlaceCursorAtEnd("mine")
        val words = (MuteWordSet.parseEditor("dog") as MuteWordsParse.Valid).set

        f.session.applyUpdatedUser(CurrentUser("me", "me", null, null, null, settings = AccountSettings(mutedWords = words, defaultCw = "web")))

        awaitTrue { clean.text.text.toString() == "dog" }
        assertEquals("mine", typing.text.text.toString())
        typing.revert()
        assertEquals("web", typing.text.text.toString())
    }
}

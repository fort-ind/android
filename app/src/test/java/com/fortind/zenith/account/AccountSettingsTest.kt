package com.fortind.zenith.account

import com.fortind.zenith.auth.parseCurrentUser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSettingsTest {

    @Test
    fun parsesTheFieldsTheServerSends() {
        val settings = parseAccountSettings(
            JSONObject(
                """{"mutedWords":[["cat"]],"hardMutedWords":["/dog/i"],"mutedInstances":["example.social"],
                "isLocked":true,"autoAcceptFollowed":false,"carefulBot":true,"followedMessage":"hi",
                "followingVisibility":"followers","followersVisibility":"private","publicReactions":true,
                "hideOnlineStatus":true,"isExplorable":false,"noCrawle":true,"preventAiLearning":true,"noindex":true,
                "enableRss":true,"requireSigninToViewContents":true,"makeNotesFollowersOnlyBefore":-3600,
                "makeNotesHiddenBefore":1700000000,"chatScope":"mutual","defaultCW":"cw","defaultCWPriority":"defaultParent",
                "defaultSensitive":true,"alwaysMarkNsfw":true,"autoSensitive":true,"lang":"de",
                "notificationRecieveConfig":{"mention":{"type":"following"}}}""",
            ),
        )

        assertEquals("cat", settings.mutedWords.toEditorText())
        assertEquals("/dog/i", settings.hardMutedWords.toEditorText())
        assertEquals(listOf("example.social"), settings.mutedInstances)
        AccountFlag.entries.filterNot { it == AccountFlag.AutoAcceptFollowed || it == AccountFlag.Explorable }.forEach { flag ->
            assertTrue(flag.key, flag.read(settings))
        }
        assertFalse(settings.autoAcceptFollowed)
        assertFalse(settings.isExplorable)
        assertEquals(-3600L, settings.notesFollowersOnlyBefore)
        assertEquals(1_700_000_000L, settings.notesHiddenBefore)
        assertEquals("following", settings.notificationRule(NotificationFilterType.Mention))
    }

    @Test
    fun omittedFieldsGetTheServersDefaults() {
        val settings = parseAccountSettings(JSONObject("""{"mutedInstances":null}"""))

        assertTrue(settings.autoAcceptFollowed)
        assertTrue(settings.isExplorable)
        assertFalse(settings.requireSignin)
        assertNull(settings.notesFollowersOnlyBefore)
        assertTrue(settings.mutedInstances.isEmpty())
        assertEquals("all", settings.notificationRule(NotificationFilterType.Reaction))
    }

    @Test
    fun theCurrentUserCarriesItsSettings() {
        val user = parseCurrentUser(JSONObject("""{"id":"1","username":"me","mutedWords":[["cat"]]}"""))

        assertEquals("cat", user.settings.mutedWords.toEditorText())
    }

    @Test
    fun changingOneNotificationRuleKeepsTheOthersIncludingLists() {
        val settings = AccountSettings(
            notificationConfig = """{"mention":{"type":"list","userListId":"abc"},"reply":{"type":"never"}}""",
        )

        val config = settings.notificationConfigWith(NotificationFilterType.Reply, NotificationRule.Following)

        assertEquals("following", config.getJSONObject("reply").getString("type"))
        assertEquals("abc", config.getJSONObject("mention").getString("userListId"))

        val fromList = settings.notificationConfigWith(NotificationFilterType.Mention, NotificationRule.All)
        assertFalse(fromList.getJSONObject("mention").has("userListId"))
    }

    @Test
    fun aDefaultWarningFillsAnEmptyOne() {
        assertEquals("cw", composerContentWarning(null, "cw", CW_PRIORITY_PARENT))
        assertEquals("cw", composerContentWarning("", "cw", CW_PRIORITY_DEFAULT_PARENT))
        assertNull(composerContentWarning(null, null, CW_PRIORITY_PARENT))
        assertEquals("parent", composerContentWarning("parent", "", CW_PRIORITY_DEFAULT))
    }

    @Test
    fun aReplysWarningFollowsThePriority() {
        assertEquals("parent", composerContentWarning("parent", "mine", CW_PRIORITY_PARENT))
        assertEquals("mine", composerContentWarning("parent", "mine", CW_PRIORITY_DEFAULT))
        assertEquals("parent, mine", composerContentWarning("parent", "mine", CW_PRIORITY_PARENT_DEFAULT))
        assertEquals("mine, parent", composerContentWarning("parent", "mine", CW_PRIORITY_DEFAULT_PARENT))
    }

    @Test
    fun aWarningAlreadyPresentAsAWholeWordIsNotRepeated() {
        assertEquals("spoilers, cats", composerContentWarning("spoilers, cats", "cats", CW_PRIORITY_PARENT_DEFAULT))
        assertEquals("catsup, cats", composerContentWarning("catsup", "cats", CW_PRIORITY_PARENT_DEFAULT))
    }

    @Test
    fun clearedTextIsSentAsJsonNull() {
        val check = SettingsEditor.DefaultCw.check("   ", AccountSettings(), maxCwLength = 10)

        assertEquals(JSONObject.NULL, (check as SettingsEditorCheck.Valid).payload)
        assertEquals("", check.canonical)
    }

    @Test
    fun theFollowMessageCountsCodePointsAndTheWarningCountsUnits() {
        val emoji = "😀"
        assertTrue(SettingsEditor.FollowedMessage.check(emoji.repeat(256), AccountSettings(), 10) is SettingsEditorCheck.Valid)
        assertEquals(SettingsEditorCheck.TooLong(257 - 1), SettingsEditor.FollowedMessage.check("a".repeat(257), AccountSettings(), 10))
        assertEquals(SettingsEditorCheck.TooLong(1), SettingsEditor.DefaultCw.check(emoji, AccountSettings(), maxCwLength = 1))
    }

    @Test
    fun wordEditorsSendTheServersShape() {
        val check = SettingsEditor.MutedWords.check("red fox\n/a/i", AccountSettings(), 10) as SettingsEditorCheck.Valid

        assertEquals("""[["red","fox"],"/a/i"]""", (check.payload as JSONArray).toString().replace("\\/", "/"))
        assertEquals(SettingsEditorCheck.BadPattern("/(/"), SettingsEditor.HardMutedWords.check("/(/", AccountSettings(), 10))
        assertEquals(SettingsEditorCheck.BadHost("a b"), SettingsEditor.MutedInstances.check("a b", AccountSettings(), 10))
    }

    @Test
    fun whitespaceIsNotAnEdit() {
        assertEquals(SettingsEditor.MutedWords.canonical("red fox"), SettingsEditor.MutedWords.canonical(" red   fox \n\n"))
        assertEquals(SettingsEditor.MutedInstances.canonical("a.example"), SettingsEditor.MutedInstances.canonical(" @A.example\n"))
    }
}

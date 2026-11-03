package com.fortind.zenith.account

import com.fortind.zenith.net.optStringOrNull
import com.fortind.zenith.net.strings
import org.json.JSONObject

data class AccountSettings(
    val mutedWords: MuteWordSet = MuteWordSet.Empty,
    val hardMutedWords: MuteWordSet = MuteWordSet.Empty,
    val mutedInstances: List<String> = emptyList(),
    val isLocked: Boolean = false,
    val autoAcceptFollowed: Boolean = true,
    val carefulBot: Boolean = false,
    val followedMessage: String? = null,
    val followingVisibility: String = VISIBILITY_PUBLIC,
    val followersVisibility: String = VISIBILITY_PUBLIC,
    val publicReactions: Boolean = false,
    val hideOnlineStatus: Boolean = false,
    val isExplorable: Boolean = true,
    val noCrawle: Boolean = false,
    val preventAiLearning: Boolean = false,
    val noIndex: Boolean = false,
    val enableRss: Boolean = false,
    val requireSignin: Boolean = false,
    val notesFollowersOnlyBefore: Long? = null,
    val notesHiddenBefore: Long? = null,
    val chatScope: String? = null,
    val defaultCw: String? = null,
    val defaultCwPriority: String = CW_PRIORITY_PARENT,
    val defaultSensitive: Boolean = false,
    val alwaysMarkNsfw: Boolean = false,
    val autoSensitive: Boolean = false,
    val lang: String? = null,
    val notificationConfig: String? = null,
) {
    val wordMutes: WordMutes get() = WordMutes(soft = mutedWords, hard = hardMutedWords)

    fun notificationRule(type: NotificationFilterType): String =
        config()?.optJSONObject(type.apiName)?.optStringOrNull("type") ?: NotificationRule.All.apiName

    fun notificationConfigWith(type: NotificationFilterType, rule: NotificationRule): JSONObject =
        (config() ?: JSONObject()).put(type.apiName, JSONObject().put("type", rule.apiName))

    private fun config(): JSONObject? = notificationConfig?.let(::JSONObject)
}

const val VISIBILITY_PUBLIC = "public"
const val CW_PRIORITY_DEFAULT = "default"
const val CW_PRIORITY_PARENT = "parent"
const val CW_PRIORITY_DEFAULT_PARENT = "defaultParent"
const val CW_PRIORITY_PARENT_DEFAULT = "parentDefault"
const val FOLLOWED_MESSAGE_MAX_LENGTH = 256

fun parseAccountSettings(json: JSONObject): AccountSettings = AccountSettings(
    mutedWords = MuteWordSet.fromJson(json.optJSONArray("mutedWords")),
    hardMutedWords = MuteWordSet.fromJson(json.optJSONArray("hardMutedWords")),
    mutedInstances = json.optJSONArray("mutedInstances")?.strings().orEmpty(),
    isLocked = json.optBoolean("isLocked"),
    autoAcceptFollowed = json.optBoolean("autoAcceptFollowed", true),
    carefulBot = json.optBoolean("carefulBot"),
    followedMessage = json.optStringOrNull("followedMessage"),
    followingVisibility = json.optStringOrNull("followingVisibility") ?: VISIBILITY_PUBLIC,
    followersVisibility = json.optStringOrNull("followersVisibility") ?: VISIBILITY_PUBLIC,
    publicReactions = json.optBoolean("publicReactions"),
    hideOnlineStatus = json.optBoolean("hideOnlineStatus"),
    isExplorable = json.optBoolean("isExplorable", true),
    noCrawle = json.optBoolean("noCrawle"),
    preventAiLearning = json.optBoolean("preventAiLearning"),
    noIndex = json.optBoolean("noindex"),
    enableRss = json.optBoolean("enableRss"),
    requireSignin = json.optBoolean("requireSigninToViewContents"),
    notesFollowersOnlyBefore = json.optLongOrNull("makeNotesFollowersOnlyBefore"),
    notesHiddenBefore = json.optLongOrNull("makeNotesHiddenBefore"),
    chatScope = json.optStringOrNull("chatScope"),
    defaultCw = json.optStringOrNull("defaultCW"),
    defaultCwPriority = json.optStringOrNull("defaultCWPriority") ?: CW_PRIORITY_PARENT,
    defaultSensitive = json.optBoolean("defaultSensitive"),
    alwaysMarkNsfw = json.optBoolean("alwaysMarkNsfw"),
    autoSensitive = json.optBoolean("autoSensitive"),
    lang = json.optStringOrNull("lang"),
    notificationConfig = json.optJSONObject("notificationRecieveConfig")?.toString(),
)

private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null

enum class AccountFlag(val key: String, val read: (AccountSettings) -> Boolean) {
    Locked("isLocked", AccountSettings::isLocked),
    AutoAcceptFollowed("autoAcceptFollowed", AccountSettings::autoAcceptFollowed),
    CarefulBot("carefulBot", AccountSettings::carefulBot),
    PublicReactions("publicReactions", AccountSettings::publicReactions),
    HideOnlineStatus("hideOnlineStatus", AccountSettings::hideOnlineStatus),
    Explorable("isExplorable", AccountSettings::isExplorable),
    NoCrawle("noCrawle", AccountSettings::noCrawle),
    PreventAiLearning("preventAiLearning", AccountSettings::preventAiLearning),
    NoIndex("noindex", AccountSettings::noIndex),
    EnableRss("enableRss", AccountSettings::enableRss),
    RequireSignin("requireSigninToViewContents", AccountSettings::requireSignin),
    DefaultSensitive("defaultSensitive", AccountSettings::defaultSensitive),
    AlwaysMarkNsfw("alwaysMarkNsfw", AccountSettings::alwaysMarkNsfw),
    AutoSensitive("autoSensitive", AccountSettings::autoSensitive),
}

enum class AccountChoice(val key: String, val read: (AccountSettings) -> String?, val options: List<String>) {
    FollowingVisibility("followingVisibility", AccountSettings::followingVisibility, listOf(VISIBILITY_PUBLIC, "followers", "private")),
    FollowersVisibility("followersVisibility", AccountSettings::followersVisibility, listOf(VISIBILITY_PUBLIC, "followers", "private")),
    ChatScope("chatScope", AccountSettings::chatScope, listOf("everyone", "followers", "following", "mutual", "none")),
    DefaultCwPriority(
        "defaultCWPriority",
        AccountSettings::defaultCwPriority,
        listOf(CW_PRIORITY_PARENT, CW_PRIORITY_DEFAULT, CW_PRIORITY_PARENT_DEFAULT, CW_PRIORITY_DEFAULT_PARENT),
    ),
}

enum class OlderNotesCutoff(val key: String, val read: (AccountSettings) -> Long?) {
    FollowersOnly("makeNotesFollowersOnlyBefore", AccountSettings::notesFollowersOnlyBefore),
    Hidden("makeNotesHiddenBefore", AccountSettings::notesHiddenBefore),
}

enum class OlderNotesStep(val seconds: Long) {
    OneHour(-3_600),
    OneDay(-86_400),
    ThreeDays(-259_200),
    OneWeek(-604_800),
    OneMonth(-2_592_000),
    ThreeMonths(-7_776_000),
    OneYear(-31_104_000),
}

enum class NotificationFilterType(val apiName: String) {
    Follow("follow"),
    Mention("mention"),
    Reply("reply"),
    Renote("renote"),
    Quote("quote"),
    Reaction("reaction"),
    PollEnded("pollEnded"),
    FollowRequest("receiveFollowRequest"),
}

enum class NotificationRule(val apiName: String) {
    All("all"),
    Following("following"),
    Follower("follower"),
    MutualFollow("mutualFollow"),
    FollowingOrFollower("followingOrFollower"),
    Never("never"),
}

const val NOTIFICATION_RULE_LIST = "list"

val PROFILE_LANGUAGE_CODES: List<String> = listOf(
    "af", "ak", "ar", "az", "bg", "bn", "br", "ca", "cs", "cy", "da", "de", "dsb", "el", "en", "eo", "es", "et", "eu",
    "fa", "ff", "fi", "fil", "fo", "fr", "ga", "gd", "gl", "gv", "he", "hi", "hr", "hsb", "ht", "hu", "hy", "id", "is",
    "it", "ja", "kab", "kl", "km", "kn", "ko", "kw", "la", "lb", "lt", "lv", "mai", "mk", "ml", "mr", "ms", "mt", "my",
    "nb", "ne", "nl", "no", "oc", "pa", "pl", "pt", "ro", "ru", "sh", "sk", "sl", "sq", "sr", "su", "sv", "sw", "ta",
    "te", "tg", "th", "tr", "uk", "ur", "uz", "vi", "yi", "zh",
)

fun composerContentWarning(parentCw: String?, defaultCw: String?, priority: String): String? {
    val default = defaultCw?.takeIf(String::isNotEmpty) ?: return parentCw
    if (parentCw.isNullOrEmpty() || priority == CW_PRIORITY_DEFAULT) return default
    return when (priority) {
        CW_PRIORITY_DEFAULT_PARENT -> appendContentWarning(parentCw, default, defaultFirst = true)
        CW_PRIORITY_PARENT_DEFAULT -> appendContentWarning(parentCw, default, defaultFirst = false)
        else -> parentCw
    }
}

private fun appendContentWarning(parent: String, default: String, defaultFirst: Boolean): String = when {
    includesWholeWord(parent, default) -> parent
    defaultFirst -> "$default, $parent"
    else -> "$parent, $default"
}

private fun includesWholeWord(input: String, target: String): Boolean {
    val parts = input.split(target)
    return (0 until parts.lastIndex).any { index ->
        val before = parts[index].lastOrNull()
        val after = parts[index + 1].firstOrNull()
        !before.isAsciiWordCharacter() && !after.isAsciiWordCharacter()
    }
}

private fun Char?.isAsciiWordCharacter(): Boolean =
    this != null && (this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_')

sealed interface SettingsEditorCheck {
    data class Valid(val payload: Any, val canonical: String) : SettingsEditorCheck

    data class BadPattern(val line: String) : SettingsEditorCheck

    data class BadHost(val line: String) : SettingsEditorCheck

    data class TooLong(val limit: Int) : SettingsEditorCheck
}

enum class SettingsEditor(val key: String) {
    FollowedMessage("followedMessage"),
    DefaultCw("defaultCW"),
    MutedWords("mutedWords"),
    HardMutedWords("hardMutedWords"),
    MutedInstances("mutedInstances");

    fun storedText(settings: AccountSettings): String = when (this) {
        FollowedMessage -> settings.followedMessage.orEmpty()
        DefaultCw -> settings.defaultCw.orEmpty()
        MutedWords -> settings.mutedWords.toEditorText()
        HardMutedWords -> settings.hardMutedWords.toEditorText()
        MutedInstances -> settings.mutedInstances.joinToString("\n")
    }

    fun canonical(text: String): String = when (this) {
        FollowedMessage, DefaultCw -> normalizeProfileText(text)
        MutedWords, HardMutedWords -> MuteWordSet.canonicalEditorText(text)
        MutedInstances -> canonicalHostsText(text)
    }

    fun check(text: String, settings: AccountSettings, maxCwLength: Int): SettingsEditorCheck = when (this) {
        FollowedMessage -> checkText(text, FOLLOWED_MESSAGE_MAX_LENGTH) { it.codePointLength() }
        DefaultCw -> checkText(text, maxCwLength) { it.length }
        MutedWords -> checkWords(text, settings.mutedWords)
        HardMutedWords -> checkWords(text, settings.hardMutedWords)
        MutedInstances -> when (val parsed = parseMuteHosts(text)) {
            is MuteHostsParse.Invalid -> SettingsEditorCheck.BadHost(parsed.line)
            is MuteHostsParse.Valid -> SettingsEditorCheck.Valid(
                org.json.JSONArray().apply { parsed.hosts.forEach { put(it) } },
                parsed.hosts.joinToString("\n"),
            )
        }
    }

    private fun checkText(text: String, limit: Int, measure: (String) -> Int): SettingsEditorCheck {
        val clean = normalizeProfileText(text)
        if (measure(clean) > limit) return SettingsEditorCheck.TooLong(limit)
        return SettingsEditorCheck.Valid(clean.ifEmpty { JSONObject.NULL }, clean)
    }

    private fun checkWords(text: String, stored: MuteWordSet): SettingsEditorCheck =
        when (val parsed = MuteWordSet.parseEditor(text, stored)) {
            is MuteWordsParse.Invalid -> SettingsEditorCheck.BadPattern(parsed.line)
            is MuteWordsParse.Valid -> SettingsEditorCheck.Valid(parsed.set.toJson(), parsed.set.toEditorText())
        }
}

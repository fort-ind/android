package com.fortind.zenith.ui

import android.app.UiAutomation
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.Poll
import com.fortind.zenith.timeline.PollChoice
import com.fortind.zenith.timeline.ReactionAcceptance
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.ui.feed.HostedPopups
import com.fortind.zenith.ui.feed.HostedPopupsLayer
import com.fortind.zenith.ui.feed.LocalHostedPopups
import com.fortind.zenith.ui.feed.NoteCallbacks
import com.fortind.zenith.ui.feed.NoteCard
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.mfm.rememberMfmEnvironment
import com.fortind.zenith.ui.theme.Contrast
import com.fortind.zenith.ui.theme.ZenithTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {

    @get:Rule
    val rule = createComposeRule()

    private val liked = mutableListOf<String>()
    private val holdMillis = InstrumentationRegistry.getArguments().getString("holdMillis")?.toLong() ?: 0L
    private var dark by mutableStateOf(false)
    private var contrast by mutableStateOf(Contrast.STANDARD)

    private val author = NoteUser("u1", "alice", null, null, emptyList(), null, null, emptyMap(), false, false, null)

    @After
    fun hold() {
        Thread.sleep(holdMillis)
    }

    private fun note(id: String, text: String, poll: Poll? = null, reactions: Map<String, Int> = emptyMap(), cw: String? = null) = Note(
        id = id,
        createdAtMillis = 0L,
        editedAtMillis = null,
        user = author,
        text = text,
        textMfm = MfmParser.parse(text),
        cw = cw,
        cwMfm = cw?.let { MfmParser.parse(it) },
        visibility = Visibility.Public,
        localOnly = false,
        renote = null,
        reply = null,
        files = emptyList(),
        poll = poll,
        emojis = emptyMap(),
        reactions = reactions,
        reactionEmojis = emptyMap(),
        renoteCount = 2,
        repliesCount = 1,
        myReaction = null,
        reactionAcceptance = ReactionAcceptance.All,
        remoteUrl = null,
        isHidden = false,
    )

    private val feed = listOf(
        note("n1", "Hello https://example.com and #tag with @bob", reactions = mapOf("👍" to 3, ":blobcat_happy@.:" to 1)),
        note(
            "n2",
            "Which one?",
            poll = Poll(listOf(PollChoice("Cats", 3, true), PollChoice("Dogs", 1, false)), multiple = false, expiresAtMillis = 0L),
        ),
        note("n3", "Votable", poll = Poll(listOf(PollChoice("Tea", 0, false), PollChoice("Coffee", 0, false)), multiple = true, expiresAtMillis = null)),
        note("n4", "The ending: \$[blur the butler did it]"),
        note("n5", "Spoilers below", cw = "Film talk"),
    )

    private fun callbacks() = NoteCallbacks(
        openThread = {},
        reply = {},
        quote = {},
        openProfile = {},
        toggleReaction = { _, _ -> },
        openReactionPicker = {},
        reactDefault = { liked += it.id },
        renote = {},
        unrenote = {},
        checkRenoted = {},
        vote = { _, _ -> },
        edit = {},
        delete = {},
        redraft = {},
        toggleBookmark = {},
        checkBookmarked = {},
        openMedia = { _, _ -> },
        openUrl = {},
        copyLink = {},
        copyText = {},
        share = {},
        muteAuthor = {},
        hideAuthorRenotes = {},
        blockAuthor = {},
        report = {},
    )

    private fun showFeed(selected: String? = null) {
        val playback = FeedPlayback(InstrumentationRegistry.getInstrumentation().targetContext)
        rule.setContent {
            ZenithTheme(darkTheme = dark, dynamicColor = false, contrast = contrast) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val popups = remember { HostedPopups() }
                    val environment = NoteEnvironment(
                        now = 0L,
                        myUserId = "me",
                        signedIn = true,
                        renoted = emptySet(),
                        renoteChecks = emptySet(),
                        bookmarked = emptySet(),
                        bookmarkChecks = emptySet(),
                        localEmojiNames = null,
                        emojiUrl = { "" },
                        mfm = rememberMfmEnvironment({ "" }, animate = false),
                        selectedNoteId = selected,
                        playback = playback,
                    )
                    val callbacks = remember { callbacks() }
                    CompositionLocalProvider(LocalHostedPopups provides popups) {
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                feed.forEach { NoteCard(it, environment, callbacks) }
                            }
                            HostedPopupsLayer(popups)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun postRow(id: String) = rule.onAllNodes(hasTestTag("note"))[feed.indexOfFirst { it.id == id }]

    private fun customAction(label: String) = SemanticsMatcher("has the $label action") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    fun theFeedPassesTheFrameworkChecksInLightAndDark() {
        rule.enableAccessibilityChecks()
        showFeed()
        for (theme in listOf(false, true)) {
            dark = theme
            rule.waitForIdle()
            rule.onRoot().tryPerformAccessibilityChecks()
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    fun aSelectedPostPassesTheChecksInEveryContrastLevel() {
        rule.enableAccessibilityChecks()
        showFeed(selected = "n1")
        for (theme in listOf(false, true)) {
            for (level in Contrast.entries) {
                dark = theme
                contrast = level
                rule.waitForIdle()
                rule.onRoot().tryPerformAccessibilityChecks()
            }
        }
    }

    @Test
    fun aPostOffersLikeAndMoreOptionsAsActions() {
        showFeed()

        val row = postRow("n1")
        row.assert(customAction("Like"))
        row.assert(customAction("More options"))

        val like = row.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Like" }
        rule.runOnIdle { like.action() }
        assertEquals(listOf("n1"), liked)

        val more = row.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "More options" }
        rule.runOnIdle { more.action() }
        rule.waitForIdle()
        rule.onAllNodes(isPopup()).assertCountEquals(1)
    }

    @Test
    fun aSelectedPostIsAnnouncedAsSelected() {
        showFeed(selected = "n1")

        postRow("n1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        postRow("n2").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun hiddenSpoilerTextIsNeverExposed() {
        showFeed()

        rule.onAllNodes(hasText("butler", substring = true), useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("Hidden text", substring = true, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theNodeAScreenReaderFocusesCarriesLikeAndMoreOptions() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        showFeed()
        val composeRoot = rule.onRoot().fetchSemanticsNode().root
        rule.runOnIdle { composeRoot?.forceAccessibilityForTesting(true) }

        val root = waitForRoot(automation)
        val text = root.find { it.text?.startsWith("Hello example.com") == true }!!
        val row = generateSequence(text) { it.parent }.first { it.isClickable && it.isScreenReaderFocusable }
        val rowLabels = row.actionList.mapNotNull { it.label?.toString() }
        assertTrue(rowLabels.toString(), "Like" in rowLabels && "More options" in rowLabels)

        val bar = root.find { it.contentDescription?.startsWith("Reply, 1. Renote, 2") == true }!!
        assertTrue(bar.actionList.none { it.label != null })
        assertFalse(bar.isScreenReaderFocusable)
    }

    private fun waitForRoot(automation: UiAutomation): AccessibilityNodeInfo {
        repeat(50) {
            rule.waitForIdle()
            val root = automation.rootInActiveWindow
            if (root?.find { it.text?.startsWith("Hello example.com") == true } != null) return root
            Thread.sleep(100)
        }
        error("The feed never reached the accessibility tree")
    }

    private fun AccessibilityNodeInfo.find(match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (match(this)) return this
        for (index in 0 until childCount) {
            getChild(index)?.find(match)?.let { return it }
        }
        return null
    }
}

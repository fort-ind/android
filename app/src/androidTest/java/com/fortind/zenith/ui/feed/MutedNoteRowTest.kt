package com.fortind.zenith.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.ReactionAcceptance
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.mfm.rememberMfmEnvironment
import com.fortind.zenith.ui.theme.ZenithTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MutedNoteRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val author = NoteUser("u1", "alex", null, null, emptyList(), null, null, emptyMap(), false, false, null)

    private val note = Note(
        id = "n1",
        createdAtMillis = 0L,
        editedAtMillis = null,
        user = author,
        text = "the ending",
        textMfm = MfmParser.parse("the ending"),
        cw = null,
        cwMfm = null,
        visibility = Visibility.Public,
        localOnly = false,
        renote = null,
        reply = null,
        files = emptyList(),
        poll = null,
        emojis = emptyMap(),
        reactions = emptyMap(),
        reactionEmojis = emptyMap(),
        renoteCount = 0,
        repliesCount = 0,
        myReaction = null,
        reactionAcceptance = ReactionAcceptance.All,
        remoteUrl = null,
        isHidden = false,
    )

    private fun callbacks() = NoteCallbacks(
        openThread = {},
        reply = {},
        quote = {},
        openProfile = {},
        toggleReaction = { _, _ -> },
        openReactionPicker = {},
        reactDefault = {},
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

    private fun show(mutedWords: List<String>?) {
        val playback = FeedPlayback(InstrumentationRegistry.getInstrumentation().targetContext)
        rule.setContent {
            ZenithTheme(dynamicColor = false) {
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
                    selectedNoteId = null,
                    playback = playback,
                )
                NoteCard(note = note, environment = environment, callbacks = callbacks(), mutedWords = mutedWords)
            }
        }
    }

    @Test
    fun aMutedNoteShowsOnlyTheReasonUntilAskedFor() {
        show(listOf("spoilers", "ending"))

        rule.onNodeWithText("Muted: spoilers, ending").assertIsDisplayed()
        rule.onNodeWithText("the ending").assertDoesNotExist()
        rule.onNodeWithText("alex").assertDoesNotExist()

        rule.onNodeWithText("Show content").performClick()

        rule.onNodeWithTag("note:muted").assertDoesNotExist()
        rule.onNodeWithText("the ending").assertIsDisplayed()
    }

    @Test
    fun anUnmutedNoteIsUnchanged() {
        show(null)

        rule.onNodeWithTag("note:muted").assertDoesNotExist()
        rule.onNodeWithText("the ending").assertIsDisplayed()
    }
}

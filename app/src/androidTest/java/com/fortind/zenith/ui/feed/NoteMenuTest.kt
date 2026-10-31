package com.fortind.zenith.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.ReactionAcceptance
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.mfm.rememberMfmEnvironment
import com.fortind.zenith.ui.theme.ZenithTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun user(id: String) = NoteUser(id, id, null, null, emptyList(), null, null, emptyMap(), false, false, null)

    private fun note(authorId: String, visibility: Visibility = Visibility.Public) = Note(
        id = "n1",
        createdAtMillis = 0L,
        editedAtMillis = null,
        user = user(authorId),
        text = "hello",
        textMfm = emptyList(),
        cw = null,
        cwMfm = null,
        visibility = visibility,
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
        edit = { calls += "edit" },
        delete = { calls += "delete" },
        redraft = { calls += "redraft" },
        toggleBookmark = { calls += "bookmark" },
        checkBookmarked = { calls += "check" },
        openMedia = { _, _ -> },
        openUrl = {},
        copyLink = {},
        copyText = {},
        share = {},
    )

    private fun show(
        note: Note,
        myUserId: String? = "me",
        signedIn: Boolean = true,
        bookmarked: Set<String> = emptySet(),
        checking: Set<String> = emptySet(),
    ) {
        rule.setContent {
            ZenithTheme(dynamicColor = false) {
                val environment = NoteEnvironment(
                    now = 0L,
                    myUserId = myUserId,
                    signedIn = signedIn,
                    renoted = emptySet(),
                    renoteChecks = emptySet(),
                    bookmarked = bookmarked,
                    bookmarkChecks = checking,
                    localEmojiNames = null,
                    emojiUrl = { "" },
                    mfm = rememberMfmEnvironment({ "" }, animate = false),
                    selectedNoteId = null,
                    playback = FeedPlayback(LocalContext.current),
                )
                Column { NoteMenuItems(note, environment, callbacks()) {} }
            }
        }
    }

    @Test
    fun myOwnPostOffersEditDeleteAndRedraft() {
        show(note("me"))

        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithText("Delete & redraft").performClick()
        rule.onNodeWithText("Delete").performClick()

        assertEquals(listOf("check", "edit", "redraft", "delete"), calls)
    }

    @Test
    fun otherPeoplesPostsOfferNeitherEditNorDelete() {
        show(note("someone"))

        rule.onNodeWithText("Edit").assertDoesNotExist()
        rule.onNodeWithText("Delete").assertDoesNotExist()
        rule.onNodeWithText("Delete & redraft").assertDoesNotExist()
        rule.onNodeWithText("Bookmark").assertExists()
    }

    @Test
    fun directMessagesCannotBeRedraftedBecauseTheirRecipientsWouldBeLost() {
        show(note("me", Visibility.Specified))

        rule.onNodeWithText("Delete & redraft").assertDoesNotExist()
        rule.onNodeWithText("Edit").assertExists()
        rule.onNodeWithText("Delete").assertExists()
    }

    @Test
    fun guestsSeeNeitherBookmarkNorOwnerActions() {
        show(note("me"), myUserId = null, signedIn = false)

        rule.onNodeWithText("Bookmark").assertDoesNotExist()
        rule.onNodeWithText("Edit").assertDoesNotExist()
        rule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun aBookmarkedPostOffersToRemoveTheBookmark() {
        show(note("someone"), bookmarked = setOf("n1"))

        rule.onNodeWithText("Remove bookmark").performClick()

        assertEquals(listOf("check", "bookmark"), calls)
    }

    @Test
    fun theBookmarkItemWaitsWhileItsStateIsBeingChecked() {
        show(note("someone"), checking = setOf("n1"))

        rule.onNodeWithText("Bookmark").assertIsNotEnabled()
    }
}

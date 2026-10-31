package com.fortind.zenith.ui.feed

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fortind.zenith.timeline.Poll
import com.fortind.zenith.timeline.PollChoice
import com.fortind.zenith.ui.theme.ZenithTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PollViewTest {

    @get:Rule
    val rule = createComposeRule()

    private val cast = mutableListOf<List<Int>>()

    private fun poll(multiple: Boolean = false, voted: Set<Int> = emptySet(), expiresAt: Long? = null) = Poll(
        choices = listOf("Cats" to 3, "Dogs" to 1, "Birds" to 0).mapIndexed { index, (text, votes) ->
            PollChoice(text, votes, index in voted)
        },
        multiple = multiple,
        expiresAtMillis = expiresAt,
    )

    private fun show(poll: Poll, signedIn: Boolean = true, now: Long = 1_000L) {
        rule.setContent {
            ZenithTheme(dynamicColor = false) {
                PollView(poll = poll, noteId = "n1", now = now, signedIn = signedIn, onVote = { cast += it })
            }
        }
    }

    @Test
    fun tappingAnOptionInASingleChoicePollVotesAtOnce() {
        show(poll())

        rule.onNodeWithText("Dogs").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(listOf(1)), cast)
    }

    @Test
    fun resultsStayHiddenUntilIHaveVoted() {
        show(poll())

        rule.onNodeWithText("75%").assertDoesNotExist()
        rule.onNodeWithText("Vote").assertDoesNotExist()
    }

    @Test
    fun aMultipleChoiceVoteNeedsAPickAndTheVoteButton() {
        show(poll(multiple = true))

        rule.onNodeWithText("Vote").assertIsNotEnabled()
        rule.onNodeWithText("Cats").performClick()
        rule.onNodeWithText("Birds").performClick()
        assertEquals(emptyList<List<Int>>(), cast)

        rule.onNodeWithText("Vote").assertIsEnabled().performClick()

        assertEquals(listOf(listOf(0, 2)), cast)
    }

    @Test
    fun aPickCanBeTakenBackBeforeVoting() {
        show(poll(multiple = true))

        rule.onNodeWithText("Cats").performClick()
        rule.onNodeWithText("Cats").performClick()

        rule.onNodeWithText("Vote").assertIsNotEnabled()
    }

    @Test
    fun aPollIAlreadyVotedInShowsResults() {
        show(poll(voted = setOf(0)))

        rule.onNodeWithText("75%").assertExists()
        rule.onNodeWithText("25%").assertExists()
        rule.onNodeWithText("Vote").assertDoesNotExist()
    }

    @Test
    fun anEndedPollShowsResultsAndNoOptions() {
        show(poll(expiresAt = 500L))

        rule.onNodeWithText("75%").assertExists()
        rule.onNodeWithText("Poll ended", substring = true).assertExists()
        rule.onNodeWithText("Cats").performClick()
        assertEquals(emptyList<List<Int>>(), cast)
    }

    @Test
    fun guestsSeeResultsBecauseTheyCannotVote() {
        show(poll(), signedIn = false)

        rule.onNodeWithText("75%").assertExists()
        rule.onNodeWithText("Cats").performClick()
        assertEquals(emptyList<List<Int>>(), cast)
    }
}

package com.fortind.zenith.ui.discovery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.R
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.mfm.rememberMfmEnvironment
import com.fortind.zenith.ui.people.PeopleActions
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import com.fortind.zenith.ui.theme.ZenithTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PersonSearchRowTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val person = NoteUser("u1", "alex", null, null, emptyList(), null, null, emptyMap(), false, false, null)

    private var opened = 0
    private var copied = 0

    private val actions = PeopleActions(
        toggleFollow = {},
        moderate = { _, _ -> },
        report = {},
        addToList = {},
        openInBrowser = {},
        copyLink = { copied++ },
        share = {},
    )

    private fun show() {
        val playback = FeedPlayback(context)
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
                CompositionLocalProvider(LocalNoteEnvironment provides environment) {
                    PersonSearchRow(person, signedIn = true, actions = actions) { opened++ }
                }
            }
        }
    }

    private fun rightClickRow() {
        rule.onNodeWithTag("search:person").performMouseInput { rightClick(center) }
        rule.waitForIdle()
    }

    @Test
    fun leftClickOpensThePerson() {
        show()

        rule.onNodeWithTag("search:person").performClick()

        rule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun rightClickOffersTheLinkActionsWithoutOpening() {
        show()

        rightClickRow()

        rule.onNodeWithText(context.getString(R.string.person_menu_view)).assertExists()
        rule.onNodeWithText(context.getString(R.string.person_menu_open_browser)).assertExists()
        rule.onNodeWithText(context.getString(R.string.person_menu_share)).assertExists()
        rule.onNodeWithText(context.getString(R.string.person_menu_copy_link)).performClick()
        rule.runOnIdle {
            assertEquals(1, copied)
            assertEquals(0, opened)
        }
    }

    @Test
    fun viewProfileInTheMenuOpensThePerson() {
        show()

        rightClickRow()
        rule.onNodeWithText(context.getString(R.string.person_menu_view)).performClick()

        rule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun actionsThatNeedTheFullProfileAreNotOffered() {
        show()

        rightClickRow()

        rule.onNodeWithText(context.getString(R.string.person_menu_add_to_list)).assertDoesNotExist()
    }
}

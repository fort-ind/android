package com.fortind.zenith.ui.feed

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.ReactionAcceptance
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.mfm.rememberMfmEnvironment
import com.fortind.zenith.ui.people.PeopleActions
import com.fortind.zenith.ui.people.PeopleViewModel
import com.fortind.zenith.ui.theme.ZenithTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostPointerDeviceTest {

    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val windows = mutableListOf<String>()
    private val drags = mutableListOf<String>()
    private lateinit var cards: HoverCards

    private val user = NoteUser("u1", "alice", null, "Alice", emptyList(), null, null, emptyMap(), false, false, null)
    private val note = Note(
        id = "n1",
        createdAtMillis = 0L,
        editedAtMillis = null,
        user = user,
        text = "hello",
        textMfm = emptyList(),
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
        openThread = { opened += it.id },
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
        startDrag = { drags += it.id },
        openInWindow = { windows += it.id },
    )

    private fun showCard() {
        val playback = FeedPlayback(InstrumentationRegistry.getInstrumentation().targetContext)
        rule.setContent {
            ZenithTheme(dynamicColor = false) {
                val scope = rememberCoroutineScope()
                cards = remember { HoverCards(scope) }
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
                val callbacks = remember { callbacks() }
                CompositionLocalProvider(LocalHoverCards provides cards) {
                    Box(Modifier.width(480.dp)) { NoteCard(note, environment, callbacks) }
                }
            }
        }
    }

    @Test
    fun restingTheMouseOnTheAvatarShowsTheProfileCardAndLeavingHidesIt() {
        showCard()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("note:avatar").performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(HOVER_CARD_SHOW_MS + 100)
        assertEquals(HoverTarget.Profile(user), cards.shown?.target)

        rule.onNodeWithTag("note:avatar").performMouseInput { moveTo(center + Offset(2000f, 0f)) }
        rule.mainClock.advanceTimeBy(HOVER_CARD_HIDE_MS + 100)
        assertNull(cards.shown)
    }

    @Test
    fun aQuickPassOverTheAvatarShowsNothing() {
        showCard()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("note:avatar").performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(HOVER_CARD_SHOW_MS / 3)
        rule.onNodeWithTag("note:avatar").performMouseInput { moveTo(center + Offset(2000f, 0f)) }
        rule.mainClock.advanceTimeBy(HOVER_CARD_SHOW_MS * 2)
        assertNull(cards.shown)
    }

    @Test
    fun aMiddleClickOpensThePostInAWindow() {
        showCard()
        withMouseButtons {
            rule.onNodeWithText("hello").performMouseInput {
                press(MouseButton.Tertiary)
                release(MouseButton.Tertiary)
            }
        }
        rule.waitForIdle()
        assertEquals(listOf("n1"), windows)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun aMouseDragFromTheTimeStartsAPostDragAndAClickStillOpensThePost() {
        showCard()
        withMouseButtons { rule.onNodeWithText("hello").performMouseInput { click() } }
        rule.waitForIdle()
        assertEquals(listOf("n1"), opened)

        withMouseButtons {
            rule.onNodeWithTag("note:time", useUnmergedTree = true).performMouseInput {
                press()
                moveBy(Offset(60f, 0f))
                release()
            }
        }
        rule.waitForIdle()
        assertEquals(listOf("n1"), drags)
        assertEquals(listOf("n1"), opened)
    }

    @Test
    fun touchStillOpensFromTheTimeAndALongPressOnTheAvatarDrags() {
        showCard()
        rule.onNodeWithTag("note:time", useUnmergedTree = true).performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(listOf("n1"), opened)

        rule.onNodeWithTag("note:avatar").performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(listOf("n1"), drags)
    }

    @Test
    fun theProfileCardRendersForTheSignedInAccount() {
        var people: PeopleViewModel? = null
        var cardsReady: HoverCards? = null
        rule.setContent {
            ZenithTheme(dynamicColor = false) {
                val model: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory)
                val feed: FeedViewModel = viewModel(factory = FeedViewModel.Factory)
                val state by model.uiState.collectAsStateWithLifecycle()
                people = model
                val scope = rememberCoroutineScope()
                val hover = remember { HoverCards(scope) }
                cardsReady = hover
                val environment = NoteEnvironment(
                    now = 0L,
                    myUserId = state.myUserId,
                    signedIn = state.signedIn,
                    renoted = emptySet(),
                    renoteChecks = emptySet(),
                    bookmarked = emptySet(),
                    bookmarkChecks = emptySet(),
                    localEmojiNames = null,
                    emojiUrl = { "" },
                    mfm = rememberMfmEnvironment({ "" }, animate = false),
                    selectedNoteId = null,
                    playback = remember { FeedPlayback(InstrumentationRegistry.getInstrumentation().targetContext) },
                )
                Box(Modifier.fillMaxSize()) {
                    HoverCardLayer(
                        cards = hover,
                        people = model,
                        feed = feed,
                        environment = environment,
                        peopleActions = PeopleActions({}, { _, _ -> }, {}, {}, {}, {}, {}),
                        onOpenProfile = {},
                    )
                }
            }
        }
        val signedIn = runCatching { rule.waitUntil(10_000) { people?.uiState?.value?.myUserId != null } }.isSuccess
        assumeTrue("needs the debug app signed in", signedIn)
        val model = checkNotNull(people)
        val me = checkNotNull(model.uiState.value.myUserId)
        val self = NoteUser(me, "me", null, null, emptyList(), null, null, emptyMap(), false, false, null)
        rule.runOnIdle { cardsReady!!.anchorEntered(HoverTarget.Profile(self), IntRect(40, 40, 120, 120)) }
        rule.waitUntil(5_000) { cardsReady!!.shown != null }
        rule.waitUntil(15_000) { model.uiState.value.person(me).detail != null }
        Thread.sleep(2_000)
        val image = rule.onNode(isPopup()).captureToImage().asAndroidBitmap()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "hover-card").apply { mkdirs() }
        File(folder, "profile.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun withMouseButtons(block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            assumeTrue("this device refuses injected mouse button presses", e.message?.contains("Failed to inject mouse input") != true)
            throw e
        }
    }
}

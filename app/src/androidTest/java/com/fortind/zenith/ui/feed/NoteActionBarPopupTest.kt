package com.fortind.zenith.ui.feed

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.ReactionAcceptance
import com.fortind.zenith.timeline.Visibility
import com.fortind.zenith.ui.media.FeedPlayback
import com.fortind.zenith.ui.mfm.rememberMfmEnvironment
import com.fortind.zenith.ui.theme.ZenithTheme
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteActionBarPopupTest {

    @get:Rule
    val rule = createComposeRule()

    private var notes by mutableStateOf((0 until 40).map(::note))
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    private fun note(index: Int) = Note(
        id = "n$index",
        createdAtMillis = 0L,
        editedAtMillis = null,
        user = NoteUser("u$index", "u$index", null, null, emptyList(), null, null, emptyMap(), false, false, null),
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

    private fun showList() {
        val playback = FeedPlayback(InstrumentationRegistry.getInstrumentation().targetContext)
        rule.setContent {
            ZenithTheme(dynamicColor = false) {
                listState = rememberLazyListState()
                scope = rememberCoroutineScope()
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
                    selectedNoteId = null,
                    playback = playback,
                )
                val callbacks = remember { callbacks() }
                CompositionLocalProvider(LocalHostedPopups provides popups) {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(state = listState, modifier = Modifier.height(320.dp)) {
                            items(notes, key = { it.id }, contentType = { "note" }) { note ->
                                NoteActionBar(note, environment, callbacks)
                            }
                        }
                        HostedPopupsLayer(popups)
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun firstBar() = rule.onAllNodes(hasContentDescription("Renote", substring = true))[0]

    private fun trackPopup(): WeakReference<View> {
        val owner = rule.onNode(isPopup()).fetchSemanticsNode().root as View
        return WeakReference(owner.parent as View)
    }

    private fun isCollected(reference: WeakReference<View>): Boolean {
        repeat(25) {
            Runtime.getRuntime().gc()
            Runtime.getRuntime().runFinalization()
            Thread.sleep(120)
            if (reference.get() == null) return true
        }
        return false
    }

    private fun hoverMoreCell() {
        firstBar().performMouseInput { moveTo(Offset(width - 24.dp.toPx(), height / 2f)) }
        rule.mainClock.advanceTimeBy(900)
        rule.waitForIdle()
    }

    private fun clickMoreCell() {
        firstBar().performTouchInput { click(Offset(width - 24.dp.toPx(), height / 2f)) }
        rule.waitForIdle()
    }

    @Test
    fun aTooltipShownByHoverIsReleasedWhenItsRowIsRemoved() {
        showList()
        hoverMoreCell()
        val popup = trackPopup()
        rule.runOnIdle { notes = notes.drop(1) }
        rule.waitForIdle()
        rule.onAllNodes(isPopup()).assertCountEquals(0)
        assertTrue("the tooltip window is still reachable after its row was removed", isCollected(popup))
    }

    @Test
    fun aTooltipShownByHoverIsReleasedWhenItsRowScrollsAway() {
        showList()
        hoverMoreCell()
        val popup = trackPopup()
        rule.runOnIdle { scope.launch { listState.scrollToItem(30) } }
        rule.waitForIdle()
        rule.onAllNodes(isPopup()).assertCountEquals(0)
        assertTrue("the tooltip window is still reachable after its row scrolled away", isCollected(popup))
    }

    @Test
    fun theMoreMenuIsReleasedWhenItsRowIsRemoved() {
        showList()
        clickMoreCell()
        val popup = trackPopup()
        rule.runOnIdle { notes = notes.drop(1) }
        rule.waitForIdle()
        rule.onAllNodes(isPopup()).assertCountEquals(0)
        assertTrue("the menu window is still reachable after its row was removed", isCollected(popup))
    }
}

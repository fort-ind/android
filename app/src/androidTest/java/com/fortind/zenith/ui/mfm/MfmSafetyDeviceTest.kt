package com.fortind.zenith.ui.mfm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.mfm.MfmParser
import com.fortind.zenith.ui.feed.DeceptiveLinkDialog
import com.fortind.zenith.ui.theme.ZenithTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MfmSafetyDeviceTest {
    @get:Rule
    val rule = createComposeRule()

    private val holdMillis = InstrumentationRegistry.getArguments().getString("holdMillis")?.toLong() ?: 0L

    @After
    fun hold() {
        Thread.sleep(holdMillis)
    }

    private fun showPost(text: String, opened: MutableList<String>) {
        val nodes = MfmParser.parse(text)
        rule.setContent {
            ZenithTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var pending by remember { mutableStateOf<DeceptiveLink?>(null) }
                    val handler = remember {
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                opened += uri
                            }
                        }
                    }
                    CompositionLocalProvider(LocalUriHandler provides handler, LocalDeceptiveLinks provides { link: DeceptiveLink -> pending = link }) {
                        Column {
                            MfmContent(nodes, rememberMfmEnvironment({ "" }, animate = false), modifier = Modifier.padding(24.dp))
                        }
                    }
                    pending?.let { link ->
                        DeceptiveLinkDialog(
                            link = link,
                            onOpen = {
                                pending = null
                                handler.openUri(link.url)
                            },
                            onDismiss = { pending = null },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aLinkShowingAnotherHostAsksFirstAndOpensOnlyWhenConfirmed() {
        val opened = mutableListOf<String>()
        showPost("[https://bank.example](https://evil.example/login)", opened)

        rule.onNodeWithText("https://bank.example").performClick()
        rule.onNodeWithText("This post shows bank.example, but the link goes to evil.example.").assertExists()
        assertTrue(opened.isEmpty())
        if (holdMillis > 0) Thread.sleep(holdMillis)

        rule.onNodeWithText("Open anyway").performClick()
        rule.runOnIdle { assertEquals(listOf("https://evil.example/login"), opened) }
    }

    @Test
    fun anHonestLinkOpensWithoutAsking() {
        val opened = mutableListOf<String>()
        showPost("[example.com](https://www.example.com/page)", opened)

        rule.onNodeWithText("example.com").performClick()
        rule.runOnIdle { assertEquals(listOf("https://www.example.com/page"), opened) }
        rule.onNodeWithText("Open anyway").assertDoesNotExist()
    }

    @Test
    fun movedAndScaledTextCannotDrawOverWhatIsAboveThePost() {
        val nodes = MfmParser.parse("\$[position.y=-5 \$[scale.x=5,y=5 \$[x4 ████]]]")
        rule.setContent {
            ZenithTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.Black).testTag("above"))
                        MfmContent(
                            nodes,
                            rememberMfmEnvironment({ "" }, animate = false),
                            style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }

        val pixels = rule.onNodeWithTag("above").captureToImage().toPixelMap()
        val lit = (0 until pixels.width step 4).sumOf { x ->
            (0 until pixels.height step 4).count { y -> pixels[x, y] != Color.Black }
        }
        assertEquals("pixels drawn over the area above the post", 0, lit)
    }
}

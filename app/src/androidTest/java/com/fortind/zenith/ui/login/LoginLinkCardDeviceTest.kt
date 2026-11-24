package com.fortind.zenith.ui.login

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortind.zenith.ui.theme.ZenithTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginLinkCardDeviceTest {
    @get:Rule
    val rule = createComposeRule()

    private val arguments = InstrumentationRegistry.getArguments()
    private val holdMillis = arguments.getString("holdMillis")?.toLong() ?: 0L
    private val saveAs = arguments.getString("saveAs")
    private val url = "https://social.fort1nd.com/miauth/00000000-test-session?name=fort.android&permission=read:account"

    @After
    fun hold() {
        Thread.sleep(holdMillis)
    }

    @Test
    fun theLinkIsShownSecurelyAndCopiedOnlyOnTapAsSensitive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
        }
        var activity: Activity? = null
        var showCard by mutableStateOf(true)
        rule.setContent {
            activity = LocalActivity.current
            ZenithTheme {
                LoginContent(
                    state = LoginUiState(signingIn = true, manualUrl = url.takeIf { showCard }),
                    onLogIn = {},
                    onCancel = {},
                    onBrowseWithoutSigningIn = {},
                )
            }
        }

        rule.runOnIdle { assertTrue(activity!!.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        rule.runOnIdle { assertNotEquals("Sign-in link", clipboard.primaryClipDescription?.label?.toString()) }
        saveAs?.let { name ->
            val bitmap = rule.onNode(isRoot()).captureToImage().asAndroidBitmap()
            File(context.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        if (holdMillis > 0) Thread.sleep(holdMillis)

        rule.onNodeWithText("Copy link").performScrollTo().performClick()
        rule.waitUntil(5_000) { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == url }

        showCard = false
        rule.runOnIdle { assertFalse(activity!!.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
        }
    }

    @Test
    fun theCopiedClipIsMarkedSensitive() {
        val description = sensitiveClip("Sign-in link", url).description
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
        assertEquals("Sign-in link", description.label)
        assertTrue(description.extras?.getBoolean(key) == true)
    }
}

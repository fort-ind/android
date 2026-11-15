package com.fortind.zenith.ui.media

import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimatedDrawableStopperTest {

    @get:Rule
    val rule = createComposeRule()

    private class FakeAnimatedDrawable : ColorDrawable(), Animatable {
        private var running = false

        override fun start() {
            running = true
        }

        override fun stop() {
            running = false
        }

        override fun isRunning(): Boolean = running
    }

    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) = Unit

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit

        override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    }

    private fun showTracking(drawable: Drawable): (Boolean) -> Unit {
        var shown by mutableStateOf(true)
        rule.setContent {
            if (shown) {
                val stopper = rememberAnimatedDrawableStopper(animate = true)
                SideEffect { stopper?.track(drawable.asImage()) }
            }
        }
        return { shown = it }
    }

    @Test
    fun leavingCompositionStopsTheTrackedAnimation() {
        val drawable = FakeAnimatedDrawable().apply {
            callback = this@AnimatedDrawableStopperTest.callback
            setVisible(true, true)
            start()
        }
        val setShown = showTracking(drawable)
        rule.runOnIdle { assertTrue(drawable.isRunning) }

        rule.runOnIdle { setShown(false) }

        rule.runOnIdle {
            assertFalse(drawable.isRunning)
            assertFalse(drawable.isVisible)
            assertNull(drawable.callback)
        }
    }

    @Test
    fun staticDrawablesAreLeftAlone() {
        val drawable = ColorDrawable().apply {
            callback = this@AnimatedDrawableStopperTest.callback
            setVisible(true, true)
        }
        val setShown = showTracking(drawable)

        rule.runOnIdle { setShown(false) }

        rule.runOnIdle {
            assertTrue(drawable.isVisible)
            assertSame(callback, drawable.callback)
        }
    }

    @Test
    fun noStopperWhenAnimationIsOff() {
        var stopper: AnimatedDrawableStopper? = AnimatedDrawableStopper()
        rule.setContent { stopper = rememberAnimatedDrawableStopper(animate = false) }
        rule.runOnIdle { assertNull(stopper) }
    }
}

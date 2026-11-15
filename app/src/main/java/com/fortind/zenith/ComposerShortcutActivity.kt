package com.fortind.zenith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.fortind.zenith.window.AppWindows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ComposerShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = (application as ZenithApplication).container.settingsStore
        lifecycleScope.launch {
            val size = settingsStore.settings.first().composerWindowSize
            startActivity(
                AppWindows.outsideComposerIntent(this@ComposerShortcutActivity),
                AppWindows.defaultDisplayComposerOptions(this@ComposerShortcutActivity, size),
            )
            finish()
        }
    }
}

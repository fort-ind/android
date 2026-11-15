package com.fortind.zenith.shortcuts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fortind.zenith.ZenithApplication
import kotlinx.coroutines.launch

class ShortcutLocaleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCALE_CHANGED) return
        val container = (context.applicationContext as ZenithApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                container.launcherShortcuts.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}

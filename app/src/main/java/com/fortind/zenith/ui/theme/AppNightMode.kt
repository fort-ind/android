package com.fortind.zenith.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

fun ThemeMode.applicationNightMode(): Int = when (this) {
    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
}

class AppNightModeSync(private val context: Context, private val settings: Flow<AppSettings>) {
    fun start(scope: CoroutineScope) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = context.getSystemService(UiModeManager::class.java) ?: return
        scope.launch {
            settings.map { it.themeMode }.distinctUntilChanged().collect { apply(manager, it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun apply(manager: UiModeManager, mode: ThemeMode) {
        manager.setApplicationNightMode(mode.applicationNightMode())
    }
}

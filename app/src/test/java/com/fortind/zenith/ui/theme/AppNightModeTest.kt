package com.fortind.zenith.ui.theme

import android.app.UiModeManager
import com.fortind.zenith.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNightModeTest {

    @Test
    fun theThemeSettingMapsToThePerAppNightMode() {
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, ThemeMode.SYSTEM.applicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_NO, ThemeMode.LIGHT.applicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_YES, ThemeMode.DARK.applicationNightMode())
    }
}

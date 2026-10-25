package com.fortind.zenith.ui.settings

import com.fortind.zenith.settings.AppIcon
import com.fortind.zenith.settings.AppIconSwitcher
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.SettingsStore
import com.fortind.zenith.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exposesTheStoredSettings() = runTest(dispatcher) {
        val store = FakeSettingsStore(AppSettings(themeMode = ThemeMode.DARK, dynamicColor = false))
        val viewModel = SettingsViewModel(store, FakeIconSwitcher())

        advanceUntilIdle()

        assertEquals(AppSettings(themeMode = ThemeMode.DARK, dynamicColor = false), viewModel.settings.value)
    }

    @Test
    fun setThemeModeWritesThroughToTheStore() = runTest(dispatcher) {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeIconSwitcher())

        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, store.current.themeMode)
        assertEquals(ThemeMode.LIGHT, viewModel.settings.value.themeMode)
    }

    @Test
    fun setDynamicColorWritesThroughToTheStore() = runTest(dispatcher) {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeIconSwitcher())

        viewModel.setDynamicColor(false)
        advanceUntilIdle()

        assertEquals(false, store.current.dynamicColor)
        assertEquals(false, viewModel.settings.value.dynamicColor)
    }

    @Test
    fun appIconStartsAsTheCurrentlyEnabledIcon() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeSettingsStore(), FakeIconSwitcher(AppIcon.FALL))

        assertEquals(AppIcon.FALL, viewModel.appIcon.value)
    }

    @Test
    fun setAppIconSwitchesTheLauncherIcon() = runTest(dispatcher) {
        val switcher = FakeIconSwitcher()
        val viewModel = SettingsViewModel(FakeSettingsStore(), switcher)

        viewModel.setAppIcon(AppIcon.FALL)

        assertEquals(AppIcon.FALL, switcher.current())
        assertEquals(AppIcon.FALL, viewModel.appIcon.value)
    }

    private class FakeIconSwitcher(private var icon: AppIcon = AppIcon.DEFAULT) : AppIconSwitcher {
        override fun current() = icon

        override fun set(icon: AppIcon) {
            this.icon = icon
        }
    }

    private class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
        private val state = MutableStateFlow(initial)
        val current: AppSettings get() = state.value

        override val settings: Flow<AppSettings> = state

        override suspend fun setThemeMode(mode: ThemeMode) {
            state.update { it.copy(themeMode = mode) }
        }

        override suspend fun setDynamicColor(enabled: Boolean) {
            state.update { it.copy(dynamicColor = enabled) }
        }
    }
}

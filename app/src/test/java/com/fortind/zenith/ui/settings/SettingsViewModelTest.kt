package com.fortind.zenith.ui.settings

import com.fortind.zenith.notifications.DefaultNotificationCategories
import com.fortind.zenith.notifications.NotificationCategory
import com.fortind.zenith.settings.AppIcon
import com.fortind.zenith.settings.AppIconSwitcher
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.FakeSettingsStore
import com.fortind.zenith.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun setShowHomeFavoritesWritesThroughToTheStore() = runTest(dispatcher) {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeIconSwitcher())

        viewModel.setShowHomeFavorites(false)
        advanceUntilIdle()

        assertEquals(false, store.current.showHomeFavorites)
        assertEquals(false, viewModel.settings.value.showHomeFavorites)
    }

    @Test
    fun systemNotificationsAreOffByDefaultAndCanBeToggled() = runTest(dispatcher) {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeIconSwitcher())
        assertEquals(false, store.current.systemNotifications)

        viewModel.setSystemNotifications(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.settings.value.systemNotifications)
    }

    @Test
    fun notificationCategoriesCanBeTurnedOnAndOff() = runTest(dispatcher) {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(store, FakeIconSwitcher())
        assertEquals(DefaultNotificationCategories, store.current.notificationCategories)

        viewModel.setNotificationCategory(NotificationCategory.Reactions, false)
        viewModel.setNotificationCategory(NotificationCategory.Other, true)
        advanceUntilIdle()

        assertEquals(
            setOf(
                NotificationCategory.Mentions,
                NotificationCategory.Renotes,
                NotificationCategory.Follows,
                NotificationCategory.Other,
            ),
            viewModel.settings.value.notificationCategories,
        )
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

    private class FakeIconSwitcher(icon: AppIcon = AppIcon.DEFAULT) : AppIconSwitcher {
        private val _selected = MutableStateFlow(icon)
        override val selected: StateFlow<AppIcon> = _selected

        override fun current() = _selected.value

        override fun set(icon: AppIcon) {
            _selected.value = icon
        }
    }
}

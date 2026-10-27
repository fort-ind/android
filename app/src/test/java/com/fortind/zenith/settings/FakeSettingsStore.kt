package com.fortind.zenith.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    private val state = MutableStateFlow(initial)
    val current: AppSettings get() = state.value

    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.update { it.copy(dynamicColor = enabled) }
    }

    override suspend fun setShowHomeFavorites(show: Boolean) {
        state.update { it.copy(showHomeFavorites = show) }
    }

    override suspend fun setHomeFavoritesExpanded(expanded: Boolean) {
        state.update { it.copy(homeFavoritesExpanded = expanded) }
    }
}

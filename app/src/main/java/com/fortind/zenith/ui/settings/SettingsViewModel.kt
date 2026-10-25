package com.fortind.zenith.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.settings.AppIcon
import com.fortind.zenith.settings.AppIconSwitcher
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.SettingsStore
import com.fortind.zenith.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
    private val iconSwitcher: AppIconSwitcher,
) : ViewModel() {

    // Eager, so the saved values are already here when the page opens.
    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _appIcon = MutableStateFlow(iconSwitcher.current())
    val appIcon: StateFlow<AppIcon> = _appIcon.asStateFlow()

    fun setAppIcon(icon: AppIcon) {
        iconSwitcher.set(icon)
        _appIcon.value = iconSwitcher.current()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { store.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { store.setDynamicColor(enabled) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                SettingsViewModel(app.container.settingsStore, app.container.appIconSwitcher)
            }
        }
    }
}

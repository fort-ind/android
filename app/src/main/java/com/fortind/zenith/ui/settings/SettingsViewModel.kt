package com.fortind.zenith.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.notifications.NotificationCategory
import com.fortind.zenith.settings.AppIcon
import com.fortind.zenith.settings.AppIconSwitcher
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.SettingsStore
import com.fortind.zenith.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
    private val iconSwitcher: AppIconSwitcher,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val appIcon: StateFlow<AppIcon> = iconSwitcher.selected

    fun setAppIcon(icon: AppIcon) {
        iconSwitcher.set(icon)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { store.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { store.setDynamicColor(enabled) }
    }

    fun setShowHomeFavorites(show: Boolean) {
        viewModelScope.launch { store.setShowHomeFavorites(show) }
    }

    fun setCompressUploads(enabled: Boolean) {
        viewModelScope.launch { store.setCompressUploads(enabled) }
    }

    fun setSystemNotifications(enabled: Boolean) {
        viewModelScope.launch { store.setSystemNotifications(enabled) }
    }

    fun setNotificationCategory(category: NotificationCategory, enabled: Boolean) {
        viewModelScope.launch { store.setNotificationCategory(category, enabled) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                SettingsViewModel(
                    store = app.container.settingsStore,
                    iconSwitcher = app.container.appIconSwitcher,
                )
            }
        }
    }
}

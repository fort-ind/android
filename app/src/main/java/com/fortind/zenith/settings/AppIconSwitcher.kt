package com.fortind.zenith.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppIcon(val aliasName: String) {
    DEFAULT("LauncherDefault"),
    FALL("LauncherFall"),
    ;

    val launcherClassName: String get() = "com.fortind.zenith.$aliasName"
}

interface AppIconSwitcher {
    val selected: StateFlow<AppIcon>
    fun current(): AppIcon
    fun set(icon: AppIcon)
}

class PackageManagerAppIconSwitcher(context: Context, private val scope: CoroutineScope) : AppIconSwitcher {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager
    private val writes = Dispatchers.IO.limitedParallelism(1)
    private val _selected = MutableStateFlow(current())

    override val selected: StateFlow<AppIcon> = _selected.asStateFlow()

    override fun current(): AppIcon =
        if (stateOf(AppIcon.FALL) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) AppIcon.FALL else AppIcon.DEFAULT

    override fun set(icon: AppIcon) {
        _selected.value = icon
        scope.launch(writes) {
            setEnabled(icon, true)
            AppIcon.entries.filter { it != icon }.forEach { setEnabled(it, false) }
            _selected.value = current()
        }
    }

    private fun component(icon: AppIcon) = ComponentName(context.packageName, icon.launcherClassName)

    private fun stateOf(icon: AppIcon) = packageManager.getComponentEnabledSetting(component(icon))

    private fun setEnabled(icon: AppIcon, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(component(icon), state, PackageManager.DONT_KILL_APP)
    }
}

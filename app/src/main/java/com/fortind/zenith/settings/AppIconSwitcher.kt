package com.fortind.zenith.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIcon(val aliasName: String) {
    DEFAULT("LauncherDefault"),
    FALL("LauncherFall"),
}

interface AppIconSwitcher {
    fun current(): AppIcon
    fun set(icon: AppIcon)
}

/** The enabled launcher alias is the source of truth, so the choice needs no separate persistence. */
class PackageManagerAppIconSwitcher(context: Context) : AppIconSwitcher {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager

    override fun current(): AppIcon =
        if (stateOf(AppIcon.FALL) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) AppIcon.FALL else AppIcon.DEFAULT

    override fun set(icon: AppIcon) {
        // Enable the new alias before disabling the old one so the app is never missing from the launcher.
        setEnabled(icon, true)
        AppIcon.entries.filter { it != icon }.forEach { setEnabled(it, false) }
    }

    // Alias classes live under the namespace, which differs from packageName if an applicationId suffix is ever added.
    private fun component(icon: AppIcon) = ComponentName(context.packageName, "com.fortind.zenith.${icon.aliasName}")

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

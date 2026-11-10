package com.fortind.zenith.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

@Stable
class AppBackStacks(
    initialTab: AppDestination = AppDestination.HOME,
    initial: Map<AppDestination, List<AppRoute>> = emptyMap(),
) {
    private val stacks: Map<AppDestination, SnapshotStateList<AppRoute>> = AppDestination.entries.associateWith { destination ->
        mutableStateListOf<AppRoute>().apply { addAll(initial[destination]?.takeIf { it.isNotEmpty() } ?: listOf(destination.root)) }
    }

    var tab: AppDestination by mutableStateOf(initialTab)
        private set

    var fixedTab: AppDestination? = null

    var onLeaveFixedTab: (AppDestination, AppRoute?) -> Unit = { _, _ -> }

    private fun leavesFixedTab(destination: AppDestination, route: AppRoute? = null): Boolean {
        val fixed = fixedTab ?: return false
        if (destination == fixed) return false
        onLeaveFixedTab(destination, route)
        return true
    }

    val current: SnapshotStateList<AppRoute> get() = stack(tab)

    val top: AppRoute get() = current.last()

    val openThreadId: String? get() = current.lastOrNull { it is AppRoute.Thread }?.let { (it as AppRoute.Thread).noteId }

    val atHomeRoot: Boolean get() = tab == AppDestination.HOME && current.size == 1

    fun stack(destination: AppDestination): SnapshotStateList<AppRoute> = stacks.getValue(destination)

    fun select(destination: AppDestination) {
        if (leavesFixedTab(destination)) return
        if (destination == tab) {
            popToRoot()
        } else {
            tab = destination
        }
    }

    fun push(route: AppRoute) {
        if (current.last() != route) current.add(route)
    }

    fun openIn(destination: AppDestination, route: AppRoute) {
        if (leavesFixedTab(destination, route)) return
        tab = destination
        push(route)
    }

    fun pop(): Boolean {
        if (current.size <= 1) return false
        current.removeAt(current.lastIndex)
        return true
    }

    fun back(): Boolean = when {
        pop() -> true
        fixedTab == null && tab != AppDestination.HOME -> {
            tab = AppDestination.HOME
            true
        }
        else -> false
    }

    fun showRoot(destination: AppDestination) {
        if (leavesFixedTab(destination)) return
        val stack = stack(destination)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        tab = destination
    }

    fun openEditProfile() {
        if (leavesFixedTab(AppDestination.SETTINGS, AppRoute.Settings(openEditProfile = true))) return
        val settings = stack(AppDestination.SETTINGS)
        settings.clear()
        settings.add(AppRoute.Settings(openEditProfile = true))
        tab = AppDestination.SETTINGS
    }

    fun editProfileHandled() {
        val settings = stack(AppDestination.SETTINGS)
        settings[0] = AppRoute.Settings()
    }

    private fun popToRoot() {
        while (current.size > 1) current.removeAt(current.lastIndex)
    }

    companion object {
        val Saver: Saver<AppBackStacks, List<List<String>>> = Saver(
            save = { stacks ->
                listOf(listOf(stacks.tab.name)) + AppDestination.entries.map { destination ->
                    stacks.stack(destination).map(AppRoute::encode)
                }
            },
            restore = { saved -> restore(saved) },
        )

        internal fun restore(saved: List<List<String>>): AppBackStacks {
            val tab = AppDestination.entries.firstOrNull { it.name == saved.firstOrNull()?.firstOrNull() } ?: AppDestination.HOME
            val initial = AppDestination.entries.withIndex().associate { (index, destination) ->
                destination to saved.getOrNull(index + 1).orEmpty().mapNotNull(AppRoute::decode)
            }
            return AppBackStacks(tab, initial)
        }
    }
}

@Composable
fun rememberAppBackStacks(fixedTab: AppDestination? = null): AppBackStacks =
    rememberSaveable(saver = AppBackStacks.Saver) { AppBackStacks(initialTab = fixedTab ?: AppDestination.HOME) }
        .also { it.fixedTab = fixedTab }

package com.fortind.zenith.ui.nav

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

internal const val DetailPaneMarker = "com.fortind.zenith.ui.nav.detailPane"

internal class DetailCollapsedSceneStrategy<T : Any>(
    private val delegate: SceneStrategy<T>,
    private val detailCollapsed: Boolean,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (detailCollapsed && entries.last().metadata.containsKey(DetailPaneMarker)) return null
        return with(delegate) { calculateScene(entries) }
    }
}

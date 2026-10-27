package com.fortind.zenith.games

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFavoritesStore(vararg initial: String) : FavoritesStore {
    private val state = MutableStateFlow(initial.toList())
    val current: List<String> get() = state.value

    override val urls: Flow<List<String>> = state

    override suspend fun toggle(url: String): FavoriteEdit {
        val (updated, edit) = toggleFavorite(state.value, url)
        state.value = updated
        return edit
    }

    override suspend fun revert(edit: FavoriteEdit) {
        state.value = revertFavorite(state.value, edit)
    }
}

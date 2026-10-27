package com.fortind.zenith.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.games.FavoriteEditor
import com.fortind.zenith.games.FavoriteNotice
import com.fortind.zenith.games.FavoritesStore
import com.fortind.zenith.games.Game
import com.fortind.zenith.games.gameFromUrl
import com.fortind.zenith.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val showFavorites: Boolean,
        val favoritesExpanded: Boolean,
        val favorites: List<Game>,
        val favoriteCount: Int,
    ) : HomeUiState {
        val hasMoreFavorites: Boolean get() = favoriteCount > favorites.size
    }
}

class HomeViewModel(
    favoritesStore: FavoritesStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val favoriteEditor = FavoriteEditor(favoritesStore, viewModelScope)

    val favoriteNotice: StateFlow<FavoriteNotice?> = favoriteEditor.notice

    val uiState: StateFlow<HomeUiState> = combine(favoritesStore.urls, settingsStore.settings) { urls, settings ->
        val games = urls.mapNotNull(::gameFromUrl)
        HomeUiState.Ready(
            showFavorites = settings.showHomeFavorites,
            favoritesExpanded = settings.homeFavoritesExpanded,
            favorites = games.take(MAX_HOME_FAVORITES),
            favoriteCount = games.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun setFavoritesExpanded(expanded: Boolean) {
        viewModelScope.launch { settingsStore.setHomeFavoritesExpanded(expanded) }
    }

    fun toggleFavorite(game: Game) = favoriteEditor.toggle(game)

    fun undoFavorite(notice: FavoriteNotice) = favoriteEditor.undo(notice)

    fun favoriteNoticeShown(notice: FavoriteNotice) = favoriteEditor.noticeShown(notice)

    companion object {
        const val MAX_HOME_FAVORITES = 8

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                HomeViewModel(app.container.favoritesStore, app.container.settingsStore)
            }
        }
    }
}

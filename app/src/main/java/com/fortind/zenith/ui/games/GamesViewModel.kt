package com.fortind.zenith.ui.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.games.FavoriteEditor
import com.fortind.zenith.games.FavoriteNotice
import com.fortind.zenith.games.FavoritesStore
import com.fortind.zenith.games.Game
import com.fortind.zenith.games.GameCatalog
import com.fortind.zenith.games.GameCategory
import com.fortind.zenith.games.GameSection
import com.fortind.zenith.games.sectionGames
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xml.sax.SAXException

sealed interface GamesUiState {
    data object Loading : GamesUiState
    data object Failed : GamesUiState

    data class Loaded(
        val games: List<Game>,
        val sections: List<GameSection>,
        val favorites: Set<String>,
    ) : GamesUiState
}

sealed interface GameRequest {
    val url: String

    data class Open(val game: Game) : GameRequest {
        override val url: String get() = game.url
    }

    data class Missing(override val url: String) : GameRequest
}

private sealed interface CatalogState {
    data object Loading : CatalogState
    data object Failed : CatalogState
    data class Loaded(val games: List<Game>) : CatalogState
}

class GamesViewModel(
    private val catalog: GameCatalog,
    favoritesStore: FavoritesStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val catalogState = MutableStateFlow<CatalogState>(CatalogState.Loading)

    private val favoriteEditor = FavoriteEditor(favoritesStore, viewModelScope)

    val favoriteNotice: StateFlow<FavoriteNotice?> = favoriteEditor.notice

    private val _selectedCategories = MutableStateFlow(
        savedState.get<List<String>>(KEY_CATEGORIES).orEmpty()
            .mapNotNull { name -> GameCategory.entries.find { it.name == name } }
            .toSet()
    )

    val selectedCategories: StateFlow<Set<GameCategory>> = _selectedCategories.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(savedState.get<Boolean>(KEY_FAVORITES_ONLY) ?: false)

    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val uiState: StateFlow<GamesUiState> = combine(
        catalogState,
        _selectedCategories,
        _favoritesOnly,
        favoritesStore.urls,
    ) { state, selected, favoritesOnly, favoriteUrls ->
        when (state) {
            CatalogState.Loading -> GamesUiState.Loading
            CatalogState.Failed -> GamesUiState.Failed
            is CatalogState.Loaded -> {
                val favorites = favoriteUrls.toSet()
                val shown = state.games.filter { game ->
                    (selected.isEmpty() || game.category in selected) && (!favoritesOnly || game.url in favorites)
                }
                GamesUiState.Loaded(games = state.games, sections = sectionGames(shown), favorites = favorites)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesUiState.Loading)

    private val requestedGameUrl = MutableStateFlow(savedState.get<String>(KEY_REQUESTED_GAME))

    val gameRequest: StateFlow<GameRequest?> = combine(catalogState, requestedGameUrl) { state, url ->
        if (url == null || state !is CatalogState.Loaded) {
            null
        } else {
            state.games.firstOrNull { it.url == url }?.let(GameRequest::Open) ?: GameRequest.Missing(url)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun retry() {
        if (catalogState.value == CatalogState.Failed) load()
    }

    fun toggleCategory(category: GameCategory) {
        val updated = _selectedCategories.value.let { if (category in it) it - category else it + category }
        _selectedCategories.value = updated
        savedState[KEY_CATEGORIES] = ArrayList(updated.map { it.name })
    }

    fun clearFilters() {
        _selectedCategories.value = emptySet()
        savedState[KEY_CATEGORIES] = ArrayList<String>()
        setFavoritesOnly(false)
    }

    fun toggleFavoritesOnly() {
        setFavoritesOnly(!_favoritesOnly.value)
    }

    fun showFavorites() {
        clearFilters()
        setFavoritesOnly(true)
    }

    fun toggleFavorite(game: Game) = favoriteEditor.toggle(game)

    fun undoFavorite(notice: FavoriteNotice) = favoriteEditor.undo(notice)

    fun favoriteNoticeShown(notice: FavoriteNotice) = favoriteEditor.noticeShown(notice)

    fun requestGame(url: String) {
        requestedGameUrl.value = url
        savedState[KEY_REQUESTED_GAME] = url
    }

    fun gameRequestHandled(request: GameRequest) {
        if (requestedGameUrl.value != request.url) return
        requestedGameUrl.value = null
        savedState.remove<String>(KEY_REQUESTED_GAME)
    }

    private fun setFavoritesOnly(favoritesOnly: Boolean) {
        _favoritesOnly.value = favoritesOnly
        savedState[KEY_FAVORITES_ONLY] = favoritesOnly
    }

    private fun load() {
        catalogState.value = CatalogState.Loading
        viewModelScope.launch {
            catalogState.value = try {
                CatalogState.Loaded(catalog.games())
            } catch (_: IOException) {
                CatalogState.Failed
            } catch (_: SAXException) {
                CatalogState.Failed
            }
        }
    }

    companion object {
        private const val KEY_CATEGORIES = "selected_categories"
        private const val KEY_FAVORITES_ONLY = "favorites_only"
        private const val KEY_REQUESTED_GAME = "requested_game"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                GamesViewModel(app.container.gameCatalog, app.container.favoritesStore, createSavedStateHandle())
            }
        }
    }
}

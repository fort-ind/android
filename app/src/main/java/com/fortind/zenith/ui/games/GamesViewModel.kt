package com.fortind.zenith.ui.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
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

    /** [games] is the whole catalog (what search looks through); [sections] is it after the category filter. */
    data class Loaded(val games: List<Game>, val sections: List<GameSection>) : GamesUiState
}

private sealed interface CatalogState {
    data object Loading : CatalogState
    data object Failed : CatalogState
    data class Loaded(val games: List<Game>) : CatalogState
}

class GamesViewModel(
    private val catalog: GameCatalog,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val catalogState = MutableStateFlow<CatalogState>(CatalogState.Loading)

    private val _selectedCategories = MutableStateFlow(
        savedState.get<List<String>>(KEY_CATEGORIES).orEmpty()
            .mapNotNull { name -> GameCategory.entries.find { it.name == name } }
            .toSet()
    )

    /** Empty means no filter: every category is shown. */
    val selectedCategories: StateFlow<Set<GameCategory>> = _selectedCategories.asStateFlow()

    val uiState: StateFlow<GamesUiState> = combine(catalogState, _selectedCategories) { state, selected ->
        when (state) {
            CatalogState.Loading -> GamesUiState.Loading
            CatalogState.Failed -> GamesUiState.Failed
            is CatalogState.Loaded -> {
                val shown = if (selected.isEmpty()) state.games else state.games.filter { it.category in selected }
                GamesUiState.Loaded(games = state.games, sections = sectionGames(shown))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesUiState.Loading)

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

    fun clearCategories() {
        _selectedCategories.value = emptySet()
        savedState[KEY_CATEGORIES] = ArrayList<String>()
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

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                GamesViewModel(app.container.gameCatalog, createSavedStateHandle())
            }
        }
    }
}

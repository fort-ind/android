package com.fortind.zenith.ui.games

import androidx.lifecycle.SavedStateHandle
import com.fortind.zenith.games.FakeFavoritesStore
import com.fortind.zenith.games.Game
import com.fortind.zenith.games.GameCatalog
import com.fortind.zenith.games.GameCategory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GamesViewModelTest {

    private val games = listOf(
        Game("Abandoned", GameCategory.FLASH, "a"),
        Game("Doom", GameCategory.RETRO, "d"),
        Game("Duck Life", GameCategory.FLASH, "dl"),
    )

    private class ScriptedCatalog(vararg responses: () -> List<Game>) : GameCatalog {
        private val queue = ArrayDeque(responses.toList())
        override suspend fun games(): List<Game> = queue.removeFirst()()
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadedGamesAreGroupedByLetter() = runTest {
        val viewModel = GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore(), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value as GamesUiState.Loaded
        assertEquals(listOf("A", "D"), state.sections.map { it.letter })
        assertEquals(3, state.games.size)
    }

    @Test
    fun categoryFilterNarrowsSectionsButNotSearchableGames() = runTest {
        val viewModel = GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore(), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.toggleCategory(GameCategory.RETRO)

        val state = viewModel.uiState.value as GamesUiState.Loaded
        assertEquals(listOf("d"), state.sections.flatMap { it.games }.map { it.url })
        assertEquals(3, state.games.size)
    }

    @Test
    fun selectedCategoriesSurviveProcessDeath() {
        val saved = SavedStateHandle()
        GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore(), saved).toggleCategory(GameCategory.FLASH)

        val restored = GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore(), saved)

        assertEquals(setOf(GameCategory.FLASH), restored.selectedCategories.value)
    }

    @Test
    fun retryAfterAFailedLoadSucceeds() = runTest {
        val viewModel = GamesViewModel(
            ScriptedCatalog({ throw IOException("missing") }, { games }),
            FakeFavoritesStore(),
            SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals(GamesUiState.Failed, viewModel.uiState.value)

        viewModel.retry()

        assertEquals(3, (viewModel.uiState.value as GamesUiState.Loaded).games.size)
    }

    @Test
    fun favoritesFilterShowsOnlyFavoritedGames() = runTest {
        val viewModel = GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore("dl", "a"), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.toggleFavoritesOnly()

        val state = viewModel.uiState.value as GamesUiState.Loaded
        assertEquals(listOf("a", "dl"), state.sections.flatMap { it.games }.map { it.url })
        assertEquals(setOf("a", "dl"), state.favorites)
    }

    @Test
    fun favoritesFilterCombinesWithCategories() = runTest {
        val viewModel = GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore("d", "a"), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.toggleFavoritesOnly()
        viewModel.toggleCategory(GameCategory.FLASH)

        val state = viewModel.uiState.value as GamesUiState.Loaded
        assertEquals(listOf("a"), state.sections.flatMap { it.games }.map { it.url })
    }

    @Test
    fun showFavoritesClearsCategoriesAndSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val viewModel = GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore(), saved)
        viewModel.toggleCategory(GameCategory.RETRO)

        viewModel.showFavorites()

        val restored = GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore(), saved)
        assertEquals(emptySet<GameCategory>(), restored.selectedCategories.value)
        assertEquals(true, restored.favoritesOnly.value)
    }

    @Test
    fun clearFiltersTurnsOffTheFavoritesFilter() {
        val viewModel = GamesViewModel(ScriptedCatalog({ games }), FakeFavoritesStore(), SavedStateHandle())
        viewModel.showFavorites()

        viewModel.clearFilters()

        assertEquals(false, viewModel.favoritesOnly.value)
    }

    @Test
    fun togglingAFavoritePostsAnUndoableNotice() = runTest {
        val store = FakeFavoritesStore()
        val viewModel = GamesViewModel(ScriptedCatalog({ games }), store, SavedStateHandle())

        viewModel.toggleFavorite(games[1])

        assertEquals(listOf("d"), store.current)
        val notice = viewModel.favoriteNotice.value!!
        assertEquals("Doom", notice.title)
        assertEquals(true, notice.edit.added)

        viewModel.undoFavorite(notice)
        viewModel.favoriteNoticeShown(notice)

        assertEquals(emptyList<String>(), store.current)
        assertEquals(null, viewModel.favoriteNotice.value)
    }
}

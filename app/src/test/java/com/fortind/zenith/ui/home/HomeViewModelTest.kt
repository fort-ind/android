package com.fortind.zenith.ui.home

import com.fortind.zenith.games.FakeFavoritesStore
import com.fortind.zenith.games.GameCategory
import com.fortind.zenith.games.gameFromUrl
import com.fortind.zenith.settings.AppSettings
import com.fortind.zenith.settings.FakeSettingsStore
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
class HomeViewModelTest {

    private fun url(slug: String) = "https://www.fort1nd.com/games/html/$slug"

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun favoritesKeepTheirMostRecentFirstOrder() = runTest {
        val viewModel = HomeViewModel(FakeFavoritesStore(url("slope"), url("retro-bowl")), FakeSettingsStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(listOf("Slope", "Retro Bowl"), state.favorites.map { it.title })
        assertEquals(GameCategory.HTML, state.favorites.first().category)
        assertEquals(false, state.hasMoreFavorites)
    }

    @Test
    fun homeShowsAtMostEightFavorites() = runTest {
        val urls = (1..10).map { url("game-$it") }.toTypedArray()
        val viewModel = HomeViewModel(FakeFavoritesStore(*urls), FakeSettingsStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(HomeViewModel.MAX_HOME_FAVORITES, state.favorites.size)
        assertEquals(10, state.favoriteCount)
        assertEquals(true, state.hasMoreFavorites)
    }

    @Test
    fun urlsThatAreNotGamesAreSkipped() = runTest {
        val viewModel = HomeViewModel(
            FakeFavoritesStore("https://www.fort1nd.com/about", url("slope")),
            FakeSettingsStore(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(listOf(url("slope")), state.favorites.map { it.url })
    }

    @Test
    fun followsTheShowAndExpandedSettings() = runTest {
        val settings = FakeSettingsStore(AppSettings(showHomeFavorites = false))
        val viewModel = HomeViewModel(FakeFavoritesStore(), settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.setFavoritesExpanded(false)

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(false, state.showFavorites)
        assertEquals(false, state.favoritesExpanded)
        assertEquals(false, settings.current.homeFavoritesExpanded)
    }

    @Test
    fun removingAFavoriteCanBeUndoneInPlace() = runTest {
        val store = FakeFavoritesStore(url("a"), url("b"), url("c"))
        val viewModel = HomeViewModel(store, FakeSettingsStore())

        viewModel.toggleFavorite(gameFromUrl(url("b"))!!)
        assertEquals(listOf(url("a"), url("c")), store.current)

        viewModel.undoFavorite(viewModel.favoriteNotice.value!!)
        assertEquals(listOf(url("a"), url("b"), url("c")), store.current)
    }
}

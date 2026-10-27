package com.fortind.zenith.games

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class FavoriteEdit(val url: String, val added: Boolean, val index: Int)

data class FavoriteNotice(val title: String, val edit: FavoriteEdit)

interface FavoritesStore {
    val urls: Flow<List<String>>
    suspend fun toggle(url: String): FavoriteEdit
    suspend fun revert(edit: FavoriteEdit)
}

fun toggleFavorite(urls: List<String>, url: String): Pair<List<String>, FavoriteEdit> {
    val index = urls.indexOf(url)
    return if (index >= 0) {
        (urls - url) to FavoriteEdit(url, added = false, index = index)
    } else {
        (listOf(url) + urls) to FavoriteEdit(url, added = true, index = 0)
    }
}

fun revertFavorite(urls: List<String>, edit: FavoriteEdit): List<String> = when {
    edit.added -> urls - edit.url
    edit.url in urls -> urls
    else -> urls.toMutableList().apply { add(edit.index.coerceIn(0, size), edit.url) }
}

class FavoriteEditor(private val store: FavoritesStore, private val scope: CoroutineScope) {
    private val _notice = MutableStateFlow<FavoriteNotice?>(null)
    val notice: StateFlow<FavoriteNotice?> = _notice.asStateFlow()

    fun toggle(game: Game) {
        scope.launch { _notice.value = FavoriteNotice(game.title, store.toggle(game.url)) }
    }

    fun undo(notice: FavoriteNotice) {
        scope.launch { store.revert(notice.edit) }
    }

    fun noticeShown(notice: FavoriteNotice) {
        _notice.compareAndSet(notice, null)
    }
}

private val Context.favoritesDataStore by preferencesDataStore(
    name = "favorites",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreFavoritesStore(context: Context) : FavoritesStore {
    private val dataStore = context.applicationContext.favoritesDataStore

    override val urls: Flow<List<String>> = dataStore.data.map { it.favoriteUrls() }

    override suspend fun toggle(url: String): FavoriteEdit {
        lateinit var edit: FavoriteEdit
        dataStore.edit { prefs ->
            val (updated, change) = toggleFavorite(prefs.favoriteUrls(), url)
            prefs[UrlsKey] = updated.joinToString(SEPARATOR)
            edit = change
        }
        return edit
    }

    override suspend fun revert(edit: FavoriteEdit) {
        dataStore.edit { prefs ->
            prefs[UrlsKey] = revertFavorite(prefs.favoriteUrls(), edit).joinToString(SEPARATOR)
        }
    }

    private fun Preferences.favoriteUrls(): List<String> =
        this[UrlsKey].orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.distinct()

    private companion object {
        const val SEPARATOR = "\n"
        val UrlsKey = stringPreferencesKey("urls")
    }
}

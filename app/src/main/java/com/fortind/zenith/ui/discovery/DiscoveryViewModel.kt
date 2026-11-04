package com.fortind.zenith.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fortind.zenith.ZenithApplication
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.autocomplete.SuggestionsApi
import com.fortind.zenith.discovery.Announcement
import com.fortind.zenith.discovery.DiscoveryApi
import com.fortind.zenith.discovery.DismissedAnnouncementsStore
import com.fortind.zenith.discovery.Hashtag
import com.fortind.zenith.discovery.unreadAnnouncements
import com.fortind.zenith.timeline.NoteUser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchSuggestions(val query: String = "", val tags: List<String> = emptyList(), val people: List<NoteUser> = emptyList())

data class DiscoveryUiState(
    val hashtagStatus: CollectionsStatus = CollectionsStatus.Idle,
    val hashtags: List<Hashtag> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val suggestions: SearchSuggestions = SearchSuggestions(),
    val searchRequested: Boolean = false,
)

class DiscoveryViewModel(
    private val session: UserSessionService,
    private val api: DiscoveryApi,
    private val people: SuggestionsApi,
    private val dismissedStore: DismissedAnnouncementsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val suggestDebounceMs: Long = SUGGEST_DEBOUNCE_MS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow("")
    private var hashtagsReadAt = 0L
    private var announcementsReadAt = 0L
    private var allAnnouncements: List<Announcement> = emptyList()
    private var dismissed: List<String> = emptyList()

    init {
        viewModelScope.launch {
            dismissedStore.dismissed.collect { ids ->
                dismissed = ids
                publishAnnouncements()
            }
        }
        viewModelScope.launch {
            session.credentialsVersion.drop(1).collect {
                announcementsReadAt = 0L
                allAnnouncements = emptyList()
                publishAnnouncements()
                loadAnnouncements()
            }
        }
        viewModelScope.launch { watchQuery() }
    }

    fun requestSearch() {
        _uiState.update { it.copy(searchRequested = true) }
    }

    fun searchRequestHandled() {
        _uiState.update { it.copy(searchRequested = false) }
    }

    fun loadHashtags(force: Boolean = false) {
        val state = _uiState.value
        if (state.hashtagStatus == CollectionsStatus.Loading) return
        val fresh = state.hashtagStatus == CollectionsStatus.Ready && clock() - hashtagsReadAt < HASHTAG_TTL_MS
        if (fresh && !force) return
        _uiState.update { it.copy(hashtagStatus = if (it.hashtags.isEmpty()) CollectionsStatus.Loading else it.hashtagStatus) }
        viewModelScope.launch {
            try {
                val tags = api.popularHashtags()
                hashtagsReadAt = clock()
                _uiState.update { it.copy(hashtagStatus = CollectionsStatus.Ready, hashtags = tags) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                _uiState.update { it.copy(hashtagStatus = if (it.hashtags.isEmpty()) CollectionsStatus.Failed else it.hashtagStatus) }
            }
        }
    }

    fun loadAnnouncements(force: Boolean = false) {
        val age = clock() - announcementsReadAt
        if (announcementsReadAt != 0L && age < if (force) ANNOUNCEMENT_FORCE_MIN_MS else ANNOUNCEMENT_TTL_MS) return
        announcementsReadAt = clock()
        viewModelScope.launch {
            try {
                allAnnouncements = api.announcements(session.token())
                publishAnnouncements()
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                announcementsReadAt = 0L
            }
        }
    }

    fun dismissAnnouncement(id: String) {
        viewModelScope.launch {
            dismissedStore.dismiss(id)
            try {
                session.token()?.let { api.markAnnouncementRead(it, id) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                return@launch
            }
        }
    }

    private fun publishAnnouncements() {
        _uiState.update { it.copy(announcements = unreadAnnouncements(allAnnouncements, dismissed)) }
    }

    fun setQuery(text: String) {
        query.value = text.trim()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watchQuery() {
        query.collectLatest { text ->
            val clean = text.trimStart('#', '@')
            if (clean.length < MIN_SUGGEST_LENGTH) {
                _uiState.update { it.copy(suggestions = SearchSuggestions(text)) }
                return@collectLatest
            }
            delay(suggestDebounceMs)
            val token = session.token()
            val tags = if (text.startsWith("@")) emptyList() else suggest { people.hashtags(token, clean, TAG_SUGGESTIONS) }
            val found = if (text.startsWith("#")) emptyList() else suggest { people.users(token, clean, PEOPLE_SUGGESTIONS) }
            _uiState.update { it.copy(suggestions = SearchSuggestions(text, tags, found)) }
        }
    }

    private suspend fun <T> suggest(request: suspend () -> List<T>): List<T> = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        emptyList()
    }

    companion object {
        const val SUGGEST_DEBOUNCE_MS = 300L
        const val MIN_SUGGEST_LENGTH = 2
        const val TAG_SUGGESTIONS = 5
        const val PEOPLE_SUGGESTIONS = 5
        const val HASHTAG_TTL_MS = 30 * 60 * 1_000L
        const val ANNOUNCEMENT_TTL_MS = 10 * 60 * 1_000L
        const val ANNOUNCEMENT_FORCE_MIN_MS = 60 * 1_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ZenithApplication
                DiscoveryViewModel(
                    session = app.container.sessionService,
                    api = app.container.discoveryApi,
                    people = app.container.suggestionsApi,
                    dismissedStore = app.container.dismissedAnnouncements,
                )
            }
        }
    }
}

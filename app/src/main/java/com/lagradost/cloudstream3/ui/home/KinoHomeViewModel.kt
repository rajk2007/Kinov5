package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.search.KinoSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KinoHomeViewModel : ViewModel() {
    enum class NetworkState { Loading, Online, Slow, Offline }
    private val _trendingMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val trendingMovies: StateFlow<List<MovieResult>> = _trendingMovies.asStateFlow()
    private val _popularMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val popularMovies: StateFlow<List<MovieResult>> = _popularMovies.asStateFlow()
    private val _topRatedMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val topRatedMovies: StateFlow<List<MovieResult>> = _topRatedMovies.asStateFlow()
    private val _nowPlaying = MutableStateFlow<List<MovieResult>>(emptyList())
    val nowPlaying: StateFlow<List<MovieResult>> = _nowPlaying.asStateFlow()
    private val _upcoming = MutableStateFlow<List<MovieResult>>(emptyList())
    val upcoming: StateFlow<List<MovieResult>> = _upcoming.asStateFlow()
    private val _popularTV = MutableStateFlow<List<MovieResult>>(emptyList())
    val popularTV: StateFlow<List<MovieResult>> = _popularTV.asStateFlow()
    private val _topRatedTV = MutableStateFlow<List<MovieResult>>(emptyList())
    val topRatedTV: StateFlow<List<MovieResult>> = _topRatedTV.asStateFlow()
    private val _trendingTv = MutableStateFlow<List<MovieResult>>(emptyList())
    val trendingTv: StateFlow<List<MovieResult>> = _trendingTv.asStateFlow()
    private val _hindiDubbedMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val hindiDubbedMovies: StateFlow<List<MovieResult>> = _hindiDubbedMovies.asStateFlow()
    private val _animeSpotlightTv = MutableStateFlow<List<MovieResult>>(emptyList())
    val animeSpotlightTv: StateFlow<List<MovieResult>> = _animeSpotlightTv.asStateFlow()
    private val _kDramaSpotlightTv = MutableStateFlow<List<MovieResult>>(emptyList())
    val kDramaSpotlightTv: StateFlow<List<MovieResult>> = _kDramaSpotlightTv.asStateFlow()
    private val _hiddenGemsMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val hiddenGemsMovies: StateFlow<List<MovieResult>> = _hiddenGemsMovies.asStateFlow()
    private val _actionAdventureMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val actionAdventureMovies: StateFlow<List<MovieResult>> = _actionAdventureMovies.asStateFlow()
    private val _comedyMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val comedyMovies: StateFlow<List<MovieResult>> = _comedyMovies.asStateFlow()
    private val _thrillerHorrorMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val thrillerHorrorMovies: StateFlow<List<MovieResult>> = _thrillerHorrorMovies.asStateFlow()
    private val _familyKidsMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val familyKidsMovies: StateFlow<List<MovieResult>> = _familyKidsMovies.asStateFlow()
    private val _internationalHitsMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val internationalHitsMovies: StateFlow<List<MovieResult>> = _internationalHitsMovies.asStateFlow()
    private val _trendingAnimeThisWeekTv = MutableStateFlow<List<MovieResult>>(emptyList())
    val trendingAnimeThisWeekTv: StateFlow<List<MovieResult>> = _trendingAnimeThisWeekTv.asStateFlow()
    private val _criticallyAcclaimedMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val criticallyAcclaimedMovies: StateFlow<List<MovieResult>> = _criticallyAcclaimedMovies.asStateFlow()
    private val _popularHindiMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val popularHindiMovies: StateFlow<List<MovieResult>> = _popularHindiMovies.asStateFlow()
    private val _topRatedHindiMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val topRatedHindiMovies: StateFlow<List<MovieResult>> = _topRatedHindiMovies.asStateFlow()
    private val _popularKoreanTv = MutableStateFlow<List<MovieResult>>(emptyList())
    val popularKoreanTv: StateFlow<List<MovieResult>> = _popularKoreanTv.asStateFlow()
    private val _actionAnimeTv = MutableStateFlow<List<MovieResult>>(emptyList())
    val actionAnimeTv: StateFlow<List<MovieResult>> = _actionAnimeTv.asStateFlow()
    private val _liveEvents = MutableStateFlow<Map<String, List<KinoSearchResult>>>(emptyMap())
    val liveEvents: StateFlow<Map<String, List<KinoSearchResult>>> = _liveEvents.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _networkState = MutableStateFlow(NetworkState.Loading)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    init { loadData() }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            _networkState.value = NetworkState.Loading
            try {
                val cncApi = APIHolder.apis.find { it.name.equals("CNC Verse", ignoreCase = true) }
                    ?: error("CNC Verse provider is not installed")
                val response = APIRepository(cncApi).getMainPage(page = 1)
                if (response !is Resource.Success) error("CNC Verse homepage unavailable")
                val rows = response.value.flatMap { page -> page?.list.orEmpty() }.map { item ->
                    MovieResult(
                        id = item.id ?: item.url.hashCode(),
                        title = item.name,
                        poster_path = item.posterUrl,
                        providerUrl = item.url,
                        providerApiName = item.apiName
                    )
                }.distinctBy { it.providerUrl }
                if (rows.isEmpty()) error("CNC Verse returned no homepage items")
                val chunks = rows.chunked(20)
                                    _trendingMovies.value = rows[0]
                    _popularMovies.value = rows[0]
                    _topRatedMovies.value = rows[0]
                    _nowPlaying.value = rows[0]
                    _upcoming.value = rows[0]
                    _popularTV.value = rows[0]
                    _topRatedTV.value = rows[0]
                    _trendingTv.value = rows[0]
                    _hindiDubbedMovies.value = rows[0]
                    _animeSpotlightTv.value = rows[0]
                    _kDramaSpotlightTv.value = rows[0]
                    _hiddenGemsMovies.value = rows[0]
                    _actionAdventureMovies.value = rows[0]
                    _comedyMovies.value = rows[0]
                    _thrillerHorrorMovies.value = rows[0]
                    _familyKidsMovies.value = rows[0]
                    _internationalHitsMovies.value = rows[0]
                    _trendingAnimeThisWeekTv.value = rows[0]
                    _criticallyAcclaimedMovies.value = rows[0]
                    _popularHindiMovies.value = rows[0]
                    _topRatedHindiMovies.value = rows[0]
                    _popularKoreanTv.value = rows[0]
                    _actionAnimeTv.value = rows[0]
                _networkState.value = NetworkState.Online
            } catch (e: Exception) {
                _error.value = e.message ?: "Unable to load CNC Verse homepage"
                _networkState.value = if (isNetworkAvailable()) NetworkState.Slow else NetworkState.Offline
            } finally { _isLoading.value = false }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val context = CloudStreamApp.context ?: return false
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return manager.activeNetwork?.let { manager.getNetworkCapabilities(it) != null } == true
    }

    fun retry() = loadData()

    fun loadLiveEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val api = APIHolder.apis.find { it.name.equals("Cricify", ignoreCase = true) } ?: return@launch
            val terms = listOf("live", "cricket", "football", "basketball")
            val events = terms.flatMap { term ->
                runCatching { (APIRepository(api).search(term, 1) as? Resource.Success)?.value?.items.orEmpty() }.getOrDefault(emptyList())
            }.distinctBy { it.url }.map { item ->
                KinoSearchResult(item.name, item.url, item.apiName, item.posterUrl, item.type, quality = item.quality?.name)
            }
            _liveEvents.value = if (events.isEmpty()) emptyMap() else mapOf("Live Now" to events)
        }
    }
}

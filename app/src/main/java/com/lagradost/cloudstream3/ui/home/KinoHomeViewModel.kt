package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.search.KinoSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class HomeRow(
    val title: String,
    val items: List<MovieResult>
)

class KinoHomeViewModel : ViewModel() {
    enum class NetworkState { Loading, Online, Slow, Offline }
    private val _homeRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val homeRows: StateFlow<List<HomeRow>> = _homeRows.asStateFlow()
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

    init {
        MainActivity.afterPluginsLoadedEvent += ::onPluginsLoaded
        loadData()
    }

    private fun onPluginsLoaded(@Suppress("UNUSED_PARAMETER") force: Boolean) {
        if (_homeRows.value.isEmpty()) retry()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            _networkState.value = NetworkState.Loading
            try {
                var cncApi: MainAPI? = null
                var retries = 0
                while (cncApi == null && retries < 30) {
                    cncApi = APIHolder.apis.firstOrNull { it.name.equals("CNC Verse", ignoreCase = true) }
                        ?: APIHolder.apis.firstOrNull { it.name.contains("CNC Verse", ignoreCase = true) }
                    if (cncApi == null) {
                        delay(500)
                        retries++
                    }
                }
                if (cncApi == null) {
                    _error.value = "CNC Verse provider not loaded."
                    return@launch
                }
                val repo = APIRepository(cncApi)
                val response = repo.getMainPage(page = 1)
                if (response !is Resource.Success) error("CNC Verse homepage unavailable")

                val homePageResponse: List<HomePageResponse?> = response.value
                val rows = homePageResponse.mapNotNull { page ->
                    page?.items?.let { items ->
                        items.map { homePageList: HomePageList ->
                            HomeRow(
                                title = homePageList.name,
                                items = homePageList.list.map { sr: SearchResponse ->
                                    MovieResult(
                                        id = sr.id ?: sr.url.hashCode(),
                                        title = sr.name,
                                        poster_path = sr.posterUrl,
                                        backdrop_path = sr.posterUrl,
                                        providerUrl = sr.url,
                                        providerApiName = sr.apiName
                                    )
                                }.distinctBy { it.providerUrl }
                            )
                        }
                    }
                }.flatten()
                _homeRows.value = rows

                val contentRows = rows.map { it.items }.filter { it.isNotEmpty() }
                if (contentRows.isEmpty()) error("CNC Verse returned no homepage items")
                fun contentRow(index: Int): List<MovieResult> =
                    contentRows.getOrElse(index) { contentRows.first() }

                _trendingMovies.value = contentRow(0)
                _popularMovies.value = contentRow(1)
                _topRatedMovies.value = contentRow(2)
                _nowPlaying.value = contentRow(3)
                _upcoming.value = contentRow(4)
                _popularTV.value = contentRow(5)
                _topRatedTV.value = contentRow(6)
                _trendingTv.value = contentRow(7)
                _hindiDubbedMovies.value = contentRow(8)
                _animeSpotlightTv.value = contentRow(9)
                _kDramaSpotlightTv.value = contentRow(10)
                _hiddenGemsMovies.value = contentRow(11)
                _actionAdventureMovies.value = contentRow(12)
                _comedyMovies.value = contentRow(13)
                _thrillerHorrorMovies.value = contentRow(14)
                _familyKidsMovies.value = contentRow(15)
                _internationalHitsMovies.value = contentRow(16)
                _trendingAnimeThisWeekTv.value = contentRow(17)
                _criticallyAcclaimedMovies.value = contentRow(18)
                _popularHindiMovies.value = contentRow(19)
                _topRatedHindiMovies.value = contentRow(20)
                _popularKoreanTv.value = contentRow(21)
                _actionAnimeTv.value = contentRow(22)
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

    override fun onCleared() {
        MainActivity.afterPluginsLoadedEvent -= ::onPluginsLoaded
        super.onCleared()
    }

    fun loadLiveEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val api = APIHolder.apis.find { it.name.equals("CricifyProvider", ignoreCase = true) } ?: return@launch
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

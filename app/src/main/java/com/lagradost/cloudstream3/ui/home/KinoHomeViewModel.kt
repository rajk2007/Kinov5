package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.api.TMDBApi
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.LoadResponse
import kotlinx.coroutines.Dispatchers
import com.lagradost.cloudstream3.ui.search.KinoSearchResult
import kotlin.math.max
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.io.File

data class HomeCache(
    val trending: List<MovieResult>,
    val popular: List<MovieResult>,
    val topRated: List<MovieResult>,
    val nowPlaying: List<MovieResult>
)

class KinoHomeViewModel : ViewModel() {
    private val tmdbApi = TMDBApi.create()

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
    val liveEvents: StateFlow<Map<String, List<KinoSearchResult>>> = _liveEvents

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { loadData() }

    private fun <T> interleave(list1: List<T>, list2: List<T>): List<T> {
        val result = mutableListOf<T>()
        val maxSize = max(list1.size, list2.size)
        for (i in 0 until maxSize) {
            if (i < list1.size) result.add(list1[i])
            if (i < list2.size) result.add(list2[i])
        }
        return result
    }

    private fun saveCache(data: HomeCache) {
        try {
            val context = CloudStreamApp.context ?: return
            val file = File(context.filesDir, "home_cache.json")
            file.writeText(data.toJson())
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun loadCache(): HomeCache? {
        return try {
            val context = CloudStreamApp.context ?: return null
            val file = File(context.filesDir, "home_cache.json")
            if (file.exists()) {
                tryParseJson<HomeCache>(file.readText())
            } else null
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            // 1. Try to load from cache first (instant offline load)
            val cached = loadCache()
            if (cached != null) {
                _trendingMovies.value = cached.trending
                _popularMovies.value = cached.popular
                _topRatedMovies.value = cached.topRated
                _nowPlaying.value = cached.nowPlaying
            }

            try {
                val standardTrending = try { tmdbApi.getTrending(TMDBApi.API_KEY).results } catch (e: Exception) { emptyList() }
                val hindiTrending = try { tmdbApi.discoverMovie(TMDBApi.API_KEY, withOriginalLanguage = "hi", sortBy = "popularity.desc").results } catch (e: Exception) { emptyList() }

                // Interleave Hindi and Standard, remove duplicates, keep 20
                val mixedTrending = interleave(hindiTrending, standardTrending)
                    .distinctBy { it.id }
                    .take(20)

                val popular = tmdbApi.getPopular(TMDBApi.API_KEY).results
                val topRated = tmdbApi.getTopRated(TMDBApi.API_KEY).results

                val standardNowPlaying = try { tmdbApi.getNowPlaying(TMDBApi.API_KEY).results } catch (e: Exception) { emptyList() }
                val recentHindi = try { tmdbApi.discoverMovie(TMDBApi.API_KEY, withOriginalLanguage = "hi", sortBy = "release_date.desc").results } catch (e: Exception) { emptyList() }

                // Interleave Hindi and Standard, remove duplicates, keep 20
                val mixedNowPlaying = interleave(recentHindi, standardNowPlaying)
                    .distinctBy { it.id }
                    .take(20)

                // 3. Save to cache file
                val cacheData = HomeCache(mixedTrending, popular, topRated, mixedNowPlaying)
                saveCache(cacheData)

                // 4. Update UI with fresh data
                _trendingMovies.value = mixedTrending
                _popularMovies.value = popular
                _topRatedMovies.value = topRated
                _nowPlaying.value = mixedNowPlaying

                _upcoming.value = tmdbApi.getUpcoming(TMDBApi.API_KEY).results
                _popularTV.value = tmdbApi.getPopularTV(TMDBApi.API_KEY).results
                _topRatedTV.value = tmdbApi.getTopRatedTV(TMDBApi.API_KEY).results
                _trendingTv.value = tmdbApi.getTrendingTv(TMDBApi.API_KEY).results
                _hindiDubbedMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, withOriginalLanguage = "hi").results
                _animeSpotlightTv.value = tmdbApi.discoverTv(TMDBApi.API_KEY, withGenres = "16").results
                _kDramaSpotlightTv.value = tmdbApi.discoverTv(TMDBApi.API_KEY, withOriginalLanguage = "ko").results
                _hiddenGemsMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, sortBy = "vote_average.desc", voteCountGte = 200).results
                _actionAdventureMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, withGenres = "28").results
                _comedyMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, withGenres = "35").results
                _thrillerHorrorMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, withGenres = "27").results
                _familyKidsMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, withGenres = "10751").results
                _internationalHitsMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, withOriginalLanguage = "ja").results
                _trendingAnimeThisWeekTv.value = tmdbApi.getTrendingTv(TMDBApi.API_KEY).results
                _criticallyAcclaimedMovies.value = tmdbApi.getTopRated(TMDBApi.API_KEY).results
                _popularHindiMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, withOriginalLanguage = "hi", sortBy = "popularity.desc").results
                _topRatedHindiMovies.value = tmdbApi.discoverMovie(TMDBApi.API_KEY, withOriginalLanguage = "hi", sortBy = "vote_average.desc").results
                _popularKoreanTv.value = tmdbApi.discoverTv(TMDBApi.API_KEY, withOriginalLanguage = "ko", sortBy = "popularity.desc").results
                _actionAnimeTv.value = tmdbApi.discoverTv(TMDBApi.API_KEY, withGenres = "16,10759").results
            } catch (e: Exception) {
                logError(e)
                // If network fails and no cache, show error
                if (cached == null) {
                    _error.value = "No internet connection"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() { loadData() }

    fun loadLiveEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val cricifyApi = APIHolder.apis.find { it.name.lowercase().contains("cricify") }
            if (cricifyApi != null) {
                try {
                    val repo = APIRepository(cricifyApi)
                    val liveMap = mutableMapOf<String, MutableList<KinoSearchResult>>()

                    // Define sports and their search terms
                    val sports = mapOf(
                        "Cricket" to listOf("cricket", "ipl", "bbl", "psl"),
                        "Football" to listOf("football", "soccer", "epl", "la liga"),
                        "Basketball" to listOf("basketball", "nba"),
                        "Tennis" to listOf("tennis", "atp", "wta"),
                        "Live Now" to listOf("live") // Catch-all for other live events
                    )

                    sports.forEach { (sportName, terms) ->
                        val sportList = mutableListOf<KinoSearchResult>()
                        terms.forEach { term ->
                            try {
                                val resource = repo.search(term, page = 1)
                                if (resource is Resource.Success) {
                                    resource.value.items.forEach { response ->
                                        if (sportList.none { it.url == response.url }) {
                                            sportList.add(
                                                KinoSearchResult(
                                                    name = response.name,
                                                    url = response.url,
                                                    apiName = response.apiName,
                                                    posterUrl = response.posterUrl,
                                                    type = response.type,
                                                    year = null,
                                                    quality = response.quality?.name
                                                )
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        if (sportList.isNotEmpty()) {
                            liveMap[sportName] = sportList
                        }
                    }

                    _liveEvents.value = liveMap
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

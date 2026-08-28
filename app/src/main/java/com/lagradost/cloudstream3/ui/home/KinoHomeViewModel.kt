package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.net.ConnectivityManager
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.CloudStreamApp
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

class KinoHomeViewModel : ViewModel() {
    enum class NetworkState { Loading, Online, Slow, Offline }

    private val tmdbApi = TMDBApi.create()
    private val cachePreferences by lazy {
        CloudStreamApp.context?.getSharedPreferences("kino_home_cache", Context.MODE_PRIVATE)
    }

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

    private val _networkState = MutableStateFlow(NetworkState.Loading)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

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

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _networkState.value = NetworkState.Loading
            try {
                val standardTrending = try { tmdbApi.getTrending(TMDBApi.API_KEY).results } catch (e: Exception) { emptyList() }
                val hindiTrending = try { tmdbApi.discoverMovie(TMDBApi.API_KEY, withOriginalLanguage = "hi", sortBy = "popularity.desc").results } catch (e: Exception) { emptyList() }

                // Interleave Hindi and Standard, remove duplicates, keep 20
                val mixedTrending = interleave(hindiTrending, standardTrending)
                    .distinctBy { it.id }
                    .take(20)
                _trendingMovies.value = mixedTrending

                _popularMovies.value = tmdbApi.getPopular(TMDBApi.API_KEY).results
                _topRatedMovies.value = tmdbApi.getTopRated(TMDBApi.API_KEY).results

                val standardNowPlaying = try { tmdbApi.getNowPlaying(TMDBApi.API_KEY).results } catch (e: Exception) { emptyList() }
                val recentHindi = try { tmdbApi.discoverMovie(TMDBApi.API_KEY, withOriginalLanguage = "hi", sortBy = "release_date.desc").results } catch (e: Exception) { emptyList() }

                // Interleave Hindi and Standard, remove duplicates, keep 20
                val mixedNowPlaying = interleave(recentHindi, standardNowPlaying)
                    .distinctBy { it.id }
                    .take(20)
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
                saveHomeCache()
                _networkState.value = NetworkState.Online
                linkMovieBoxResults()
            } catch (e: Exception) {
                logError(e)
                _error.value = e.message ?: "Unknown error"
                restoreHomeCache()
                _networkState.value = if (isNetworkAvailable()) NetworkState.Slow else NetworkState.Offline
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun currentHomeLists(): Map<String, List<MovieResult>> = linkedMapOf(
        "trending" to _trendingMovies.value,
        "popular" to _popularMovies.value,
        "topRated" to _topRatedMovies.value,
        "nowPlaying" to _nowPlaying.value,
        "upcoming" to _upcoming.value,
        "popularTV" to _popularTV.value,
        "topRatedTV" to _topRatedTV.value,
        "trendingTv" to _trendingTv.value,
        "hindiDubbedMovies" to _hindiDubbedMovies.value,
        "animeSpotlightTv" to _animeSpotlightTv.value,
        "kDramaSpotlightTv" to _kDramaSpotlightTv.value,
        "hiddenGemsMovies" to _hiddenGemsMovies.value,
        "actionAdventureMovies" to _actionAdventureMovies.value,
        "comedyMovies" to _comedyMovies.value,
        "thrillerHorrorMovies" to _thrillerHorrorMovies.value,
        "familyKidsMovies" to _familyKidsMovies.value,
        "internationalHitsMovies" to _internationalHitsMovies.value,
        "trendingAnimeThisWeekTv" to _trendingAnimeThisWeekTv.value,
        "criticallyAcclaimedMovies" to _criticallyAcclaimedMovies.value,
        "popularHindiMovies" to _popularHindiMovies.value,
        "topRatedHindiMovies" to _topRatedHindiMovies.value,
        "popularKoreanTv" to _popularKoreanTv.value,
        "actionAnimeTv" to _actionAnimeTv.value
    )

    private fun movieToJson(movie: MovieResult): JSONObject = JSONObject().apply {
        put("id", movie.id)
        put("title", movie.title)
        put("name", movie.name)
        put("poster_path", movie.poster_path)
        put("backdrop_path", movie.backdrop_path)
        put("overview", movie.overview)
        put("vote_average", movie.vote_average)
        put("release_date", movie.release_date)
        put("first_air_date", movie.first_air_date)
        put("media_type", movie.media_type)
        put("genre_ids", JSONArray(movie.genre_ids ?: emptyList<Int>()))
        put("providerUrl", movie.providerUrl)
        put("providerApiName", movie.providerApiName)
    }

    private fun jsonToMovie(json: JSONObject): MovieResult = MovieResult(
        id = json.optInt("id"),
        title = json.optString("title").takeIf { it.isNotEmpty() },
        name = json.optString("name").takeIf { it.isNotEmpty() },
        poster_path = json.optString("poster_path").takeIf { it.isNotEmpty() },
        backdrop_path = json.optString("backdrop_path").takeIf { it.isNotEmpty() },
        overview = json.optString("overview").takeIf { it.isNotEmpty() },
        vote_average = if (json.isNull("vote_average")) null else json.optDouble("vote_average"),
        release_date = json.optString("release_date").takeIf { it.isNotEmpty() },
        first_air_date = json.optString("first_air_date").takeIf { it.isNotEmpty() },
        media_type = json.optString("media_type").takeIf { it.isNotEmpty() },
        genre_ids = json.optJSONArray("genre_ids")?.let { array ->
            List(array.length()) { index -> array.optInt(index) }
        },
        providerUrl = json.optString("providerUrl").takeIf { it.isNotEmpty() },
        providerApiName = json.optString("providerApiName").takeIf { it.isNotEmpty() }
    )

    private fun saveHomeCache() {
        val root = JSONObject()
        currentHomeLists().forEach { (key, movies) ->
            root.put(key, JSONArray().apply { movies.forEach { put(movieToJson(it)) } })
        }
        cachePreferences?.edit()?.putString("home_data", root.toString())?.apply()
    }

    private fun restoreHomeCache() {
        val root = cachePreferences?.getString("home_data", null)?.let {
            runCatching { JSONObject(it) }.getOrNull()
        } ?: return

        fun restore(key: String, setter: (List<MovieResult>) -> Unit) {
            val array = root.optJSONArray(key) ?: return
            setter(List(array.length()) { index -> jsonToMovie(array.getJSONObject(index)) })
        }
        restore("trending") { _trendingMovies.value = it }
        restore("popular") { _popularMovies.value = it }
        restore("topRated") { _topRatedMovies.value = it }
        restore("nowPlaying") { _nowPlaying.value = it }
        restore("upcoming") { _upcoming.value = it }
        restore("popularTV") { _popularTV.value = it }
        restore("topRatedTV") { _topRatedTV.value = it }
        restore("trendingTv") { _trendingTv.value = it }
        restore("hindiDubbedMovies") { _hindiDubbedMovies.value = it }
        restore("animeSpotlightTv") { _animeSpotlightTv.value = it }
        restore("kDramaSpotlightTv") { _kDramaSpotlightTv.value = it }
        restore("hiddenGemsMovies") { _hiddenGemsMovies.value = it }
        restore("actionAdventureMovies") { _actionAdventureMovies.value = it }
        restore("comedyMovies") { _comedyMovies.value = it }
        restore("thrillerHorrorMovies") { _thrillerHorrorMovies.value = it }
        restore("familyKidsMovies") { _familyKidsMovies.value = it }
        restore("internationalHitsMovies") { _internationalHitsMovies.value = it }
        restore("trendingAnimeThisWeekTv") { _trendingAnimeThisWeekTv.value = it }
        restore("criticallyAcclaimedMovies") { _criticallyAcclaimedMovies.value = it }
        restore("popularHindiMovies") { _popularHindiMovies.value = it }
        restore("topRatedHindiMovies") { _topRatedHindiMovies.value = it }
        restore("popularKoreanTv") { _popularKoreanTv.value = it }
        restore("actionAnimeTv") { _actionAnimeTv.value = it }
    }

    private fun isNetworkAvailable(): Boolean {
        val context = CloudStreamApp.context ?: return false
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return manager.activeNetwork?.let { manager.getNetworkCapabilities(it) != null } == true
    }

    private fun movieBoxMatchScore(movie: MovieResult, candidateName: String): Int? {
        val movieTitle = movie.displayTitle().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        val candidateTitle = candidateName.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        if (movieTitle.isBlank() || candidateTitle.isBlank()) return null

        val movieYear = movie.release_date?.take(4)?.toIntOrNull()
        val candidateHasYear = movieYear?.toString()?.let { candidateTitle.contains(it) } == true
        val movieWords = movieTitle.split(" ").filter { it.length > 1 }.toSet()
        val candidateWords = candidateTitle.split(" ").filter { it.length > 1 }.toSet()
        val overlap = movieWords.intersect(candidateWords).size
        val containsTitle = candidateTitle.contains(movieTitle) || movieTitle.contains(candidateTitle)
        val minimumOverlap = if (movieWords.size <= 2) movieWords.size else 2
        if (!containsTitle && overlap < minimumOverlap) return null

        return (if (containsTitle) 100 else 0) + overlap * 10 + if (candidateHasYear) 20 else 0
    }

    private fun linkMovieBoxResults() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val movieBoxApi = APIHolder.apis.find { it.name.equals("MovieBox", ignoreCase = true) }
                    ?: return@launch
                val repo = APIRepository(movieBoxApi)

                suspend fun linkMovies(movies: List<MovieResult>): List<MovieResult> {
                    return movies.mapIndexed { index, movie ->
                        if (index >= 10) return@mapIndexed movie
                        try {
                            val searchRes = repo.search(movie.displayTitle(), page = 1)
                            if (searchRes is Resource.Success) {
                                val match = searchRes.value.items
                                    .take(5)
                                    .mapNotNull { candidate ->
                                        movieBoxMatchScore(movie, candidate.name)?.let { score -> score to candidate }
                                    }
                                    .maxByOrNull { it.first }
                                    ?.second
                                if (match != null) {
                                    movie.copy(providerUrl = match.url, providerApiName = match.apiName)
                                } else movie
                            } else movie
                        } catch (e: Exception) {
                            movie
                        }
                    }
                }

                _trendingMovies.value = linkMovies(_trendingMovies.value)
                _popularMovies.value = linkMovies(_popularMovies.value)
                _topRatedMovies.value = linkMovies(_topRatedMovies.value)
            } catch (e: Exception) {
                logError(e)
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

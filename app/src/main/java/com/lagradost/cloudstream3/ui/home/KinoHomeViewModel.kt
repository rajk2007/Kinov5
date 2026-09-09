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
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.SearchResponse
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
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            _networkState.value = NetworkState.Loading
            try {
                var istreamFlareApi: com.lagradost.cloudstream3.MainAPI? = null
                repeat(60) {
                    if (istreamFlareApi == null) {
                        istreamFlareApi = APIHolder.apis.firstOrNull {
                            it.name.contains("IStreamFlare", ignoreCase = true) ||
                                it.name.contains("IStream Flare", ignoreCase = true) ||
                                it.name.contains("IStreamplay", ignoreCase = true)
                        }
                        if (istreamFlareApi == null) kotlinx.coroutines.delay(500)
                    }
                }
                if (istreamFlareApi == null) {
                    _error.value = "IStreamFlare provider not loaded. Available: ${APIHolder.apis.map { it.name }}"
                    _networkState.value = if (isNetworkAvailable()) NetworkState.Slow else NetworkState.Offline
                    return@launch
                }
                val response = APIRepository(istreamFlareApi!!).getMainPage(page = 1)
                if (response !is Resource.Success) error("IStreamFlare homepage unavailable")
                val rows = response.value.flatMap { it?.items.orEmpty() }.map { homePageList: HomePageList ->
                    HomeRow(
                        title = homePageList.name,
                        items = homePageList.list.map { sr: SearchResponse ->
                            MovieResult(
                                id = sr.id ?: 0,
                                title = sr.name,
                                poster_path = sr.posterUrl,
                                backdrop_path = sr.posterUrl,
                                providerUrl = sr.url,
                                providerApiName = sr.apiName
                            )
                        }
                    )
                }
                _homeRows.value = rows
                if (rows.isEmpty() || rows.none { it.items.isNotEmpty() }) error("IStreamFlare returned no homepage items")
                _trendingMovies.value = rows.firstOrNull()?.items?.take(7).orEmpty()
                fun sectionItems(vararg keywords: String): List<MovieResult> = rows
                    .filter { row -> keywords.any { row.title.contains(it, ignoreCase = true) } }
                    .flatMap { it.items }
                _popularMovies.value = sectionItems("Popular", "Trending")
                _topRatedMovies.value = sectionItems("Top Rated", "Best")
                _nowPlaying.value = sectionItems("New", "Recent", "Latest")
                _upcoming.value = sectionItems("Upcoming", "Soon")
                _popularTV.value = sectionItems("TV", "Series")
                _trendingTv.value = sectionItems("Trending")
                _animeSpotlightTv.value = sectionItems("Anime", "Animation")
                _actionAdventureMovies.value = sectionItems("Action", "Adventure")
                _comedyMovies.value = sectionItems("Comedy")
                _thrillerHorrorMovies.value = sectionItems("Thriller", "Horror")
                _familyKidsMovies.value = sectionItems("Family", "Kids")
                _networkState.value = NetworkState.Online
                withContext(Dispatchers.IO) { saveHomeCache() }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unable to load IStreamFlare homepage"
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

    fun retry() { loadData() }

}

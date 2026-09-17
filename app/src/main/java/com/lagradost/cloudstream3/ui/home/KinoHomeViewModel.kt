package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Presentation categories are intentionally independent of provider row names. */
enum class HomeSectionType {
    NEW_NETFLIX, LATEST_HOTSTAR, TOP_NETFLIX_SERIES, TOP_PRIME_MOVIES, TOP_PRIME_SERIES,
    K_DRAMAS, KOREAN, COMEDY_MOVIES, SCI_FI_FILMS, HORROR_FILMS, CROWD_PLEASERS,
    US_TV_SHOWS, HOTSTAR_SPECIALS, PRIME_ORIGINALS, HORROR_STORIES
}

data class HomeRow(val title: String, val items: List<MovieResult>, val sectionType: HomeSectionType, val isPersonalized: Boolean = false)
data class HeroBannerItem(val movie: MovieResult, val backdropUrl: String?, val title: String, val year: String?, val rating: String?, val genre: String?)

private data class ProviderHomeContent(val api: MainAPI, val sections: List<Pair<String, List<MovieResult>>>) {
    val allItems: List<MovieResult> get() = sections.flatMap { it.second }.distinctBy { "${it.providerApiName}:${it.providerUrl ?: it.id}" }
}

class KinoHomeViewModel : ViewModel() {
    enum class NetworkState { Loading, Online, Slow, Offline }

    private val _homeRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val homeRows: StateFlow<List<HomeRow>> = _homeRows.asStateFlow()
    private val _heroBannerItems = MutableStateFlow<List<HeroBannerItem>>(emptyList())
    val heroBannerItems: StateFlow<List<HeroBannerItem>> = _heroBannerItems.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _networkState = MutableStateFlow(NetworkState.Loading)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    init { loadData() }
    fun retry() = loadData()

    private fun loadData() = viewModelScope.launch(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        _networkState.value = NetworkState.Loading
        try {
            val providers = waitForProviders()
            Log.d("KINO_HOME", "Providers loaded: ${providers.map { it.name }}")
            val content = providers.map { api -> async { fetchProviderContent(api) } }.awaitAll()
            val netflix = content.firstOrNull { it.api.name.contains("Netflix", true) }
            val prime = content.firstOrNull { it.api.name.contains("PrimeVideo", true) || it.api.name.contains("Prime Video", true) }
            val hotstar = content.firstOrNull { it.api.name.contains("Hotstar", true) }
            val rows = buildHomeRows(netflix, prime, hotstar)
            Log.d("KINO_HOME", "Content counts: ${content.associate { it.api.name to it.allItems.size }}")
            if (rows.none { it.items.isNotEmpty() }) {
                _error.value = "No content available from Netflix, Prime Video, or Hotstar"
            }
            _homeRows.value = rows
            _heroBannerItems.value = rows.take(2).flatMap { it.items }.distinctBy { it.displayTitle() }.take(7).map(::hero)
            _networkState.value = NetworkState.Online
        } catch (error: Throwable) {
            _error.value = error.message ?: "Unable to load content"
            _networkState.value = if (isNetworkAvailable()) NetworkState.Slow else NetworkState.Offline
        } finally { _isLoading.value = false }
    }

    private suspend fun waitForProviders(): List<MainAPI> {
        repeat(60) {
            val providers = APIHolder.apis.toList()
            val wanted = providers.filter { api ->
                api.name.contains("Netflix", true) || api.name.contains("PrimeVideo", true) ||
                    api.name.contains("Prime Video", true) || api.name.contains("Hotstar", true)
            }.distinctBy { it.name }
            if (wanted.isNotEmpty()) return wanted
            delay(500)
        }
        return APIHolder.apis.filter { api ->
            api.name.contains("Netflix", true) || api.name.contains("Prime", true) || api.name.contains("Hotstar", true)
        }.distinctBy { it.name }
    }

    private suspend fun fetchProviderContent(api: MainAPI): ProviderHomeContent = try {
        when (val response = APIRepository(api).getMainPage(1)) {
            is Resource.Success -> ProviderHomeContent(api, response.value.orEmpty().flatMap { page ->
                page?.items.orEmpty().map { list: HomePageList ->
                    list.name to list.list.map { it.toMovieResult(api) }
                }
            })
            else -> ProviderHomeContent(api, emptyList())
        }
    } catch (error: Exception) {
        Log.e("KINO_HOME", "${api.name} homepage failed: ${error.message}")
        ProviderHomeContent(api, emptyList())
    }

    private fun SearchResponse.toMovieResult(api: MainAPI) = MovieResult(
        id = id ?: url.hashCode(), title = name, poster_path = posterUrl, backdrop_path = posterUrl,
        providerUrl = url, providerApiName = apiName.ifBlank { api.name },
        media_type = if (type == TvType.TvSeries) "tv" else "movie", vote_average = score?.toDouble()
    )

    private fun buildHomeRows(netflix: ProviderHomeContent?, prime: ProviderHomeContent?, hotstar: ProviderHomeContent?): List<HomeRow> {
        fun pick(provider: ProviderHomeContent?, vararg words: String): List<MovieResult> {
            if (provider == null) return emptyList()
            val matched = provider.sections.filter { section -> words.any { section.first.contains(it, true) } }.flatMap { it.second }
            return (matched.ifEmpty { provider.allItems }).distinctBy { "${it.providerApiName}:${it.providerUrl ?: it.id}" }.take(20)
        }
        return listOf(
            HomeRow("New on Netflix", pick(netflix, "new", "latest", "release", "recent"), HomeSectionType.NEW_NETFLIX),
            HomeRow("Latest Releases", pick(hotstar, "new", "latest", "release", "recent"), HomeSectionType.LATEST_HOTSTAR),
            HomeRow("Top 10 Series in Netflix Today", pick(netflix, "top 10", "series"), HomeSectionType.TOP_NETFLIX_SERIES),
            HomeRow("Top 10 Movies in Prime Video", pick(prime, "top 10", "movie"), HomeSectionType.TOP_PRIME_MOVIES),
            HomeRow("Top 10 Series in Prime Video", pick(prime, "top 10", "series"), HomeSectionType.TOP_PRIME_SERIES),
            HomeRow("K-Dramas", pick(netflix, "k-drama", "korean", "korea"), HomeSectionType.K_DRAMAS),
            HomeRow("Korean", pick(hotstar, "korean", "korea"), HomeSectionType.KOREAN),
            HomeRow("Comedy Movies", pick(hotstar, "comedy"), HomeSectionType.COMEDY_MOVIES),
            HomeRow("Sci-Fi Films", pick(prime, "sci-fi", "science fiction"), HomeSectionType.SCI_FI_FILMS),
            HomeRow("Horror Films", pick(prime, "horror"), HomeSectionType.HORROR_FILMS),
            HomeRow("Crowd Pleasers", pick(netflix, "crowd", "popular", "pleaser"), HomeSectionType.CROWD_PLEASERS),
            HomeRow("US TV Shows", pick(netflix, "us tv", "american", "show"), HomeSectionType.US_TV_SHOWS),
            HomeRow("Hotstar Specials", pick(hotstar, "special", "original"), HomeSectionType.HOTSTAR_SPECIALS),
            HomeRow("Featured Originals: Series", pick(prime, "original", "featured", "series"), HomeSectionType.PRIME_ORIGINALS),
            HomeRow("Horror Stories", pick(hotstar, "horror", "scary"), HomeSectionType.HORROR_STORIES)
        )
    }

    private fun hero(movie: MovieResult) = HeroBannerItem(
        movie = movie, backdropUrl = movie.backdrop_path ?: movie.poster_path, title = movie.displayTitle(),
        year = movie.release_date?.take(4) ?: movie.first_air_date?.take(4), rating = movie.vote_average?.let { "%.1f".format(it) }, genre = null
    )

    private fun isNetworkAvailable(): Boolean {
        val context = CloudStreamApp.context ?: return false
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return manager.activeNetwork?.let { manager.getNetworkCapabilities(it) != null } == true
    }
}

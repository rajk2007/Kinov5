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

data class HomeRow(
    val title: String,
    val items: List<MovieResult>,
    val sectionType: HomeSectionType,
    val isPersonalized: Boolean = false
)

data class HeroBannerItem(
    val movie: MovieResult,
    val backdropUrl: String?,
    val title: String,
    val year: String?,
    val rating: String?,
    val genre: String?
)

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

    private fun isBingeCloud(api: MainAPI): Boolean =
        api.name.contains("BingeCloud", true) || api.name.contains("Binge Cloud", true)

    private fun loadData() = viewModelScope.launch(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        _networkState.value = NetworkState.Loading
        try {
            var bingeCloudApi: MainAPI? = APIHolder.apis.firstOrNull(::isBingeCloud)
            repeat(60) {
                if (bingeCloudApi != null) return@repeat
                delay(500)
                bingeCloudApi = APIHolder.apis.firstOrNull(::isBingeCloud)
            }

            Log.d("KINO_HOME", "BingeCloud API: ${bingeCloudApi?.name ?: "NOT FOUND"}")
            if (bingeCloudApi == null) {
                _error.value = "BingeCloud provider not loaded."
                _networkState.value = if (isNetworkAvailable()) NetworkState.Slow else NetworkState.Offline
                return@launch
            }

            val sections = fetchProviderSections(bingeCloudApi!!)
            val rows = buildHomeRowsFromBingeCloud(sections)
            val allItems = rows.flatMap { it.items }.distinctBy { itemKey(it) }
            Log.d("KINO_HOME", "BingeCloud sections: ${sections.keys}; items: ${allItems.size}")
            if (allItems.isEmpty()) {
                _error.value = "No content available from BingeCloud"
            }
            _homeRows.value = rows
            _heroBannerItems.value = prepareHeroBanner(allItems)
            _networkState.value = NetworkState.Online
        } catch (error: Throwable) {
            _error.value = error.message ?: "Unable to load content"
            _networkState.value = if (isNetworkAvailable()) NetworkState.Slow else NetworkState.Offline
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchProviderSections(api: MainAPI): Map<String, List<MovieResult>> {
        val sections = linkedMapOf<String, List<MovieResult>>()
        try {
            when (val response = APIRepository(api).getMainPage(page = 1)) {
                is Resource.Success -> response.value.orEmpty().flatMap { it?.items.orEmpty() }
                    .forEach { page: HomePageList ->
                        val items = page.list.map { it.toMovieResult(api) }
                        if (items.isNotEmpty()) {
                            sections[page.name] = items.distinctBy(::itemKey)
                            Log.d("KINO_HOME", "BingeCloud section '${page.name}': ${items.size} items")
                        }
                    }
                else -> Log.e("KINO_HOME", "BingeCloud homepage request failed")
            }
        } catch (error: Exception) {
            Log.e("KINO_HOME", "BingeCloud homepage exception: ${error.message}", error)
        }
        return sections
    }

    private fun SearchResponse.toMovieResult(api: MainAPI) = MovieResult(
        id = id ?: url.hashCode(),
        title = name,
        poster_path = posterUrl,
        backdrop_path = posterUrl,
        providerUrl = url,
        providerApiName = apiName.ifBlank { api.name },
        media_type = if (type == TvType.TvSeries) "tv" else "movie",
        vote_average = score?.toDouble()
    )

    private fun itemKey(item: MovieResult): String =
        "${item.providerApiName}:${item.providerUrl ?: item.id}"

    private fun buildHomeRowsFromBingeCloud(
        sections: Map<String, List<MovieResult>>
    ): List<HomeRow> = sections.map { (name, items) ->
        val lower = name.lowercase()
        val type = when {
            "trending" in lower || "popular" in lower -> HomeSectionType.CROWD_PLEASERS
            "netflix" in lower || "new" in lower || "latest" in lower -> HomeSectionType.NEW_NETFLIX
            "prime" in lower -> HomeSectionType.TOP_PRIME_MOVIES
            "korean" in lower || "k-drama" in lower -> HomeSectionType.KOREAN
            "comedy" in lower -> HomeSectionType.COMEDY_MOVIES
            "sci-fi" in lower || "science fiction" in lower -> HomeSectionType.SCI_FI_FILMS
            "horror" in lower -> HomeSectionType.HORROR_FILMS
            else -> HomeSectionType.CROWD_PLEASERS
        }
        HomeRow(name, items.take(20), type)
    }.filter { it.items.isNotEmpty() }

    private fun prepareHeroBanner(content: List<MovieResult>): List<HeroBannerItem> =
        content.take(6).map { movie ->
            HeroBannerItem(
                movie = movie,
                backdropUrl = movie.backdrop_path ?: movie.poster_path,
                title = movie.displayTitle(),
                year = movie.release_date?.take(4) ?: movie.first_air_date?.take(4),
                rating = movie.vote_average?.toString(),
                genre = null
            )
        }

    private fun isNetworkAvailable(): Boolean {
        val context = CloudStreamApp.context ?: return false
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return manager.activeNetwork?.let { manager.getNetworkCapabilities(it) != null } == true
    }
}

package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.net.ConnectivityManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The semantic type keeps presentation independent from provider row names. */
enum class HomeSectionType(val displayName: String, val priority: Int) {
    TRENDING_NOW("Trending Now", 1), TOP_10_TODAY("Top 10 Today", 2), NEW_RELEASES("New Releases", 3),
    PSYCHOLOGICAL("Psychological", 4), MIND_BENDING("Mind-Bending", 5), COMEDY_PICKS("Comedy Picks", 6),
    ROMANCE_PICKS("Romance Picks", 7), ACTION_HITS("Action Hits", 8), CRIME_MYSTERY("Crime & Mystery", 9),
    HORROR_AFTER_DARK("Horror After Dark", 10), EMOTIONAL_PICKS("Emotional Picks", 11),
    ANIME_SPOTLIGHT("Anime Spotlight", 12), KDRAMA_FAVORITES("K-Drama Favorites", 13),
    INDIAN_HITS("Indian Hits", 14), HIDDEN_GEMS("Hidden Gems", 15), BINGE_WORTHY("Binge-Worthy", 16),
    KINO_RECOMMENDS("Kino Recommends", 17)
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

    init {
        loadData()
    }

    fun retry() = loadData()

    private fun loadData() = viewModelScope.launch(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        _networkState.value = NetworkState.Loading
        try {
            val providers = waitForProviders()
            val content = providers.map { api -> async { api to fetchProviderContent(api) } }.awaitAll().toMap()
            val netflix = providers.firstOrNull { it.name.contains("Netflix", true) }?.let(content::get).orEmpty()
            val prime = providers.firstOrNull {
                it.name.contains("PrimeVideo", true) || it.name.contains("Prime Video", true)
            }?.let(content::get).orEmpty()
            val hotstar = providers.firstOrNull { it.name.contains("Hotstar", true) }?.let(content::get).orEmpty()
            if (netflix.isEmpty()) error("Netflix returned no homepage items")

            val sections = buildSections(netflix, prime, hotstar)
            _homeRows.value = orderSections(sections)
            val releases = sections[HomeSectionType.NEW_RELEASES].orEmpty()
            _heroBannerItems.value = releases.take(7).map { hero(it) }
            _networkState.value = NetworkState.Online
        } catch (t: Throwable) {
            _error.value = t.message ?: "Unable to load content"
            _networkState.value = if (isNetworkAvailable()) NetworkState.Slow else NetworkState.Offline
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun waitForProviders(): List<MainAPI> {
        repeat(60) {
            val found = APIHolder.apis.filter { api ->
                api.name.contains("Netflix", true) || api.name.contains("PrimeVideo", true) ||
                    api.name.contains("Prime Video", true) || api.name.contains("Hotstar", true)
            }
            val netflix = found.firstOrNull { it.name.contains("Netflix", true) }
            val prime = found.firstOrNull {
                it.name.contains("PrimeVideo", true) || it.name.contains("Prime Video", true)
            }
            val hotstar = found.firstOrNull { it.name.contains("Hotstar", true) }
            if (netflix != null && prime != null && hotstar != null) {
                return listOf(netflix, prime, hotstar).distinct()
            }
            delay(500)
        }
        return APIHolder.apis.filter {
            it.name.contains("Netflix", true) || it.name.contains("PrimeVideo", true) ||
                it.name.contains("Prime Video", true) || it.name.contains("Hotstar", true)
        }.distinct()
    }

    private suspend fun fetchProviderContent(api: MainAPI): List<MovieResult> = runCatching {
        val response = APIRepository(api).getMainPage(page = 1)
        if (response !is Resource.Success) return emptyList()
        response.value.orEmpty().flatMap { it?.items.orEmpty() }.flatMap(HomePageList::list)
            .map { it.toMovieResult(api) }
    }.getOrDefault(emptyList())

    private fun SearchResponse.toMovieResult(api: MainAPI) = MovieResult(
        id = id ?: url.hashCode(), title = name, poster_path = posterUrl, backdrop_path = posterUrl,
        providerUrl = url, providerApiName = apiName.ifBlank { api.name },
        media_type = if (type == TvType.TvSeries) "tv" else "movie",
        vote_average = score?.toDouble()
    )

    private fun buildSections(
        netflix: List<MovieResult>,
        prime: List<MovieResult>,
        hotstar: List<MovieResult>
    ): Map<HomeSectionType, List<MovieResult>> {
        val uniqueNetflix = netflix.distinctBy { "${it.providerApiName}:${it.providerUrl ?: it.id}" }
        val uniquePrime = prime.distinctBy { "${it.providerApiName}:${it.providerUrl ?: it.id}" }
        val uniqueHotstar = hotstar.distinctBy { "${it.providerApiName}:${it.providerUrl ?: it.id}" }
        val general = uniqueNetflix + uniquePrime
        val all = general + uniqueHotstar
        val sections = HomeSectionType.entries.associateWith { mutableListOf<MovieResult>() }
        fun add(type: HomeSectionType, movie: MovieResult) { sections.getValue(type).add(movie) }
        fun textOf(movie: MovieResult): String = listOfNotNull(movie.title, movie.name).joinToString(" ").lowercase()
        fun matches(movie: MovieResult, keywords: Set<String>): Boolean {
            val text = textOf(movie)
            return keywords.any(text::contains)
        }
        val psychological = setOf("psychological", "mind game", "mental", "character study", "mental health")
        val mindBending = setOf("mind-bending", "mind bending", "plot twist", "time loop", "alternate reality", "unreliable narrator", "reality manipulation", "twist ending", "complex narrative", "science fiction", "sci-fi", "inception")
        val romance = setOf("romance", "romantic", "rom-com", "love story", "relationship", "love triangle")
        val romanceExcludes = setOf("horror", "thriller", "sci-fi", "science fiction", "monster", "alien")
        val action = setOf("action", "adventure", "superhero", "martial arts", "spy", "mission", "combat", "fight")
        val crime = setOf("crime", "detective", "murder", "mystery", "investigation", "police", "whodunit", "heist", "criminal", "forensic")
        val horror = setOf("horror", "supernatural", "paranormal", "slasher", "haunted", "ghost", "demon", "zombie", "creature")
        val horrorExcludes = setOf("cartoon", "animation", "anime", "kids", "children", "family", "shinchan")
        val comedy = setOf("comedy", "sitcom", "funny", "humor")
        val indian = setOf("indian", "bollywood", "hindi", "tamil", "telugu", "bengali", "malayalam", "kannada", "marathi", "punjabi")
        val korean = setOf("korean", "k-drama", "k drama", "korea")
        val emotional = setOf("emotional", "tearjerker", "family drama", "inspirational", "heartwarming", "coming of age")
        all.forEach { movie ->
            if (matches(movie, psychological)) add(HomeSectionType.PSYCHOLOGICAL, movie)
            if (matches(movie, mindBending)) add(HomeSectionType.MIND_BENDING, movie)
            if (matches(movie, romance) && !matches(movie, romanceExcludes)) add(HomeSectionType.ROMANCE_PICKS, movie)
            if (matches(movie, action)) add(HomeSectionType.ACTION_HITS, movie)
            if (matches(movie, crime)) add(HomeSectionType.CRIME_MYSTERY, movie)
            if (matches(movie, horror) && !matches(movie, horrorExcludes)) add(HomeSectionType.HORROR_AFTER_DARK, movie)
            if (matches(movie, comedy)) add(HomeSectionType.COMEDY_PICKS, movie)
            if (matches(movie, indian)) add(HomeSectionType.INDIAN_HITS, movie)
            if (matches(movie, korean) && !matches(movie, indian)) add(HomeSectionType.KDRAMA_FAVORITES, movie)
            if (matches(movie, emotional)) add(HomeSectionType.EMOTIONAL_PICKS, movie)
            if (movie.media_type == "tv" || textOf(movie).hasAny("series", "season")) add(HomeSectionType.BINGE_WORTHY, movie)
            if (movie.vote_average != null && movie.vote_average >= 8.0) add(HomeSectionType.HIDDEN_GEMS, movie)
        }
        uniqueNetflix.take(20).forEach { add(HomeSectionType.TRENDING_NOW, it) }
        uniqueNetflix.filter(::isRecentlyReleased).take(20).forEach { add(HomeSectionType.NEW_RELEASES, it) }
        uniqueHotstar.take(20).forEach { add(HomeSectionType.INDIAN_HITS, it) }
        all.sortedByDescending { it.vote_average ?: 0.0 }.take(10).forEach { add(HomeSectionType.TOP_10_TODAY, it) }
        all.sortedByDescending { it.vote_average ?: 0.0 }.take(20).forEach { add(HomeSectionType.KINO_RECOMMENDS, it) }
        return sections.mapValues { (_, values) -> values.distinctBy { "${it.providerApiName}:${it.providerUrl ?: it.id}" }.take(20) }
    }

    private fun orderSections(sections: Map<HomeSectionType, List<MovieResult>>): List<HomeRow> {
        val history = (DataStoreHelper.getAllBookmarkedData() + DataStoreHelper.getAllFavorites())
            .flatMap { listOf(it.name.lowercase(), it.type?.name?.lowercase().orEmpty()) }
        val preference = sections.keys.associateWith { type ->
            history.count { it.isNotBlank() && type.displayName.lowercase().split(' ').any(it::contains) }
        }
        val fixed = listOf(HomeSectionType.TRENDING_NOW, HomeSectionType.TOP_10_TODAY, HomeSectionType.NEW_RELEASES)
        val rest = sections.keys.filterNot(fixed::contains).sortedWith(compareByDescending<HomeSectionType> { preference[it] ?: 0 }.thenBy { it.priority })
        return (fixed + rest).mapNotNull { type -> sections[type]?.takeIf { it.isNotEmpty() }?.let { HomeRow(type.displayName, it, type, (preference[type] ?: 0) > 0) } }
    }

    private fun hero(movie: MovieResult): HeroBannerItem {
        val title = movie.displayTitle()
        val lower = title.lowercase()
        val genre = listOf(
            "Action" to listOf("action", "hero", "war"), "Comedy" to listOf("comedy", "funny"),
            "Romance" to listOf("romance", "love"), "Crime" to listOf("crime", "detective"),
            "Horror" to listOf("horror", "haunt", "dead"), "Thriller" to listOf("thriller", "mystery"),
            "Drama" to listOf("drama", "family")
        ).firstOrNull { (_, words) -> words.any(lower::contains) }?.first ?: "Featured"
        return HeroBannerItem(
            movie = movie,
            backdropUrl = movie.backdrop_path ?: movie.poster_path,
            title = title,
            year = Regex("\\b(19|20)\\d{2}\\b").find(title)?.value,
            rating = movie.vote_average?.let { "%.1f".format(it) },
            genre = genre
        )
    }
    private fun String.hasAny(vararg values: String) = values.any(::contains)
    private fun isRecentlyReleased(movie: MovieResult): Boolean {
        val date = movie.release_date ?: movie.first_air_date ?: return false
        val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull()
        val cutoff = Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.time
        return parsed?.let { !it.before(cutoff) } ?: date.take(4).toIntOrNull()?.let {
            it == Calendar.getInstance().get(Calendar.YEAR)
        } == true
    }
    private fun isNetworkAvailable(): Boolean {
        val context = CloudStreamApp.context ?: return false
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return manager.activeNetwork?.let { manager.getNetworkCapabilities(it) != null } == true
    }
}

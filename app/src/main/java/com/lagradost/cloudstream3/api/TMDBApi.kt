package com.lagradost.cloudstream3.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import retrofit2.http.GET
import retrofit2.http.Query

interface TMDBApi {
    @GET("trending/movie/week")
    suspend fun getTrending(@Query("api_key") apiKey: String): TMDBResponse

    @GET("movie/popular")
    suspend fun getPopular(@Query("api_key") apiKey: String): TMDBResponse

    @GET("movie/top_rated")
    suspend fun getTopRated(@Query("api_key") apiKey: String): TMDBResponse

    @GET("movie/now_playing")
    suspend fun getNowPlaying(@Query("api_key") apiKey: String): TMDBResponse

    @GET("movie/upcoming")
    suspend fun getUpcoming(@Query("api_key") apiKey: String): TMDBResponse

    @GET("tv/popular")
    suspend fun getPopularTV(@Query("api_key") apiKey: String): TMDBResponse

    @GET("tv/top_rated")
    suspend fun getTopRatedTV(@Query("api_key") apiKey: String): TMDBResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String
    ): TMDBResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTv(@Query("api_key") apiKey: String): TMDBResponse

    @GET("discover/movie")
    suspend fun discoverMovie(
        @Query("api_key") apiKey: String,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_original_language") withOriginalLanguage: String? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("vote_count.gte") voteCountGte: Int? = null
    ): TMDBResponse

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("api_key") apiKey: String,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_original_language") withOriginalLanguage: String? = null,
        @Query("sort_by") sortBy: String? = null
    ): TMDBResponse

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
        const val API_KEY = "cf5a2b948bb3cbe03332dc70594b4ba7"

        fun create(): TMDBApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TMDBApi::class.java)
        }
    }
}

data class TMDBResponse(val results: List<MovieResult>)

data class MovieResult(
    val id: Int,
    val title: String? = null,
    val name: String? = null, // For TV shows
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val overview: String? = null,
    val vote_average: Double? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val media_type: String? = null
) {
    fun displayTitle(): String = title ?: name ?: "Unknown"
}

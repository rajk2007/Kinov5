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
    val title: String,
    val poster_path: String?,
    val overview: String?,
    val vote_average: Double?
)

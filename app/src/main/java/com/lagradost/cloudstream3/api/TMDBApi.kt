package com.lagradost.cloudstream3.api

import com.lagradost.cloudstream3.mvvm.log

import retrofit2.http.GET

import retrofit2.http.Query

interface TMDBApi {
    @GET("trending/movie/week")
    suspend fun getTrending(
        @Query("api_key") apiKey: String
    ): TMDBResponse

    @GET("movie/popular")
    suspend fun getPopular(
        @Query("api_key") apiKey: String
    ): TMDBResponse

    @GET("movie/top_rated")
    suspend fun getTopRated(
        @Query("api_key") apiKey: String
    ): TMDBResponse
}

data class TMDBResponse(
    val results: List<MovieResult>
)

data class MovieResult(
    val id: Int,
    val title: String,
    val poster_path: String?,
    val overview: String?,
    val vote_average: Double?
)
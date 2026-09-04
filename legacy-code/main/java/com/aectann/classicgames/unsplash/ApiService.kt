package com.aectann.classicgames.unsplash

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("photos/random")
    suspend fun getRandomPhoto(
        @Query("query") query: String
    ): Response<UnsplashResponse>

}
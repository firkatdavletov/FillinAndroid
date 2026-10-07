package com.firkat.fillin.feature.categories.data.remote

import com.firkat.fillin.feature.categories.data.remote.dto.CategoriesResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface CategoriesApi {
    @Headers("Accept: application/json")
    @GET("api/v1/categories")
    suspend fun getCategories(
        @Header("Authorization") authorization: String,
    ): CategoriesResponseDto
}

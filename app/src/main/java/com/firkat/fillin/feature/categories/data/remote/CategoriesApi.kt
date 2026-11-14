package com.firkat.fillin.feature.categories.data.remote

import com.firkat.fillin.feature.categories.data.remote.dto.CategoriesResponseDto
import com.firkat.fillin.feature.categories.data.remote.dto.CategoryDto
import com.firkat.fillin.feature.categories.data.remote.dto.CreateCategoryRequestDto
import com.firkat.fillin.feature.categories.data.remote.dto.UpdateCategoryRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface CategoriesApi {
    @Headers("Accept: application/json")
    @GET("api/v1/categories")
    suspend fun getCategories(
        @Header("Authorization") authorization: String
    ): CategoriesResponseDto

    @Headers("Accept: application/json")
    @POST("api/v1/categories")
    suspend fun createCategory(
        @Header("Authorization") authorization: String,
        @Body request: CreateCategoryRequestDto
    ): CategoryDto

    @Headers("Accept: application/json")
    @PATCH("api/v1/categories/{categoryId}")
    suspend fun updateCategory(
        @Header("Authorization") authorization: String,
        @Path("categoryId") categoryId: String,
        @Body request: UpdateCategoryRequestDto
    ): CategoryDto

    @DELETE("api/v1/categories/{categoryId}")
    suspend fun deleteCategory(
        @Header("Authorization") authorization: String,
        @Path("categoryId") categoryId: String
    )
}

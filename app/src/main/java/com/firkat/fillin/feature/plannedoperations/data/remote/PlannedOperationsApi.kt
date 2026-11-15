package com.firkat.fillin.feature.plannedoperations.data.remote

import com.firkat.fillin.feature.plannedoperations.data.remote.dto.PlannedOperationsResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface PlannedOperationsApi {
    @GET("api/v1/planned-operations")
    suspend fun getPlannedOperations(
        @Header("Authorization") authorization: String,
        @Query("type") type: String?,
        @Query("period") period: String,
        @Query("dateFrom") dateFrom: String?,
        @Query("dateTo") dateTo: String?
    ): PlannedOperationsResponseDto
}

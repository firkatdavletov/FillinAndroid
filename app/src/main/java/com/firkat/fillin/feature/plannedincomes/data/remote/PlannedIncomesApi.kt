package com.firkat.fillin.feature.plannedincomes.data.remote

import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomesResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface PlannedIncomesApi {
    @Headers("Accept: application/json")
    @GET("api/v1/planned-incomes")
    suspend fun getPlannedIncomes(
        @Header("Authorization") authorization: String,
    ): PlannedIncomesResponseDto
}

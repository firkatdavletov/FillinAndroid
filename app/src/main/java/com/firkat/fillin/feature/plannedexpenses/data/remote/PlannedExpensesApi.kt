package com.firkat.fillin.feature.plannedexpenses.data.remote

import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpensesResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface PlannedExpensesApi {
    @Headers("Accept: application/json")
    @GET("api/v1/planned-expenses")
    suspend fun getPlannedExpenses(
        @Header("Authorization") authorization: String,
    ): PlannedExpensesResponseDto
}

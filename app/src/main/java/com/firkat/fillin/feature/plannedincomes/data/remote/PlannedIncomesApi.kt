package com.firkat.fillin.feature.plannedincomes.data.remote

import com.firkat.fillin.feature.plannedincomes.data.remote.dto.CreatePlannedIncomeRequestDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomesResponseDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.UpdatePlannedIncomeRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlannedIncomesApi {
    @Headers("Accept: application/json")
    @GET("api/v1/planned-incomes")
    suspend fun getPlannedIncomes(
        @Header("Authorization") authorization: String
    ): PlannedIncomesResponseDto

    @Headers("Accept: application/json")
    @POST("api/v1/planned-incomes")
    suspend fun createPlannedIncome(
        @Header("Authorization") authorization: String,
        @Body request: CreatePlannedIncomeRequestDto
    ): Response<PlannedIncomeDto>

    @Headers("Accept: application/json")
    @PATCH("api/v1/planned-incomes/{incomeId}")
    suspend fun updatePlannedIncome(
        @Header("Authorization") authorization: String,
        @Path("incomeId") incomeId: String,
        @Body request: UpdatePlannedIncomeRequestDto
    ): Response<PlannedIncomeDto>

    @DELETE("api/v1/planned-incomes/{incomeId}")
    suspend fun deletePlannedIncome(
        @Header("Authorization") authorization: String,
        @Path("incomeId") incomeId: String,
        @Query("version") version: Long
    ): Response<Unit>
}

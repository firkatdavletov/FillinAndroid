package com.firkat.fillin.feature.plannedexpenses.data.remote

import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.ClosePlannedExpenseRequestDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.CreatePlannedExpenseRuleRequestDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseRuleDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpensesResponseDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.UpdatePlannedExpenseRuleRequestDto
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

interface PlannedExpensesApi {
    @Headers("Accept: application/json")
    @GET("api/v1/planned-expenses")
    suspend fun getPlannedExpenses(
        @Header("Authorization") authorization: String
    ): PlannedExpensesResponseDto

    @Headers("Accept: application/json")
    @POST("api/v1/planned-expenses")
    suspend fun createRule(
        @Header("Authorization") authorization: String,
        @Body request: CreatePlannedExpenseRuleRequestDto
    ): Response<PlannedExpenseRuleDto>

    @Headers("Accept: application/json")
    @PATCH("api/v1/planned-expenses/{ruleId}")
    suspend fun updateRule(
        @Header("Authorization") authorization: String,
        @Path("ruleId") ruleId: String,
        @Body request: UpdatePlannedExpenseRuleRequestDto
    ): Response<PlannedExpenseRuleDto>

    @DELETE("api/v1/planned-expenses/{ruleId}")
    suspend fun deleteRule(
        @Header("Authorization") authorization: String,
        @Path("ruleId") ruleId: String,
        @Query("version") version: Long
    ): Response<Unit>

    @Headers("Accept: application/json")
    @POST("api/v1/planned-expenses/{expenseId}/close")
    suspend fun closeExpense(
        @Header("Authorization") authorization: String,
        @Path("expenseId") expenseId: String,
        @Body request: ClosePlannedExpenseRequestDto
    ): Response<PlannedExpenseDto>
}

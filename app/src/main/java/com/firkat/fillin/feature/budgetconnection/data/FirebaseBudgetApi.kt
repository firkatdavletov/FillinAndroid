package com.firkat.fillin.feature.budgetconnection.data

import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.sync.data.*
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class AvailableBudgetDto(val id: String, val name: String, val memberId: String, val role: String)

@Serializable
data class CreateFirebaseBudgetDto(val requestId: String, val name: String, val openingBalance: String, val accountingStartedOn: String, val currency: String = "KZT")

@Serializable
data class ImportFirebaseBudgetDto(val name: String, val budget: CreateCloudBudget)

interface FirebaseBudgetApi {
    @POST("api/v4/budgets/import")
    suspend fun importLocal(@Header("Authorization") authorization: String, @Body request: ImportFirebaseBudgetDto): Response<AvailableBudgetDto>

    @POST("api/v4/budgets")
    suspend fun create(@Header("Authorization") authorization: String, @Body request: CreateFirebaseBudgetDto): Response<AvailableBudgetDto>

    @GET("api/v4/budgets")
    suspend fun budgets(@Header("Authorization") authorization: String): Response<List<AvailableBudgetDto>>

    @GET("api/v4/budgets/{budgetId}")
    suspend fun budget(@Header("Authorization") authorization: String, @Path("budgetId") budgetId: String): Response<CloudBudget>

    @GET("api/v4/budgets/{budgetId}/sync/changes")
    suspend fun changes(@Header("Authorization") authorization: String, @Path("budgetId") budgetId: String, @Query("cursor") cursor: String?): Response<SyncPageDto>

    @POST("api/v4/budgets/{budgetId}/sync/commands")
    suspend fun command(@Header("Authorization") authorization: String, @Path("budgetId") budgetId: String, @Body command: BudgetSyncCommand): Response<BudgetCommandReceipt>
}

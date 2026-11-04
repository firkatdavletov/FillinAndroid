package com.firkat.fillin.feature.sync.data

import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncScopeDto
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

object BudgetCapabilities {
    const val CONFIRMED_PLAN_LINKING = "confirmedPlanLinkingV1"
    const val INCOME_RULE_EDITING = "incomeRuleEditingV1"
    const val DAILY_LIMIT_PLANNING = "dailyLimitPlanningV1"
    const val CLIENT_HEADER = "X-Fillin-Capabilities: $CONFIRMED_PLAN_LINKING,$INCOME_RULE_EDITING,$DAILY_LIMIT_PLANNING"
}

@Serializable
data class CloudBudget(
    val scope: SyncScopeDto,
    val capabilities: Set<String> = emptySet(),
)

@Serializable
data class CloudSeedCategory(
    val id: String,
    val name: String,
    val kind: String,
    val icon: String,
    val dictionaryKey: String,
)

@Serializable
data class CreateCloudBudget(
    val requestId: String,
    val householdId: String,
    val memberId: String,
    val openingBalance: String,
    val accountingStartedOn: String,
    val categories: List<CloudSeedCategory>,
)

interface CloudBudgetApi {
    @Headers(BudgetCapabilities.CLIENT_HEADER)
    @GET("api/v4/budget")
    suspend fun budget(
        @Header("Authorization") authorization: String,
    ): Response<CloudBudget>

    @Headers(BudgetCapabilities.CLIENT_HEADER)
    @POST("api/v4/legacy/budgets")
    suspend fun create(
        @Header("Authorization") authorization: String,
        @Body request: CreateCloudBudget,
    ): Response<CloudBudget>

    @Headers(BudgetCapabilities.CLIENT_HEADER)
    @GET("api/v4/sync/changes")
    suspend fun changes(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: String?,
    ): SyncPageDto

    @Headers(BudgetCapabilities.CLIENT_HEADER)
    @POST("api/v4/sync/commands")
    suspend fun command(
        @Header("Authorization") authorization: String,
        @Body command: BudgetSyncCommand,
    ): Response<BudgetCommandReceipt>
}

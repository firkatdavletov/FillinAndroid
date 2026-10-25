package com.firkat.fillin.feature.sync.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class SyncPlanRecord(
    val id: String,
    val categoryId: String? = null,
    val ruleId: String? = null,
    val ledgerId: String? = null,
    val amount: String? = null,
    val frequency: String? = null,
    val scheduleValue: Int? = null,
    val startsOn: String? = null,
    val active: Boolean? = null,
    val dueOn: String? = null,
    val status: String? = null,
    val version: Long? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
)

@Serializable
data class BudgetSyncCommand(
    val requestId: String,
    val householdId: String,
    val memberId: String,
    val type: String,
    val entityId: String,
    val authoredOn: String,
    val expectedToken: Long? = null,
    val categoryId: String? = null,
    val name: String? = null,
    val kind: String? = null,
    val icon: String? = null,
    val amount: String? = null,
    val frequency: String? = null,
    val scheduleValue: Int? = null,
    val startsOn: String? = null,
    val dueOn: String? = null,
    val ruleId: String? = null,
    val occurredOn: String? = null,
    val description: String? = null,
    val refundId: String? = null,
    val replaceDetails: Boolean = false,
)

@Serializable
data class BudgetCommandReceipt(
    val requestId: String,
    val revision: Long,
    val entityToken: Long? = null,
)

interface BudgetSyncApi {
    @POST("api/v2/sync/commands")
    suspend fun command(
        @Header("Authorization") authorization: String,
        @Body command: BudgetSyncCommand,
    ): Response<BudgetCommandReceipt>
}

internal fun BudgetSyncCommand.entityKind(): String =
    when {
        type in setOf("CREATE_OPERATION", "UPDATE_OPERATION", "REFUND_OPERATION") -> "LEDGER"
        type.endsWith("CATEGORY") -> "CATEGORY"
        type.endsWith("RULE") -> "RECURRING_RULE"
        type.endsWith("INCOME") -> "EXPECTED_INCOME"
        type == "CLOSE_EXPENSE" -> "RECURRING_OCCURRENCE"
        else -> error("Unknown command")
    }

@Serializable
data class SyncBudgetSettings(
    val id: String,
    val openingBalance: String,
    val protectedReserve: String,
)

@Serializable
data class SyncSavingsGoal(
    val id: String,
    val allocatedAmount: String,
)

@Serializable
data class SyncVariableSpendingExclusion(
    val id: String,
)

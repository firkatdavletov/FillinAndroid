package com.firkat.fillin.feature.operationhistory.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class PlanLinkTarget(
    val type: String,
    val planId: String? = null,
    val ruleId: String? = null,
    val dueOn: String,
    val scheduledOn: String? = null,
) {
    val stableKey: String get() = if (type == "EXPENSE") "$ruleId@$dueOn" else requireNotNull(planId)
}

@Serializable
data class SuggestedPlan(
    val target: PlanLinkTarget,
    val categoryId: String,
    val categoryName: String,
    val plannedAmount: String,
    val remainingAmount: String,
    val version: Long,
)

data class CreateOperationResult(val operationId: String, val suggestedPlan: SuggestedPlan? = null)
data class OperationPlanLinks(val candidates: List<SuggestedPlan> = emptyList(), val current: List<SuggestedPlan> = emptyList())

interface OperationPlanLinkStore {
    fun observeLinks(operationId: String): Flow<OperationPlanLinks>
    val pendingProposal: Flow<String?>
    suspend fun proposal(operationId: String): SuggestedPlan?
    suspend fun selectProposal(operationId: String, plan: SuggestedPlan)
    suspend fun dismissProposal(operationId: String)
    suspend fun setLink(operationId: String, plan: SuggestedPlan, operationVersion: Long)
    suspend fun removeLink(operationId: String, operationVersion: Long)
}

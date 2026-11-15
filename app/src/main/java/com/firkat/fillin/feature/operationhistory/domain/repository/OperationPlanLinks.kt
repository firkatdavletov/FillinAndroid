package com.firkat.fillin.feature.operationhistory.domain.repository

import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class PlanLinkTarget(
    val type: String,
    val planId: String? = null,
    val ruleId: String? = null,
    val dueOn: String,
    val scheduledOn: String? = null
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
    val name: String? = null,
    val frequency: PlanningFrequency = PlanningFrequency.Once,
    val currency: String? = null
) {
    val displayName: String get() = name?.takeIf(String::isNotBlank) ?: categoryName
}

data class CreateOperationResult(val operationId: String)
data class OperationPlanLinks(
    val candidates: List<SuggestedPlan> = emptyList(),
    val current: List<SuggestedPlan> = emptyList()
)

@Serializable
sealed interface PlanSuggestion {
    @Serializable data object None : PlanSuggestion

    @Serializable data class Recommended(val plan: SuggestedPlan) : PlanSuggestion

    @Serializable data class Choice(val plans: List<SuggestedPlan>, val hasMore: Boolean) : PlanSuggestion
}

@Serializable
data class PlanLinkOperation(
    val version: Long,
    val type: String,
    val categoryId: String,
    val categoryName: String,
    val categoryIconCode: String,
    val currency: String,
    val occurredOn: String,
    val amount: String,
    val netAmount: String
) {
    fun matches(
        other: PlanLinkOperation
    ): Boolean = version == other.version &&
        type == other.type &&
        categoryId == other.categoryId &&
        currency == other.currency &&
        occurredOn == other.occurredOn &&
        amount.toBigDecimal().compareTo(other.amount.toBigDecimal()) == 0 &&
        netAmount.toBigDecimal().compareTo(other.netAmount.toBigDecimal()) == 0
}

fun SuggestedPlan.matches(
    other: SuggestedPlan
): Boolean = target.type == other.target.type &&
    target.stableKey == other.target.stableKey &&
    target.dueOn == other.target.dueOn &&
    target.scheduledOn == other.target.scheduledOn &&
    version == other.version &&
    categoryId == other.categoryId &&
    currency == other.currency &&
    displayName == other.displayName &&
    frequency == other.frequency &&
    plannedAmount.toBigDecimal().compareTo(other.plannedAmount.toBigDecimal()) == 0 &&
    remainingAmount.toBigDecimal().compareTo(other.remainingAmount.toBigDecimal()) == 0

@Serializable
data class OperationPlanOffer(val operation: PlanLinkOperation, val suggestion: PlanSuggestion)

data class OperationPlanLinkContext(
    val operation: PlanLinkOperation? = null,
    val links: OperationPlanLinks = OperationPlanLinks(),
    val isLinked: Boolean = false
)

interface OperationPlanLinkStore {
    fun observeLinks(
        operationId: String
    ): Flow<OperationPlanLinks>
    suspend fun setLink(
        operationId: String,
        plan: SuggestedPlan,
        operationVersion: Long
    )
    suspend fun removeLink(
        operationId: String,
        operationVersion: Long
    )
}

interface OperationPlanOfferStore {
    suspend fun prepareOffer(
        operationId: String
    ): Boolean
    suspend fun proposal(
        operationId: String
    ): OperationPlanOffer?
    fun observeOfferContext(
        operationId: String
    ): Flow<OperationPlanLinkContext>
    suspend fun confirmOffer(
        operationId: String,
        operation: PlanLinkOperation,
        plan: SuggestedPlan
    )
    suspend fun dismissProposal(
        operationId: String
    )
}

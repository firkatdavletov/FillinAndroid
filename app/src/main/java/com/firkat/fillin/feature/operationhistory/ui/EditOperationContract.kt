package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinks
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError

enum class EditOperationMode { Edit, Correct, ReadOnly }

data class EditOperationUiState(
    val window: OperationDetailsWindow? = null,
    val dirty: Boolean = false,
    val links: OperationPlanLinks = OperationPlanLinks(),
    val planError: Boolean = false,
    val operation: Operation? = null,
    val isLoading: Boolean = true,
    val mode: EditOperationMode = EditOperationMode.ReadOnly,
    val input: String = "",
    val description: String = "",
    val descriptionError: Boolean = false,
    val type: OperationType = OperationType.Expense,
    val categoryId: String? = null,
    val categories: List<Category> = emptyList(),
    val inputError: OperationInputError? = null,
    val error: OperationHistoryError? = null,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val canRefund: Boolean = false,
    val isRefundConfirmationVisible: Boolean = false,
)

enum class OperationDetailsWindow { Category, Discard, Unlink }

sealed interface EditOperationEvent {
    data class WindowChanged(val window: OperationDetailsWindow?) : EditOperationEvent
    data object DiscardConfirmed : EditOperationEvent
    data object UnlinkConfirmed : EditOperationEvent
    data class PlanSelected(val plan: SuggestedPlan) : EditOperationEvent
    data class OpenPlan(val plan: SuggestedPlan) : EditOperationEvent
    data class InputChanged(val value: String) : EditOperationEvent
    data class DescriptionChanged(val value: String) : EditOperationEvent
    data class TypeChanged(val type: OperationType) : EditOperationEvent
    data class CategorySelected(val id: String) : EditOperationEvent
    data object SaveClicked : EditOperationEvent
    data object RefundClicked : EditOperationEvent
    data object RefundConfirmed : EditOperationEvent
    data object RefundDismissed : EditOperationEvent
    data object CloseClicked : EditOperationEvent
}

enum class EditOperationOutcome { Updated, Corrected, Refunded }

/** Identifies the source record, including when correction replaces it with a new request. */
data class EditOperationResult(val operationId: String, val outcome: EditOperationOutcome)

sealed interface EditOperationEffect {
    data object OpenLinkConfirmation : EditOperationEffect
    data class OpenPlan(val type: PlannedOperationType, val ruleId: String) : EditOperationEffect
    data object Close : EditOperationEffect
    data class Saved(val result: EditOperationResult) : EditOperationEffect
}

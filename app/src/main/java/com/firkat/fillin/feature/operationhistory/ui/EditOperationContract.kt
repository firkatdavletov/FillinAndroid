package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError

enum class EditOperationMode { Edit, Correct, ReadOnly }

data class EditOperationUiState(
    val operation: Operation? = null,
    val isLoading: Boolean = true,
    val mode: EditOperationMode = EditOperationMode.ReadOnly,
    val input: String = "",
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

sealed interface EditOperationEvent {
    data class InputChanged(val value: String) : EditOperationEvent
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
    data object Close : EditOperationEffect
    data class Saved(val result: EditOperationResult) : EditOperationEffect
}

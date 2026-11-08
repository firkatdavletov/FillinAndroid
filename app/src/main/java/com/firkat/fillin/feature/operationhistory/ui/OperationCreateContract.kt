package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError
import java.time.LocalDate

data class OperationCreateUiState(
    val type: OperationType = OperationType.Expense,
    val input: String = "",
    val occurredOn: LocalDate,
    val isSaving: Boolean = false,
    val frequentlyUsed: List<Category> = emptyList(),
    val recentlyUsed: List<Category> = emptyList(),
    val error: OperationHistoryError? = null,
    val inputError: OperationInputError? = null,
    val feedback: OperationCreateFeedback? = null,
    val feedbackSequence: Long = 0,
)

enum class OperationCreateFeedback { InvalidInput, InvalidCategory, Unavailable, SaveFailed }

data class OperationCreateState(val createEditor: OperationCreateUiState?, val offline: OfflineHistoryContext = OfflineHistoryContext(), val created: com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult? = null)

sealed interface OperationCreateEvent {
    data object CreateClosed : OperationCreateEvent

    data class CreateTypeChanged(
        val type: OperationType,
    ) : OperationCreateEvent

    data class CreateInputChanged(
        val value: String,
    ) : OperationCreateEvent

    data class CreateCategoryClicked(
        val id: String,
    ) : OperationCreateEvent

    data class CreateFeedbackShown(
        val sequence: Long,
    ) : OperationCreateEvent

}

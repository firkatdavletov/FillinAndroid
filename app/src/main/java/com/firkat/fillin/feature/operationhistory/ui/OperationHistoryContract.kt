package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

data class OperationHistoryUiState(
    val operations: List<Operation> = emptyList(),
    val filters: OperationHistoryFilter = OperationHistoryFilter.currentMonth(LocalDate.now(ZoneId.of("Asia/Almaty"))),
    val periodTotals: OperationPeriodTotals? = null,
    val isPeriodFilterVisible: Boolean = false,
    val isCustomPeriod: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: OperationHistoryError? = null,
    val editor: PurchaseEditorUiState? = null,
    val offline: OfflineHistoryContext = OfflineHistoryContext(),
    val createEditor: OperationCreateEditorUiState? = null,
)

data class PurchaseEditorUiState(
    val original: Operation,
    val amount: String = original.originalAmount.toPlainString(),
    val isSaving: Boolean = false,
    val error: OperationHistoryError? = null,
    val isRefundConfirmationVisible: Boolean = false,
    val isOnline: Boolean = true,
) {
    val parsedAmount: BigDecimal?
        get() {
            val normalized = amount.trim().replace(',', '.')
            if (!DECIMAL_AMOUNT.matches(normalized)) return null
            return normalized.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
        }

    val canSave: Boolean
        get() =
            isOnline && !isSaving &&
                original.type == OperationType.Expense &&
                original.status == OperationStatus.Posted &&
                parsedAmount?.let { it.compareTo(original.originalAmount) != 0 } == true

    val canRefund: Boolean
        get() = isOnline && !isSaving && original.status == OperationStatus.Posted && original.type == OperationType.Expense
}

data class OperationCreateEditorUiState(
    val type: OperationType = OperationType.Expense,
    val amount: String = "",
    val categoryId: String? = null,
    val occurredOn: LocalDate,
    val description: String = "",
    val replacesRejectedId: String? = null,
    val isSaving: Boolean = false,
    val error: OperationHistoryError? = null,
) {
    val parsedAmount: BigDecimal? get() =
        amount
            .trim()
            .replace(',', '.')
            .takeIf(DECIMAL_AMOUNT::matches)
            ?.toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 && it <= BigDecimal("99999999999999999.99") }
    val canSave: Boolean get() = !isSaving && parsedAmount != null && categoryId != null && description.length <= 160
}

enum class OperationHistoryError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    InvalidRequest,
    NotFound,
    Conflict,
    Network,
    InvalidResponse,
    Unknown,
}

sealed interface OperationHistoryEvent {
    data object CreateRequested : OperationHistoryEvent

    data class CorrectRejectedRequested(
        val id: String,
    ) : OperationHistoryEvent

    data object CreateClosed : OperationHistoryEvent

    data class CreateTypeChanged(
        val type: OperationType,
    ) : OperationHistoryEvent

    data class CreateAmountChanged(
        val value: String,
    ) : OperationHistoryEvent

    data class CreateCategoryChanged(
        val id: String,
    ) : OperationHistoryEvent

    data class CreateDateChanged(
        val date: LocalDate,
    ) : OperationHistoryEvent

    data class CreateDescriptionChanged(
        val value: String,
    ) : OperationHistoryEvent

    data object CreateSaved : OperationHistoryEvent

    data class TypeSelected(
        val type: OperationType?,
    ) : OperationHistoryEvent

    data object PeriodFilterOpened : OperationHistoryEvent

    data object PeriodFilterClosed : OperationHistoryEvent

    data object PeriodFilterReset : OperationHistoryEvent

    data class PeriodSelected(
        val dateFrom: LocalDate,
        val dateTo: LocalDate,
    ) : OperationHistoryEvent

    data object Refresh : OperationHistoryEvent

    data object LoadNextPage : OperationHistoryEvent

    data object Retry : OperationHistoryEvent

    data class EditRequested(
        val purchaseId: String,
    ) : OperationHistoryEvent

    data object EditorClosed : OperationHistoryEvent

    data class AmountChanged(
        val amount: String,
    ) : OperationHistoryEvent

    data object SaveRequested : OperationHistoryEvent

    data object RefundRequested : OperationHistoryEvent

    data object RefundDismissed : OperationHistoryEvent

    data object RefundConfirmed : OperationHistoryEvent
}

private val DECIMAL_AMOUNT = Regex("[0-9]+([.,][0-9]{1,2})?")

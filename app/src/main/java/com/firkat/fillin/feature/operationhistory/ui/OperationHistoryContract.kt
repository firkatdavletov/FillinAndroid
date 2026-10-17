package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

data class OperationHistoryUiState(
    val operations: List<Operation> = emptyList(),
    val filters: OperationHistoryFilter = OperationHistoryFilter.currentMonth(LocalDate.now(ZoneId.of("Asia/Almaty"))),
    val periodTotals: OperationPeriodTotals? = null,
    val isTotalsLoading: Boolean = false,
    val totalsError: OperationHistoryError? = null,
    val isPeriodFilterVisible: Boolean = false,
    val isCustomPeriod: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: OperationHistoryError? = null,
    val editor: PurchaseEditorUiState? = null,
)

data class PurchaseEditorUiState(
    val original: Operation,
    val amount: String = original.originalAmount.toPlainString(),
    val isSaving: Boolean = false,
    val error: OperationHistoryError? = null,
    val isRefundConfirmationVisible: Boolean = false,
) {
    val parsedAmount: BigDecimal?
        get() {
            val normalized = amount.trim().replace(',', '.')
            if (!DECIMAL_AMOUNT.matches(normalized)) return null
            return normalized.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
        }

    val canSave: Boolean
        get() = !isSaving &&
            original.type == OperationType.Expense &&
            original.status == OperationStatus.Posted &&
            parsedAmount?.let { it.compareTo(original.originalAmount) != 0 } == true

    val canRefund: Boolean
        get() = !isSaving && original.status == OperationStatus.Posted && original.type == OperationType.Expense
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
    data class TypeSelected(val type: OperationType?) : OperationHistoryEvent
    data object TotalsRetry : OperationHistoryEvent
    data object PeriodFilterOpened : OperationHistoryEvent
    data object PeriodFilterClosed : OperationHistoryEvent
    data object PeriodFilterReset : OperationHistoryEvent
    data class PeriodSelected(val dateFrom: LocalDate, val dateTo: LocalDate) : OperationHistoryEvent
    data object Refresh : OperationHistoryEvent
    data object LoadNextPage : OperationHistoryEvent
    data object Retry : OperationHistoryEvent
    data class EditRequested(val purchaseId: String) : OperationHistoryEvent
    data object EditorClosed : OperationHistoryEvent
    data class AmountChanged(val amount: String) : OperationHistoryEvent
    data object SaveRequested : OperationHistoryEvent
    data object RefundRequested : OperationHistoryEvent
    data object RefundDismissed : OperationHistoryEvent
    data object RefundConfirmed : OperationHistoryEvent
}

private val DECIMAL_AMOUNT = Regex("[0-9]+([.,][0-9]{1,2})?")

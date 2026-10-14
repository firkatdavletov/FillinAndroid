package com.firkat.fillin.feature.purchasehistory.ui

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import java.math.BigDecimal

data class PurchaseHistoryUiState(
    val purchases: List<Purchase> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: PurchaseHistoryError? = null,
    val editor: PurchaseEditorUiState? = null,
)

data class PurchaseEditorUiState(
    val original: Purchase,
    val amount: String = original.originalAmount.toPlainString(),
    val isSaving: Boolean = false,
    val error: PurchaseHistoryError? = null,
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
            original.status == PurchaseStatus.Posted &&
            parsedAmount?.let { it.compareTo(original.originalAmount) != 0 } == true

    val canRefund: Boolean
        get() = !isSaving && original.status == PurchaseStatus.Posted
}

enum class PurchaseHistoryError {
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

sealed interface PurchaseHistoryEvent {
    data object Refresh : PurchaseHistoryEvent
    data object LoadNextPage : PurchaseHistoryEvent
    data object Retry : PurchaseHistoryEvent
    data class EditRequested(val purchaseId: String) : PurchaseHistoryEvent
    data object EditorClosed : PurchaseHistoryEvent
    data class AmountChanged(val amount: String) : PurchaseHistoryEvent
    data object SaveRequested : PurchaseHistoryEvent
    data object RefundRequested : PurchaseHistoryEvent
    data object RefundDismissed : PurchaseHistoryEvent
    data object RefundConfirmed : PurchaseHistoryEvent
}

private val DECIMAL_AMOUNT = Regex("[0-9]+([.,][0-9]{1,2})?")

package com.firkat.fillin.feature.purchasehistory.ui

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase

data class PurchaseHistoryUiState(
    val purchases: List<Purchase> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: PurchaseHistoryError? = null,
)

enum class PurchaseHistoryError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    Network,
    InvalidResponse,
    Unknown,
}

sealed interface PurchaseHistoryEvent {
    data object LoadNextPage : PurchaseHistoryEvent
    data object Retry : PurchaseHistoryEvent
}

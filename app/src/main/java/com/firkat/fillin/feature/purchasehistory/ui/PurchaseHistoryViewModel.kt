package com.firkat.fillin.feature.purchasehistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.usecase.GetPurchaseHistoryPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PurchaseHistoryViewModel(
    private val getPurchaseHistoryPage: GetPurchaseHistoryPage,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PurchaseHistoryUiState())
    val uiState: StateFlow<PurchaseHistoryUiState> = _uiState.asStateFlow()

    private var nextCursor: Long? = null

    init {
        loadNextPage()
    }

    fun onEvent(event: PurchaseHistoryEvent) {
        when (event) {
            PurchaseHistoryEvent.LoadNextPage,
            PurchaseHistoryEvent.Retry,
            -> loadNextPage()
        }
    }

    private fun loadNextPage() {
        val currentState = _uiState.value
        if (
            currentState.isInitialLoading ||
            currentState.isLoadingNextPage ||
            !currentState.canLoadMore
        ) {
            return
        }

        val isInitialLoad = currentState.purchases.isEmpty()
        _uiState.update {
            it.copy(
                isInitialLoading = isInitialLoad,
                isLoadingNextPage = !isInitialLoad,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val requestedCursor = nextCursor
                val page = getPurchaseHistoryPage(cursor = requestedCursor)
                nextCursor = page.nextCursor
                _uiState.update { state ->
                    state.copy(
                        purchases = (state.purchases + page.items).distinctBy { it.id },
                        isInitialLoading = false,
                        isLoadingNextPage = false,
                        canLoadMore = page.nextCursor != null &&
                            page.nextCursor != requestedCursor,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PurchaseHistoryException) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isLoadingNextPage = false,
                        error = exception.toUiError(),
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isLoadingNextPage = false,
                        error = PurchaseHistoryError.Unknown,
                    )
                }
            }
        }
    }
}

private fun PurchaseHistoryException.toUiError(): PurchaseHistoryError = when (this) {
    PurchaseHistoryException.MissingApiToken -> PurchaseHistoryError.MissingApiToken
    PurchaseHistoryException.Unauthorized -> PurchaseHistoryError.Unauthorized
    PurchaseHistoryException.Forbidden -> PurchaseHistoryError.Forbidden
    PurchaseHistoryException.Network -> PurchaseHistoryError.Network
    PurchaseHistoryException.InvalidResponse -> PurchaseHistoryError.InvalidResponse
    PurchaseHistoryException.Unknown -> PurchaseHistoryError.Unknown
}

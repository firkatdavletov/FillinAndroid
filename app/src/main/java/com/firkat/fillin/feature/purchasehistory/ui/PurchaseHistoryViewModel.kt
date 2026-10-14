package com.firkat.fillin.feature.purchasehistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.usecase.GetPurchaseHistoryPage
import com.firkat.fillin.feature.purchasehistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.purchasehistory.domain.usecase.UpdatePurchaseAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PurchaseHistoryViewModel(
    private val getPurchaseHistoryPage: GetPurchaseHistoryPage,
    private val updatePurchaseAmount: UpdatePurchaseAmount,
    private val refundPurchase: RefundPurchase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PurchaseHistoryUiState())
    val uiState: StateFlow<PurchaseHistoryUiState> = _uiState.asStateFlow()

    private var nextCursor: Long? = null
    private var loadJob: Job? = null
    private var failedLoadWasRefresh = false

    init {
        loadNextPage()
    }

    fun onEvent(event: PurchaseHistoryEvent) {
        when (event) {
            PurchaseHistoryEvent.LoadNextPage -> loadNextPage()
            PurchaseHistoryEvent.Refresh -> loadNextPage(isRefreshing = true)
            PurchaseHistoryEvent.Retry -> loadNextPage(isRefreshing = failedLoadWasRefresh)
            is PurchaseHistoryEvent.EditRequested -> openEditor(event.purchaseId)
            PurchaseHistoryEvent.EditorClosed -> closeEditor()
            is PurchaseHistoryEvent.AmountChanged -> updateEditor {
                copy(amount = event.amount, error = null)
            }
            PurchaseHistoryEvent.SaveRequested -> save()
            PurchaseHistoryEvent.RefundRequested -> updateEditor {
                if (canRefund) copy(isRefundConfirmationVisible = true) else this
            }
            PurchaseHistoryEvent.RefundDismissed -> updateEditor {
                copy(isRefundConfirmationVisible = false)
            }
            PurchaseHistoryEvent.RefundConfirmed -> refund()
        }
    }

    private fun openEditor(purchaseId: String) {
        if (_uiState.value.isRefreshing) return
        val purchase = _uiState.value.purchases.firstOrNull { it.id == purchaseId } ?: return
        if (purchase.status != PurchaseStatus.Posted) {
            return
        }
        _uiState.update { it.copy(editor = PurchaseEditorUiState(original = purchase)) }
    }

    private fun closeEditor() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return
        val shouldReload = editor.error == PurchaseHistoryError.Conflict ||
            editor.error == PurchaseHistoryError.NotFound
        _uiState.update { it.copy(editor = null) }
        if (shouldReload) reload()
    }

    private fun save() {
        val editor = _uiState.value.editor?.takeIf(PurchaseEditorUiState::canSave) ?: return
        val amount = editor.parsedAmount ?: return
        updateEditor { copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val updated = updatePurchaseAmount(editor.original, amount)
                _uiState.update { state ->
                    state.copy(
                        purchases = state.purchases.map { purchase ->
                            if (purchase.id == updated.id) updated else purchase
                        },
                        editor = null,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PurchaseHistoryException) {
                showEditorError(exception.toUiError())
            } catch (exception: Exception) {
                showEditorError(PurchaseHistoryError.Unknown)
            }
        }
    }

    private fun refund() {
        val editor = _uiState.value.editor ?: return
        if (!editor.canRefund || !editor.isRefundConfirmationVisible) return
        updateEditor {
            copy(
                isSaving = true,
                error = null,
                isRefundConfirmationVisible = false,
            )
        }

        viewModelScope.launch {
            try {
                refundPurchase(editor.original)
                reload()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PurchaseHistoryException) {
                showEditorError(exception.toUiError())
            } catch (exception: Exception) {
                showEditorError(PurchaseHistoryError.Unknown)
            }
        }
    }

    private fun showEditorError(error: PurchaseHistoryError) {
        updateEditor {
            copy(
                isSaving = false,
                error = error,
                isRefundConfirmationVisible = false,
            )
        }
    }

    private fun updateEditor(transform: PurchaseEditorUiState.() -> PurchaseEditorUiState) {
        _uiState.update { state -> state.copy(editor = state.editor?.transform()) }
    }

    private fun reload() {
        loadJob?.cancel()
        failedLoadWasRefresh = false
        nextCursor = null
        _uiState.update {
            it.copy(
                purchases = emptyList(),
                isInitialLoading = false,
                isLoadingNextPage = false,
                isRefreshing = false,
                canLoadMore = true,
                error = null,
                editor = null,
            )
        }
        loadNextPage()
    }

    private fun loadNextPage(isRefreshing: Boolean = false) {
        val currentState = _uiState.value
        if (
            currentState.isInitialLoading ||
            currentState.isRefreshing ||
            (isRefreshing && currentState.editor != null) ||
            (!isRefreshing && (currentState.isLoadingNextPage || !currentState.canLoadMore))
        ) {
            return
        }

        // Refresh supersedes pagination, keeping the current data and cursor until it succeeds.
        if (isRefreshing) loadJob?.cancel()
        val requestedCursor = if (isRefreshing) null else nextCursor
        val isInitialLoad = !isRefreshing && currentState.purchases.isEmpty()
        _uiState.update {
            it.copy(
                isInitialLoading = isInitialLoad,
                isLoadingNextPage = !isRefreshing && !isInitialLoad,
                isRefreshing = isRefreshing,
                error = null,
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val page = getPurchaseHistoryPage(cursor = requestedCursor)
                // A cancelled page must never append stale data after a refresh or reload.
                coroutineContext.ensureActive()
                nextCursor = page.nextCursor
                failedLoadWasRefresh = false
                _uiState.update { state ->
                    state.copy(
                        purchases = (if (isRefreshing) page.items else state.purchases + page.items)
                            .distinctBy { it.id },
                        isInitialLoading = false,
                        isLoadingNextPage = false,
                        isRefreshing = false,
                        canLoadMore = page.nextCursor != null &&
                            page.nextCursor != requestedCursor,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PurchaseHistoryException) {
                coroutineContext.ensureActive()
                failedLoadWasRefresh = isRefreshing
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isLoadingNextPage = false,
                        isRefreshing = false,
                        error = exception.toUiError(),
                    )
                }
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                failedLoadWasRefresh = isRefreshing
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isLoadingNextPage = false,
                        isRefreshing = false,
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
    PurchaseHistoryException.InvalidRequest -> PurchaseHistoryError.InvalidRequest
    PurchaseHistoryException.NotFound -> PurchaseHistoryError.NotFound
    PurchaseHistoryException.Conflict -> PurchaseHistoryError.Conflict
    PurchaseHistoryException.Network -> PurchaseHistoryError.Network
    PurchaseHistoryException.InvalidResponse -> PurchaseHistoryError.InvalidResponse
    PurchaseHistoryException.Unknown -> PurchaseHistoryError.Unknown
}

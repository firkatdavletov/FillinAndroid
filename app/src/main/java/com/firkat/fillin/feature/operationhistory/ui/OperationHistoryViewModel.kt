package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.usecase.GetOperationHistoryPage
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OperationHistoryViewModel(
    private val getOperationHistoryPage: GetOperationHistoryPage,
    private val updatePurchaseAmount: UpdatePurchaseAmount,
    private val refundPurchase: RefundPurchase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        OperationHistoryUiState(filters = OperationHistoryFilter.currentMonth(LocalDate.now(clock))),
    )
    val uiState: StateFlow<OperationHistoryUiState> = _uiState.asStateFlow()

    private var nextCursor: Long? = null
    private var loadJob: Job? = null
    private var failedLoadWasRefresh = false

    init {
        loadNextPage()
    }

    fun onEvent(event: OperationHistoryEvent) {
        when (event) {
            is OperationHistoryEvent.TypeSelected -> selectType(event.type)
            OperationHistoryEvent.PeriodFilterOpened -> _uiState.update { it.copy(isPeriodFilterVisible = true) }
            OperationHistoryEvent.PeriodFilterClosed -> _uiState.update { it.copy(isPeriodFilterVisible = false) }
            OperationHistoryEvent.PeriodFilterReset -> {
                val period = OperationHistoryFilter.currentMonth(LocalDate.now(clock))
                selectPeriod(period.dateFrom, period.dateTo)
            }
            is OperationHistoryEvent.PeriodSelected -> selectPeriod(event.dateFrom, event.dateTo)
            OperationHistoryEvent.LoadNextPage -> loadNextPage()
            OperationHistoryEvent.Refresh -> loadNextPage(isRefreshing = true)
            OperationHistoryEvent.Retry -> loadNextPage(isRefreshing = failedLoadWasRefresh)
            is OperationHistoryEvent.EditRequested -> openEditor(event.purchaseId)
            OperationHistoryEvent.EditorClosed -> closeEditor()
            is OperationHistoryEvent.AmountChanged -> updateEditor {
                copy(amount = event.amount, error = null)
            }
            OperationHistoryEvent.SaveRequested -> save()
            OperationHistoryEvent.RefundRequested -> updateEditor {
                if (canRefund) copy(isRefundConfirmationVisible = true) else this
            }
            OperationHistoryEvent.RefundDismissed -> updateEditor {
                copy(isRefundConfirmationVisible = false)
            }
            OperationHistoryEvent.RefundConfirmed -> refund()
        }
    }

    private fun openEditor(purchaseId: String) {
        if (_uiState.value.isRefreshing) return
        val purchase = _uiState.value.operations.firstOrNull { it.id == purchaseId } ?: return
        if (purchase.status != OperationStatus.Posted || purchase.type != OperationType.Expense) {
            return
        }
        _uiState.update { it.copy(editor = PurchaseEditorUiState(original = purchase)) }
    }

    private fun selectType(type: OperationType?) {
        changeFilters(_uiState.value.filters.copy(type = type))
    }

    private fun selectPeriod(dateFrom: LocalDate, dateTo: LocalDate) {
        if (dateFrom.isAfter(dateTo) || _uiState.value.editor != null) return
        val defaultPeriod = OperationHistoryFilter.currentMonth(LocalDate.now(clock))
        _uiState.update {
            it.copy(
                isPeriodFilterVisible = false,
                isCustomPeriod = dateFrom != defaultPeriod.dateFrom || dateTo != defaultPeriod.dateTo,
            )
        }
        changeFilters(_uiState.value.filters.copy(dateFrom = dateFrom, dateTo = dateTo))
    }

    private fun changeFilters(filters: OperationHistoryFilter) {
        if (_uiState.value.editor != null || _uiState.value.filters == filters) return
        val previous = _uiState.value.filters
        val periodChanged = previous.dateFrom != filters.dateFrom || previous.dateTo != filters.dateTo
        _uiState.update { it.copy(filters = filters) }
        reload(clearTotals = periodChanged)
    }

    private fun closeEditor() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return
        val shouldReload = editor.error == OperationHistoryError.Conflict ||
            editor.error == OperationHistoryError.NotFound
        _uiState.update { it.copy(editor = null) }
        if (shouldReload) reload()
    }

    private fun save() {
        val editor = _uiState.value.editor?.takeIf(PurchaseEditorUiState::canSave) ?: return
        val amount = editor.parsedAmount ?: return
        updateEditor { copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                updatePurchaseAmount(editor.original, amount)
                reload()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: OperationHistoryException) {
                showEditorError(exception.toUiError())
            } catch (exception: Exception) {
                showEditorError(OperationHistoryError.Unknown)
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
            } catch (exception: OperationHistoryException) {
                showEditorError(exception.toUiError())
            } catch (exception: Exception) {
                showEditorError(OperationHistoryError.Unknown)
            }
        }
    }

    private fun showEditorError(error: OperationHistoryError) {
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

    private fun reload(clearTotals: Boolean = true) {
        loadJob?.cancel()
        failedLoadWasRefresh = false
        nextCursor = null
        _uiState.update {
            it.copy(
                operations = emptyList(),
                periodTotals = it.periodTotals.takeUnless { clearTotals },
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
        val requestedFilters = currentState.filters
        val isInitialLoad = !isRefreshing && currentState.operations.isEmpty()
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
                val page = getOperationHistoryPage(cursor = requestedCursor, filter = requestedFilters)
                // A cancelled page must never append stale data after a refresh or reload.
                coroutineContext.ensureActive()
                nextCursor = page.nextCursor
                failedLoadWasRefresh = false
                _uiState.update { state ->
                    state.copy(
                        operations = (if (isRefreshing) page.items else state.operations + page.items)
                            .distinctBy { it.id },
                        periodTotals = page.totals,
                        isInitialLoading = false,
                        isLoadingNextPage = false,
                        isRefreshing = false,
                        canLoadMore = page.nextCursor != null &&
                            page.nextCursor != requestedCursor,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: OperationHistoryException) {
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
                        error = OperationHistoryError.Unknown,
                    )
                }
            }
        }
    }
}

private fun OperationHistoryException.toUiError(): OperationHistoryError = when (this) {
    OperationHistoryException.MissingApiToken -> OperationHistoryError.MissingApiToken
    OperationHistoryException.Unauthorized -> OperationHistoryError.Unauthorized
    OperationHistoryException.Forbidden -> OperationHistoryError.Forbidden
    OperationHistoryException.InvalidRequest -> OperationHistoryError.InvalidRequest
    OperationHistoryException.NotFound -> OperationHistoryError.NotFound
    OperationHistoryException.Conflict -> OperationHistoryError.Conflict
    OperationHistoryException.Network -> OperationHistoryError.Network
    OperationHistoryException.InvalidResponse -> OperationHistoryError.InvalidResponse
    OperationHistoryException.Unknown -> OperationHistoryError.Unknown
}

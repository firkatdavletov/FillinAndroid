package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class OperationHistoryViewModel(
    private val history: OperationHistoryStore,
    private val updatePurchaseAmount: UpdatePurchaseAmount,
    private val refundPurchase: RefundPurchase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : ViewModel() {
    private data class Query(
        val filter: OperationHistoryFilter,
        val limit: Int = 20,
    )

    private val initialFilter = OperationHistoryFilter.currentMonth(LocalDate.now(clock))
    private val query = MutableStateFlow(Query(initialFilter))
    private val _uiState = MutableStateFlow(OperationHistoryUiState(filters = initialFilter, isInitialLoading = true))
    val uiState: StateFlow<OperationHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            history.context.collect { context ->
                _uiState.update {
                    it.copy(
                        offline = context,
                        isRefreshing = context.isSyncing,
                        editor = it.editor?.copy(isOnline = context.isOnline),
                    )
                }
            }
        }
        viewModelScope.launch {
            query.collectLatest { requested ->
                try {
                    history.observeHistory(requested.filter, requested.limit).collect { page ->
                        _uiState.update {
                            it.copy(
                                operations = page.items,
                                periodTotals = page.totals.takeIf { page.isComplete },
                                canLoadMore = page.nextCursor != null,
                                isInitialLoading = false,
                                isLoadingNextPage = false,
                                error = null,
                            )
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isLoadingNextPage = false,
                            error = OperationHistoryError.Unknown,
                        )
                    }
                }
            }
        }
        history.requestSync()
    }

    fun onEvent(event: OperationHistoryEvent) {
        when (event) {
            OperationHistoryEvent.CreateRequested -> {
                openCreator()
            }

            is OperationHistoryEvent.CorrectRejectedRequested -> {
                openCreator(event.id)
            }

            OperationHistoryEvent.CreateClosed -> {
                if (_uiState.value.createEditor?.isSaving != true) {
                    _uiState.update { it.copy(createEditor = null) }
                }
            }

            is OperationHistoryEvent.CreateTypeChanged -> {
                updateCreator { copy(type = event.type, categoryId = null, error = null) }
            }

            is OperationHistoryEvent.CreateAmountChanged -> {
                updateCreator { copy(amount = event.value, error = null) }
            }

            is OperationHistoryEvent.CreateCategoryChanged -> {
                updateCreator { copy(categoryId = event.id, error = null) }
            }

            is OperationHistoryEvent.CreateDateChanged -> {
                updateCreator { copy(occurredOn = event.date, error = null) }
            }

            is OperationHistoryEvent.CreateDescriptionChanged -> {
                updateCreator { copy(description = event.value, error = null) }
            }

            OperationHistoryEvent.CreateSaved -> {
                create()
            }

            is OperationHistoryEvent.TypeSelected -> {
                selectFilter(_uiState.value.filters.copy(type = event.type))
            }

            OperationHistoryEvent.PeriodFilterOpened -> {
                _uiState.update { it.copy(isPeriodFilterVisible = true) }
            }

            OperationHistoryEvent.PeriodFilterClosed -> {
                _uiState.update { it.copy(isPeriodFilterVisible = false) }
            }

            OperationHistoryEvent.PeriodFilterReset -> {
                val period = OperationHistoryFilter.currentMonth(LocalDate.now(clock))
                selectPeriod(period.dateFrom, period.dateTo)
            }

            is OperationHistoryEvent.PeriodSelected -> {
                selectPeriod(event.dateFrom, event.dateTo)
            }

            OperationHistoryEvent.LoadNextPage -> {
                if (_uiState.value.canLoadMore && !_uiState.value.isLoadingNextPage) {
                    _uiState.update { it.copy(isLoadingNextPage = true) }
                    query.update { it.copy(limit = it.limit + 20) }
                }
            }

            OperationHistoryEvent.Refresh, OperationHistoryEvent.Retry -> {
                history.requestSync()
            }

            is OperationHistoryEvent.EditRequested -> {
                openEditor(event.purchaseId)
            }

            OperationHistoryEvent.EditorClosed -> {
                if (_uiState.value.editor?.isSaving != true) _uiState.update { it.copy(editor = null) }
            }

            is OperationHistoryEvent.AmountChanged -> {
                updateEditor { copy(amount = event.amount, error = null) }
            }

            OperationHistoryEvent.SaveRequested -> {
                save()
            }

            OperationHistoryEvent.RefundRequested -> {
                updateEditor { if (canRefund) copy(isRefundConfirmationVisible = true) else this }
            }

            OperationHistoryEvent.RefundDismissed -> {
                updateEditor { copy(isRefundConfirmationVisible = false) }
            }

            OperationHistoryEvent.RefundConfirmed -> {
                refund()
            }
        }
    }

    private fun openCreator(rejectedId: String? = null) {
        val current = _uiState.value
        if (!current.offline.initialSyncComplete || current.offline.syncError == "scope_changed") return
        val rejected =
            rejectedId?.let { id ->
                current.operations.firstOrNull { it.id == id && it.syncStatus == OperationSyncStatus.Rejected }
                    ?: return
            }
        _uiState.update {
            it.copy(
                createEditor =
                    OperationCreateEditorUiState(
                        type = rejected?.type ?: OperationType.Expense,
                        amount = rejected?.originalAmount?.toPlainString().orEmpty(),
                        categoryId = rejected?.categoryId?.takeIf { id -> current.offline.categories.any { it.id == id } },
                        occurredOn = rejected?.occurredOn ?: LocalDate.now(clock),
                        description = rejected?.description.orEmpty(),
                        replacesRejectedId = rejectedId,
                    ),
            )
        }
    }

    private fun create() {
        val editor = _uiState.value.createEditor?.takeIf { it.canSave } ?: return
        val amount = editor.parsedAmount ?: return
        val category =
            _uiState.value.offline.categories
                .firstOrNull { it.id == editor.categoryId }
        val expectedKind = if (editor.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income
        if (category?.kind != expectedKind || editor.occurredOn > LocalDate.now(clock) ||
            _uiState.value.offline.accountingStartedOn
                ?.let { editor.occurredOn < it } == true
        ) {
            updateCreator { copy(error = OperationHistoryError.InvalidRequest) }
            return
        }
        updateCreator { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                history.create(
                    NewOperation(
                        editor.type,
                        requireNotNull(editor.categoryId),
                        amount,
                        editor.occurredOn,
                        editor.description,
                    ),
                    editor.replacesRejectedId,
                )
                _uiState.update { it.copy(createEditor = null) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                updateCreator {
                    copy(
                        isSaving = false,
                        error = (exception as? OperationHistoryException)?.toUiError() ?: OperationHistoryError.Unknown,
                    )
                }
            }
        }
    }

    private fun openEditor(id: String) {
        val current = _uiState.value
        val operation = current.operations.firstOrNull { it.id == id } ?: return
        if (operation.type != OperationType.Expense || operation.status != OperationStatus.Posted ||
            operation.syncStatus != OperationSyncStatus.Synced || !current.offline.isOnline
        ) {
            return
        }
        _uiState.update { it.copy(editor = PurchaseEditorUiState(operation, isOnline = current.offline.isOnline)) }
    }

    private fun selectPeriod(
        from: LocalDate,
        to: LocalDate,
    ) {
        if (from > to || _uiState.value.editor != null || _uiState.value.createEditor != null) return
        val period = OperationHistoryFilter.currentMonth(LocalDate.now(clock))
        _uiState.update { it.copy(isPeriodFilterVisible = false, isCustomPeriod = from != period.dateFrom || to != period.dateTo) }
        selectFilter(_uiState.value.filters.copy(dateFrom = from, dateTo = to))
    }

    private fun selectFilter(filter: OperationHistoryFilter) {
        val state = _uiState.value
        if (state.editor != null || state.createEditor != null || state.filters == filter) return
        val changedPeriod = state.filters.dateFrom != filter.dateFrom || state.filters.dateTo != filter.dateTo
        _uiState.update {
            it.copy(
                filters = filter,
                operations = emptyList(),
                isInitialLoading = true,
                periodTotals = if (changedPeriod) null else it.periodTotals,
                error = null,
            )
        }
        query.value = Query(filter)
    }

    private fun save() {
        val editor = _uiState.value.editor?.takeIf { it.canSave } ?: return
        updateEditor { copy(isSaving = true, error = null) }
        viewModelScope.launch { mutate { updatePurchaseAmount(editor.original, requireNotNull(editor.parsedAmount)) } }
    }

    private fun refund() {
        val editor = _uiState.value.editor?.takeIf { it.canRefund && it.isRefundConfirmationVisible } ?: return
        updateEditor { copy(isSaving = true, error = null, isRefundConfirmationVisible = false) }
        viewModelScope.launch { mutate { refundPurchase(editor.original) } }
    }

    private suspend fun mutate(action: suspend () -> Unit) {
        try {
            action()
            _uiState.update { it.copy(editor = null) }
        } catch (
            exception: CancellationException,
        ) {
            throw exception
        } catch (exception: Exception) {
            updateEditor {
                copy(
                    isSaving = false,
                    error = (exception as? OperationHistoryException)?.toUiError() ?: OperationHistoryError.Unknown,
                )
            }
        }
    }

    private fun updateEditor(update: PurchaseEditorUiState.() -> PurchaseEditorUiState) {
        _uiState.update { it.copy(editor = it.editor?.update()) }
    }

    private fun updateCreator(update: OperationCreateEditorUiState.() -> OperationCreateEditorUiState) {
        _uiState.update { it.copy(createEditor = it.createEditor?.update()) }
    }
}

private fun OperationHistoryException.toUiError(): OperationHistoryError =
    when (this) {
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

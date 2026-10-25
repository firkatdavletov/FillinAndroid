package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
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
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : ViewModel() {
    private data class Query(
        val filter: OperationHistoryFilter,
        val limit: Int = 20,
    )

    private val initialFilter = OperationHistoryFilter.currentMonth(LocalDate.now(clock))
    private val query = MutableStateFlow(Query(initialFilter))
    private val _uiState = MutableStateFlow(
        OperationHistoryUiState(
            filters = initialFilter,
            isInitialLoading = true,
        ),
    )
    val uiState: StateFlow<OperationHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            history.context.collect { context ->
                _uiState.update {
                    it.copy(
                        offline = context,
                        isRefreshing = context.isSyncing,
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
    }

    fun onEvent(event: OperationHistoryEvent) {
        when (event) {
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

        }
    }

    private fun selectPeriod(
        from: LocalDate,
        to: LocalDate,
    ) {
        if (from > to) return
        val period = OperationHistoryFilter.currentMonth(LocalDate.now(clock))
        _uiState.update { it.copy(isPeriodFilterVisible = false, isCustomPeriod = from != period.dateFrom || to != period.dateTo) }
        selectFilter(_uiState.value.filters.copy(dateFrom = from, dateTo = to))
    }

    private fun selectFilter(filter: OperationHistoryFilter) {
        val state = _uiState.value
        if (state.filters == filter) return
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

}

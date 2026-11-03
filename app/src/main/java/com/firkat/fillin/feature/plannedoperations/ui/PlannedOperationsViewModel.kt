package com.firkat.fillin.feature.plannedoperations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationPeriod
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationTotals
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperations
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsException
import com.firkat.fillin.feature.plannedoperations.domain.usecase.GetPlannedOperations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class PlannedOperationsUiState(
    val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Almaty")),
    val filters: PlannedOperationsFilter = PlannedOperationsFilter.currentMonth(today),
    val operations: PlannedOperations? = null,
    val periodTotals: PlannedOperationTotals? = operations?.totals,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: PlannedOperationsException? = null,
    val isPeriodPickerVisible: Boolean = false,
)

sealed interface PlannedOperationsEvent {
    data class FilterApplied(val filter: PlannedOperationsFilter) : PlannedOperationsEvent
    data class TypeSelected(
        val type: PlannedOperationType?,
    ) : PlannedOperationsEvent

    data class PeriodSelected(
        val period: PlannedOperationPeriod,
    ) : PlannedOperationsEvent

    data object CustomPeriodOpened : PlannedOperationsEvent

    data object CustomPeriodClosed : PlannedOperationsEvent

    data class CustomPeriodApplied(
        val from: LocalDate,
        val to: LocalDate,
    ) : PlannedOperationsEvent

    data object Refresh : PlannedOperationsEvent

    data object Retry : PlannedOperationsEvent
}

class PlannedOperationsViewModel(
    private val getPlannedOperations: GetPlannedOperations,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlannedOperationsUiState(today = today()))
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: PlannedOperationsEvent) {
        val filters = _uiState.value.filters
        when (event) {
            is PlannedOperationsEvent.FilterApplied -> {
                _uiState.update { it.copy(isPeriodPickerVisible = false) }
                changeFilters(event.filter)
            }
            is PlannedOperationsEvent.TypeSelected -> {
                changeFilters(filters.copy(type = event.type))
            }

            is PlannedOperationsEvent.PeriodSelected -> {
                _uiState.update { it.copy(isPeriodPickerVisible = false) }
                when (event.period) {
                    PlannedOperationPeriod.Custom -> {
                        onEvent(PlannedOperationsEvent.CustomPeriodOpened)
                    }

                    PlannedOperationPeriod.CurrentMonth -> {
                        changeFilters(
                            PlannedOperationsFilter.currentMonth(today()).copy(type = filters.type),
                        )
                    }

                    else -> {
                        changeFilters(filters.copy(period = event.period, dateFrom = null, dateTo = null))
                    }
                }
            }

            PlannedOperationsEvent.CustomPeriodOpened -> {
                _uiState.update { it.copy(isPeriodPickerVisible = true) }
            }

            PlannedOperationsEvent.CustomPeriodClosed -> {
                _uiState.update { it.copy(isPeriodPickerVisible = false) }
            }

            is PlannedOperationsEvent.CustomPeriodApplied -> {
                if (event.from <= event.to && event.from.year >= 1 && event.to <= today().plusYears(5)) {
                    _uiState.update { it.copy(isPeriodPickerVisible = false) }
                    changeFilters(
                        filters.copy(period = PlannedOperationPeriod.Custom, dateFrom = event.from, dateTo = event.to),
                    )
                }
            }

            PlannedOperationsEvent.Refresh -> {
                if (!_uiState.value.isLoading && !_uiState.value.isRefreshing) load(refresh = true)
            }

            PlannedOperationsEvent.Retry -> {
                load(refresh = _uiState.value.operations != null)
            }
        }
    }

    fun reloadAfterMutation() {
        _uiState.update { it.copy(operations = null, periodTotals = null) }
        load()
    }

    private fun changeFilters(filters: PlannedOperationsFilter) {
        if (_uiState.value.filters == filters) return
        val periodChanged = _uiState.value.filters.copy(type = null) != filters.copy(type = null)
        _uiState.update {
            it.copy(filters = filters, operations = null, periodTotals = it.periodTotals.takeUnless { periodChanged })
        }
        load()
    }

    private fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        val filters = _uiState.value.filters
        _uiState.update {
            it.copy(isLoading = !refresh, isRefreshing = refresh, error = null, today = today())
        }
        if (refresh) getPlannedOperations.requestSync()
        loadJob =
            viewModelScope.launch {
                try {
                    getPlannedOperations.observe(filters).collect { operations ->
                        coroutineContext.ensureActive()
                        _uiState.update {
                            it.copy(
                                operations = operations,
                                periodTotals = operations.totals,
                                isLoading = false,
                                isRefreshing = false,
                            )
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: PlannedOperationsException) {
                    coroutineContext.ensureActive()
                    _uiState.update {
                        it.copy(
                            operations =
                                it.operations.takeUnless {
                                    exception == PlannedOperationsException.Unauthorized ||
                                        exception == PlannedOperationsException.Forbidden
                                },
                            periodTotals =
                                it.periodTotals.takeUnless {
                                    exception == PlannedOperationsException.Unauthorized ||
                                        exception == PlannedOperationsException.Forbidden
                                },
                            isLoading = false,
                            isRefreshing = false,
                            error = exception,
                        )
                    }
                } catch (exception: Exception) {
                    coroutineContext.ensureActive()
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = PlannedOperationsException.Unknown)
                    }
                }
            }
    }

    private fun today() = LocalDate.now(clock.withZone(ZoneId.of("Asia/Almaty")))
}

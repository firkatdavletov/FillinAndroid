package com.firkat.fillin.feature.plannedexpenses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlannedExpensesViewModel(
    private val getPlannedExpenses: GetPlannedExpenses,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlannedExpensesUiState())
    val uiState: StateFlow<PlannedExpensesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: PlannedExpensesEvent) {
        when (event) {
            PlannedExpensesEvent.Retry -> load()
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val plannedExpenses = getPlannedExpenses()
                _uiState.update {
                    it.copy(
                        asOf = plannedExpenses.asOf,
                        throughInclusive = plannedExpenses.throughInclusive,
                        items = plannedExpenses.items,
                        isLoading = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedExpensesException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.toUiError(),
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = PlannedExpensesError.Unknown,
                    )
                }
            }
        }
    }
}

private fun PlannedExpensesException.toUiError(): PlannedExpensesError = when (this) {
    PlannedExpensesException.MissingApiToken -> PlannedExpensesError.MissingApiToken
    PlannedExpensesException.Unauthorized -> PlannedExpensesError.Unauthorized
    PlannedExpensesException.Forbidden -> PlannedExpensesError.Forbidden
    PlannedExpensesException.Network -> PlannedExpensesError.Network
    PlannedExpensesException.InvalidResponse -> PlannedExpensesError.InvalidResponse
    PlannedExpensesException.Unknown -> PlannedExpensesError.Unknown
}

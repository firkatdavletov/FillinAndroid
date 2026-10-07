package com.firkat.fillin.feature.plannedincomes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlannedIncomesViewModel(
    private val getPlannedIncomes: GetPlannedIncomes,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlannedIncomesUiState())
    val uiState: StateFlow<PlannedIncomesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: PlannedIncomesEvent) {
        when (event) {
            PlannedIncomesEvent.Retry -> load()
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val plannedIncomes = getPlannedIncomes()
                _uiState.update {
                    it.copy(
                        asOf = plannedIncomes.asOf,
                        items = plannedIncomes.items,
                        isLoading = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedIncomesException) {
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
                        error = PlannedIncomesError.Unknown,
                    )
                }
            }
        }
    }
}

private fun PlannedIncomesException.toUiError(): PlannedIncomesError = when (this) {
    PlannedIncomesException.MissingApiToken -> PlannedIncomesError.MissingApiToken
    PlannedIncomesException.Unauthorized -> PlannedIncomesError.Unauthorized
    PlannedIncomesException.Forbidden -> PlannedIncomesError.Forbidden
    PlannedIncomesException.Network -> PlannedIncomesError.Network
    PlannedIncomesException.InvalidResponse -> PlannedIncomesError.InvalidResponse
    PlannedIncomesException.Unknown -> PlannedIncomesError.Unknown
}

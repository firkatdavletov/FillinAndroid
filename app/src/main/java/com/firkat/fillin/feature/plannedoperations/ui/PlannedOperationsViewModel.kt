package com.firkat.fillin.feature.plannedoperations.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsException
import com.firkat.fillin.feature.plannedoperations.domain.usecase.ObservePlanningRules
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlanningRuleGroup(val frequency: PlanningFrequency, val rules: List<PlanningRule>)

data class PlannedOperationsUiState(
    val rules: List<PlanningRule> = emptyList(),
    val selectedType: PlannedOperationType? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: PlannedOperationsException? = null,
    val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Almaty"))
) {
    val groups: List<PlanningRuleGroup> get() = PlanningFrequency.entries.mapNotNull { frequency ->
        rules.filter { it.frequency == frequency && (selectedType == null || it.type == selectedType) }
            .sortedWith(compareBy<PlanningRule> { it.nextDate(today) }.thenBy { it.name.lowercase() }.thenBy { it.key })
            .takeIf { it.isNotEmpty() }?.let { PlanningRuleGroup(frequency, it) }
    }
}

sealed interface PlannedOperationsEvent {
    data class TypeSelected(val type: PlannedOperationType?) : PlannedOperationsEvent
    data object Refresh : PlannedOperationsEvent
    data object Retry : PlannedOperationsEvent
}

class PlannedOperationsViewModel(
    private val observeRules: ObservePlanningRules,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        PlannedOperationsUiState(
            selectedType = savedStateHandle.get<String>("planning-type")?.let(PlannedOperationType::valueOf)
        )
    )
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    init {
        load()
    }

    fun onEvent(
        event: PlannedOperationsEvent
    ) {
        when (event) {
            is PlannedOperationsEvent.TypeSelected -> {
                savedStateHandle["planning-type"] = event.type?.name
                _uiState.update { it.copy(selectedType = event.type) }
            }
            PlannedOperationsEvent.Refresh -> if (!_uiState.value.isRefreshing) {
                observeRules.requestSync()
                load(refresh = true)
            }
            PlannedOperationsEvent.Retry -> load()
        }
    }

    private fun load(
        refresh: Boolean = false
    ) {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = it.rules.isEmpty() && !refresh, isRefreshing = refresh, error = null) }
        loadJob = viewModelScope.launch {
            try {
                observeRules().collect { rules ->
                    _uiState.update {
                        it.copy(
                            rules = rules,
                            isLoading = false,
                            isRefreshing = false,
                            today = LocalDate.now(ZoneId.of("Asia/Almaty"))
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e as? PlannedOperationsException ?: PlannedOperationsException.Unknown
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error,
                        rules = if (error == PlannedOperationsException.Forbidden ||
                            error == PlannedOperationsException.Unauthorized
                        ) {
                            emptyList()
                        } else {
                            it.rules
                        }
                    )
                }
            }
        }
    }
}

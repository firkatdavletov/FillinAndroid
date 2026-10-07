package com.firkat.fillin.feature.plannedincomes.ui

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import java.time.LocalDate

data class PlannedIncomesUiState(
    val asOf: LocalDate? = null,
    val items: List<PlannedIncome> = emptyList(),
    val isLoading: Boolean = false,
    val error: PlannedIncomesError? = null,
)

enum class PlannedIncomesError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    Network,
    InvalidResponse,
    Unknown,
}

sealed interface PlannedIncomesEvent {
    data object Retry : PlannedIncomesEvent
}

package com.firkat.fillin.feature.plannedexpenses.ui

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import java.time.LocalDate

data class PlannedExpensesUiState(
    val asOf: LocalDate? = null,
    val throughInclusive: LocalDate? = null,
    val items: List<PlannedExpense> = emptyList(),
    val isLoading: Boolean = false,
    val error: PlannedExpensesError? = null,
)

enum class PlannedExpensesError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    Network,
    InvalidResponse,
    Unknown,
}

sealed interface PlannedExpensesEvent {
    data object Retry : PlannedExpensesEvent
}

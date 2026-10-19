package com.firkat.fillin.feature.dashboard.ui

import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import java.time.LocalDate

data class DashboardUiState(
    val date: LocalDate? = null,
    val status: DashboardStatus = DashboardStatus.WaitingForBudget,
    val isRefreshing: Boolean = false,
)

sealed interface DashboardEvent {
    data object Resume : DashboardEvent
    data object Retry : DashboardEvent
    data object Refresh : DashboardEvent
}

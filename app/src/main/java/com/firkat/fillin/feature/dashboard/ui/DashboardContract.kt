package com.firkat.fillin.feature.dashboard.ui

import com.firkat.fillin.feature.dashboard.domain.model.Dashboard

data class DashboardUiState(
    val dashboard: Dashboard? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: DashboardError? = null,
    val awaitingInitialSync: Boolean = true,
)

enum class DashboardError {
    Forbidden,
    LocalData,
    Unknown,
}

sealed interface DashboardEvent {
    data object Resume : DashboardEvent
    data object Retry : DashboardEvent
    data object Refresh : DashboardEvent
}

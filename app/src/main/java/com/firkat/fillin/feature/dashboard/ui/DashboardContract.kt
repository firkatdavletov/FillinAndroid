package com.firkat.fillin.feature.dashboard.ui

import com.firkat.fillin.feature.dashboard.domain.model.Dashboard

data class DashboardUiState(
    val dashboard: Dashboard? = null,
    val isLoading: Boolean = false,
    val error: DashboardError? = null,
)

enum class DashboardError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    Network,
    InvalidResponse,
    Unknown,
}

sealed interface DashboardEvent {
    data object Retry : DashboardEvent
    data object Refresh : DashboardEvent
}

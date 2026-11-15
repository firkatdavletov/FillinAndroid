package com.firkat.fillin.feature.dashboard.domain.model

enum class DashboardStatus {
    WaitingForBudget,
    Loading,
    Ready,
    LocalError,
    Forbidden;

    val isLoading: Boolean get() = this == WaitingForBudget || this == Loading
}

internal data class DashboardWidgetData<T>(val value: T?, val status: DashboardStatus)

data class DashboardDataState(
    val dashboard: Dashboard? = null,
    val status: DashboardStatus = DashboardStatus.WaitingForBudget,
    val isRefreshing: Boolean = false
)

package com.firkat.fillin.feature.dashboard.domain.model

enum class DashboardStatus { WaitingForBudget, Loading, Ready, LocalError, Forbidden }

data class DashboardDataState(
    val dashboard: Dashboard? = null,
    val status: DashboardStatus = DashboardStatus.WaitingForBudget,
    val isRefreshing: Boolean = false
)

package com.firkat.fillin.feature.dashboard.domain.usecase

import com.firkat.fillin.feature.dashboard.domain.model.Dashboard
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository

class GetDashboard(
    private val repository: DashboardRepository,
) {
    suspend operator fun invoke(): Dashboard = repository.getDashboard()
}

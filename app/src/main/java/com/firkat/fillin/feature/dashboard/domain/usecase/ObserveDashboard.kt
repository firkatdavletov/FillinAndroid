package com.firkat.fillin.feature.dashboard.domain.usecase

import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository

class ObserveDashboard(private val repository: DashboardRepository) {
    operator fun invoke() = repository.observeDashboard()
    fun requestSync() = repository.requestSync()
}

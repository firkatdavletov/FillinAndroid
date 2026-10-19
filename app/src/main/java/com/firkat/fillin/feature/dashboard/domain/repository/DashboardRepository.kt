package com.firkat.fillin.feature.dashboard.domain.repository

import com.firkat.fillin.feature.dashboard.domain.model.Dashboard
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun observeDashboard(): Flow<Dashboard>
    fun requestSync()
}

package com.firkat.fillin.feature.dashboard.domain.repository

import com.firkat.fillin.feature.dashboard.domain.model.Dashboard

interface DashboardRepository {
    suspend fun getDashboard(): Dashboard
}

sealed class DashboardException : Exception() {
    data object MissingApiToken : DashboardException()
    data object Unauthorized : DashboardException()
    data object Forbidden : DashboardException()
    data object Network : DashboardException()
    data object InvalidResponse : DashboardException()
    data object Unknown : DashboardException()
}

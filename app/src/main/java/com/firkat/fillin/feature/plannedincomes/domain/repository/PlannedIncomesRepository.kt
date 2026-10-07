package com.firkat.fillin.feature.plannedincomes.domain.repository

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes

interface PlannedIncomesRepository {
    suspend fun getPlannedIncomes(): PlannedIncomes
}

sealed class PlannedIncomesException : Exception() {
    data object MissingApiToken : PlannedIncomesException()
    data object Unauthorized : PlannedIncomesException()
    data object Forbidden : PlannedIncomesException()
    data object Network : PlannedIncomesException()
    data object InvalidResponse : PlannedIncomesException()
    data object Unknown : PlannedIncomesException()
}

package com.firkat.fillin.feature.plannedexpenses.domain.repository

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenses

interface PlannedExpensesRepository {
    suspend fun getPlannedExpenses(): PlannedExpenses
}

sealed class PlannedExpensesException : Exception() {
    data object MissingApiToken : PlannedExpensesException()
    data object Unauthorized : PlannedExpensesException()
    data object Forbidden : PlannedExpensesException()
    data object Network : PlannedExpensesException()
    data object InvalidResponse : PlannedExpensesException()
    data object Unknown : PlannedExpensesException()
}

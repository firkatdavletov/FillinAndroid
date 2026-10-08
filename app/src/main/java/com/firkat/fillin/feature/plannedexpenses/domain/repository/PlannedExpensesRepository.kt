package com.firkat.fillin.feature.plannedexpenses.domain.repository

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseRule
import java.math.BigDecimal
import java.time.LocalDate

interface PlannedExpensesRepository {
    suspend fun getPlannedExpenses(): PlannedExpenses

    suspend fun createRule(
        categoryId: String,
        amount: BigDecimal,
        frequency: PlannedExpenseFrequency,
        scheduleValue: Int,
        startsOn: LocalDate,
    ): PlannedExpenseRule

    suspend fun updateRule(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): PlannedExpenseRule

    suspend fun deleteRule(id: String, version: Long)

    suspend fun closeExpense(id: String, version: Long)
}

sealed class PlannedExpensesException : Exception() {
    data object MissingApiToken : PlannedExpensesException()
    data object Unauthorized : PlannedExpensesException()
    data object Forbidden : PlannedExpensesException()
    data object InvalidRequest : PlannedExpensesException()
    data object NotFound : PlannedExpensesException()
    data object Conflict : PlannedExpensesException()
    data object Network : PlannedExpensesException()
    data object InvalidResponse : PlannedExpensesException()
    data object Unknown : PlannedExpensesException()
}

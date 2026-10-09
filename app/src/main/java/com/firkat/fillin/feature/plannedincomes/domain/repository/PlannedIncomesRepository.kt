package com.firkat.fillin.feature.plannedincomes.domain.repository

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import java.math.BigDecimal
import java.time.LocalDate

interface PlannedIncomesRepository {
    suspend fun getPlannedIncomes(): PlannedIncomes

    suspend fun createPlannedIncome(
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate,
    ): PlannedIncome

    suspend fun updatePlannedIncome(
        id: String,
        version: Long,
        categoryId: String?,
        amount: BigDecimal?,
        dueOn: LocalDate?,
    ): PlannedIncome

    suspend fun deletePlannedIncome(id: String, version: Long)
}

sealed class PlannedIncomesException : Exception() {
    data object MissingApiToken : PlannedIncomesException()
    data object Unauthorized : PlannedIncomesException()
    data object Forbidden : PlannedIncomesException()
    data object InvalidRequest : PlannedIncomesException()
    data object NotFound : PlannedIncomesException()
    data object Conflict : PlannedIncomesException()
    data object Network : PlannedIncomesException()
    data object InvalidResponse : PlannedIncomesException()
    data object Unknown : PlannedIncomesException()
}

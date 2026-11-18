package com.firkat.fillin.feature.plannedincomes.domain.repository

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.flow

interface PlannedIncomesRepository {
    suspend fun saveDetails(
        income: PlannedIncome?,
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate,
        name: String,
        frequency: com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency,
        active: Boolean,
        confirmRemainder: Boolean = false
    ): PlannedIncome = if (income ==
        null
    ) {
        createPlannedIncome(categoryId, amount, dueOn)
    } else {
        updatePlannedIncome(income.id, income.version, categoryId, amount, dueOn)
    }

    suspend fun saveRuleDetails(
        rule: PlanningRule,
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate,
        name: String,
        frequency: com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency,
        active: Boolean
    ): Unit = throw UnsupportedOperationException("Rule editing requires an offline repository")

    fun observePlannedIncomes() = flow { emit(getPlannedIncomes()) }

    suspend fun getPlannedIncomes(): PlannedIncomes

    suspend fun createPlannedIncome(
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate
    ): PlannedIncome

    suspend fun updatePlannedIncome(
        id: String,
        version: Long,
        categoryId: String?,
        amount: BigDecimal?,
        dueOn: LocalDate?
    ): PlannedIncome

    suspend fun deletePlannedIncome(
        id: String,
        version: Long
    )
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

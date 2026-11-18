package com.firkat.fillin.feature.plannedexpenses.domain.usecase

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository

class GetPlannedExpenses(private val repository: PlannedExpensesRepository) {
    fun observe() = repository.observePlannedExpenses()

    suspend operator fun invoke(): PlannedExpenses = repository.getPlannedExpenses()
}

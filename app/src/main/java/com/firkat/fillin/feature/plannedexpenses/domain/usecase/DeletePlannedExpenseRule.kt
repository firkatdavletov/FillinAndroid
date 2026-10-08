package com.firkat.fillin.feature.plannedexpenses.domain.usecase

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository

class DeletePlannedExpenseRule(
    private val repository: PlannedExpensesRepository,
) {
    suspend operator fun invoke(expense: PlannedExpense) {
        repository.deleteRule(
            id = expense.ruleId,
            version = expense.ruleVersion,
        )
    }
}

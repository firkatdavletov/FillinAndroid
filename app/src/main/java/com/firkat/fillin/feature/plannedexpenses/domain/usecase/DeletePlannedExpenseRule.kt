package com.firkat.fillin.feature.plannedexpenses.domain.usecase

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule

class DeletePlannedExpenseRule(private val repository: PlannedExpensesRepository) {
    suspend operator fun invoke(
        rule: PlanningRule
    ) {
        repository.deleteRule(rule.id, rule.version)
    }

    suspend operator fun invoke(
        expense: PlannedExpense
    ) {
        repository.deleteRule(
            id = expense.ruleId,
            version = expense.ruleVersion
        )
    }
}

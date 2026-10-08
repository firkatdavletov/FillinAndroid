package com.firkat.fillin.feature.plannedexpenses.domain.usecase

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import java.math.BigDecimal

class UpdatePlannedExpenseRule(
    private val repository: PlannedExpensesRepository,
) {
    suspend operator fun invoke(
        expense: PlannedExpense,
        amount: BigDecimal,
    ): PlannedExpenseRule {
        if (!amount.isValidKztAmount()) throw PlannedExpensesException.InvalidRequest
        return repository.updateRule(
            id = expense.ruleId,
            version = expense.ruleVersion,
            amount = amount,
        )
    }
}

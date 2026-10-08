package com.firkat.fillin.feature.plannedexpenses.domain.usecase

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository

class ClosePlannedExpense(
    private val repository: PlannedExpensesRepository,
) {
    suspend operator fun invoke(expense: PlannedExpense) {
        if (!expense.canBeClosed) throw PlannedExpensesException.InvalidRequest
        repository.closeExpense(id = expense.id, version = expense.version)
    }
}

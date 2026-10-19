package com.firkat.fillin.feature.budgetsetup.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

interface BudgetSetupRepository {
    val ready: Flow<Boolean>
    val today: LocalDate

    suspend fun createLocal(
        openingBalance: BigDecimal,
        startedOn: LocalDate,
    )

    suspend fun connectExisting()
}

class CreateLocalBudget(
    private val repository: BudgetSetupRepository,
) {
    suspend operator fun invoke(
        balance: BigDecimal,
        date: LocalDate,
    ) {
        require(balance.scale() <= 2 && balance.abs() <= BigDecimal("99999999999999999.99"))
        require(date.year >= 1 && date <= repository.today)
        repository.createLocal(balance, date)
    }
}

class ConnectExistingBudget(
    private val repository: BudgetSetupRepository,
) {
    suspend operator fun invoke() = repository.connectExisting()
}

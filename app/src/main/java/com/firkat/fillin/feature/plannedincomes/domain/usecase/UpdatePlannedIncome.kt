package com.firkat.fillin.feature.plannedincomes.domain.usecase

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import java.math.BigDecimal
import java.time.LocalDate

class UpdatePlannedIncome(
    private val repository: PlannedIncomesRepository,
    private val currentDate: () -> LocalDate = { LocalDate.now(ALMATY_ZONE) },
) {
    suspend operator fun invoke(
        income: PlannedIncome,
        categoryId: String? = null,
        amount: BigDecimal? = null,
        dueOn: LocalDate? = null,
    ): PlannedIncome {
        if (
            listOf(categoryId, amount, dueOn).all { it == null } ||
            categoryId?.isBlank() == true ||
            amount?.isValidKztAmount() == false ||
            dueOn?.isBefore(currentDate()) == true
        ) {
            throw PlannedIncomesException.InvalidRequest
        }

        return repository.updatePlannedIncome(
            id = income.id,
            version = income.version,
            categoryId = categoryId,
            amount = amount,
            dueOn = dueOn,
        )
    }
}

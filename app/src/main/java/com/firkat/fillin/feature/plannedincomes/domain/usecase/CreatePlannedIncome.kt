package com.firkat.fillin.feature.plannedincomes.domain.usecase

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class CreatePlannedIncome(
    private val repository: PlannedIncomesRepository,
    private val currentDate: () -> LocalDate = { LocalDate.now(ALMATY_ZONE) }
) {
    suspend operator fun invoke(
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate
    ): PlannedIncome {
        if (
            categoryId.isBlank() ||
            !amount.isValidKztAmount() ||
            dueOn.isBefore(currentDate())
        ) {
            throw PlannedIncomesException.InvalidRequest
        }

        return repository.createPlannedIncome(
            categoryId = categoryId,
            amount = amount,
            dueOn = dueOn
        )
    }
}

internal fun BigDecimal.isValidKztAmount(): Boolean = this > BigDecimal.ZERO && scale() <= KZT_FRACTION_DIGITS

internal val ALMATY_ZONE: ZoneId = ZoneId.of("Asia/Almaty")

private const val KZT_FRACTION_DIGITS = 2

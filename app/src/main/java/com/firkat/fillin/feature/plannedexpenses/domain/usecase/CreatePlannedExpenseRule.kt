package com.firkat.fillin.feature.plannedexpenses.domain.usecase

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import java.math.BigDecimal
import java.time.LocalDate

class CreatePlannedExpenseRule(
    private val repository: PlannedExpensesRepository,
) {
    suspend operator fun invoke(
        categoryId: String,
        amount: BigDecimal,
        frequency: PlannedExpenseFrequency,
        scheduleValue: Int,
        startsOn: LocalDate,
    ): PlannedExpenseRule {
        if (
            categoryId.isBlank() ||
            !amount.isValidKztAmount() ||
            scheduleValue !in frequency.validScheduleValues
        ) {
            throw PlannedExpensesException.InvalidRequest
        }

        return repository.createRule(
            categoryId = categoryId,
            amount = amount,
            frequency = frequency,
            scheduleValue = scheduleValue,
            startsOn = startsOn,
        )
    }
}

internal fun BigDecimal.isValidKztAmount(): Boolean =
    this > BigDecimal.ZERO && scale() <= KZT_FRACTION_DIGITS

internal val PlannedExpenseFrequency.validScheduleValues: IntRange
    get() = when (this) {
        PlannedExpenseFrequency.Monthly -> 1..31
        PlannedExpenseFrequency.Weekly -> 1..7
    }

private const val KZT_FRACTION_DIGITS = 2
